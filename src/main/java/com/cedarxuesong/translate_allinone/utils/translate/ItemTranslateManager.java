package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemTranslateManager {
    private static final ItemTranslateManager INSTANCE = new ItemTranslateManager();
    private static final Gson GSON = new Gson();
    private static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final int MAX_KEY_MISMATCH_BATCH_RETRIES = 1;

    private record BatchRequestContext(
            long batchId,
            long requestId,
            int attempt,
            String reason,
            Long parentRequestId,
            long collectedAtNanos,
            long enqueuedAtNanos,
            long dequeuedAtNanos
    ) {}

    private record BatchTimingMetrics(
            long endToEndElapsedNanos,
            long collectToEnqueueElapsedNanos,
            long queueWaitElapsedNanos,
            long preflightElapsedNanos,
            long networkElapsedNanos,
            long callbackElapsedNanos,
            long jsonExtractElapsedNanos,
            long jsonParseElapsedNanos,
            long validationElapsedNanos,
            long cacheUpdateElapsedNanos
    ) {}

    private record RetryRequest(
            List<String> originalTexts,
            BatchRequestContext batchContext,
            int keyMismatchRetryCount
    ) {}

    private ExecutorService workerExecutor;
    private ScheduledExecutorService collectorExecutor;
    private ScheduledExecutorService retryExecutor;
    private final ScheduledExecutorService runtimeRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "translate_allinone-item-runtime-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private Semaphore requestPermitLimiter;
    private final ItemTemplateCache cache = ItemTemplateCache.getInstance();
    private int currentConcurrentRequests = -1;
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final AtomicLong batchIdSequence = new AtomicLong(0);
    private final AtomicLong requestIdSequence = new AtomicLong(0);
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private final AtomicBoolean runtimeRefreshPending = new AtomicBoolean(false);
    private final AtomicBoolean runtimeRefreshScheduled = new AtomicBoolean(false);

    private ItemTranslateManager() {}

    public static ItemTranslateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        long newSessionEpoch = sessionEpoch.incrementAndGet();
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        Translate_AllinOne.LOGGER.info("Item translation session started. epoch={}", newSessionEpoch);
        logSessionSnapshot("start", newSessionEpoch, config);

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            currentConcurrentRequests = Math.max(1, config.max_concurrent_requests);
            requestPermitLimiter = new Semaphore(currentConcurrentRequests, true);
            workerExecutor = Executors.newFixedThreadPool(currentConcurrentRequests);
            for (int i = 0; i < currentConcurrentRequests; i++) {
                workerExecutor.submit(this::processingLoop);
            }
            Translate_AllinOne.LOGGER.info("ItemTranslateManager started with {} worker threads.", currentConcurrentRequests);
        }

        if (collectorExecutor == null || collectorExecutor.isShutdown()) {
            collectorExecutor = Executors.newSingleThreadScheduledExecutor();
            collectorExecutor.scheduleAtFixedRate(this::collectAndBatchItems, 0, 1, TimeUnit.SECONDS);
            Translate_AllinOne.LOGGER.info("Item translation collector started.");
        }

        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor();
            retryExecutor.scheduleAtFixedRate(this::requeueErroredItems, 15, 15, TimeUnit.SECONDS);
            Translate_AllinOne.LOGGER.info("Item translation retry scheduler started.");
        }

    }

    public synchronized void stop() {
        long invalidatedSessionEpoch = sessionEpoch.incrementAndGet();
        Translate_AllinOne.LOGGER.info(
                "Item translation session invalidated. epoch={} inFlight={} queue={}",
                invalidatedSessionEpoch,
                inFlightRequests.get(),
                ItemTemplateCache.formatQueueSnapshot(cache.snapshotQueues())
        );

        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdownNow();
            try {
                if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Translate_AllinOne.LOGGER.error("Processing executor did not terminate in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Translate_AllinOne.LOGGER.info("ItemTranslateManager's processing threads stopped.");
        }
        if (collectorExecutor != null && !collectorExecutor.isShutdown()) {
            collectorExecutor.shutdownNow();
            Translate_AllinOne.LOGGER.info("Item translation collector stopped.");
        }
        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdownNow();
            Translate_AllinOne.LOGGER.info("Item translation retry scheduler stopped.");
        }
        requestPermitLimiter = null;
        runtimeRefreshPending.set(false);
    }

    public void requestRuntimeRefresh() {
        runtimeRefreshPending.set(true);
        scheduleRuntimeRefreshCheck(0L);
    }

    private void processingLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            ItemTemplateCache.BatchWorkItem workItem = null;
            List<String> batch = null;
            try {
                long batchSessionEpoch = sessionEpoch.get();
                ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
                if (!config.enabled) {
                    TimeUnit.SECONDS.sleep(5);
                    continue;
                }
                
                workItem = cache.takeBatchForTranslation();
                batch = workItem.items();
                cache.markAsInProgress(batch);
                BatchRequestContext batchContext = createInitialBatchRequestContext(workItem);
                logDispatchEvent(config, "dequeue", batchContext, batch, -1);

                if (!isSessionActive(batchSessionEpoch)) {
                    cache.releaseInProgress(new java.util.HashSet<>(batch));
                    continue;
                }

                translateBatch(batch, config, batchContext, batchSessionEpoch);

            } catch (InterruptedException e) {
                if (batch != null && !batch.isEmpty()) {
                    cache.requeueFailed(new java.util.HashSet<>(batch), "Processing thread interrupted");
                }
                Thread.currentThread().interrupt();
                Translate_AllinOne.LOGGER.info("Processing thread interrupted, shutting down.");
                break;
            } catch (Exception e) {
                if (batch != null && !batch.isEmpty()) {
                    cache.requeueFailed(new java.util.HashSet<>(batch), "Processing loop failure: " + e.getMessage());
                }
                Translate_AllinOne.LOGGER.error("An unexpected error occurred in the processing loop, continuing.", e);
            }
        }
    }

    private void collectAndBatchItems() {
        try {
            ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
            if (!config.enabled) {
                return;
            }
            long collectedAtNanos = System.nanoTime();
            List<String> items = cache.drainAllPendingItems();
            if (items.isEmpty()) {
                return;
            }

            int batchSize = Math.max(1, config.max_batch_size);
            List<Long> batchIds = new ArrayList<>();
            for (int i = 0; i < items.size(); i += batchSize) {
                int end = Math.min(items.size(), i + batchSize);
                List<String> batch = new java.util.ArrayList<>(items.subList(i, end));
                long batchId = batchIdSequence.incrementAndGet();
                batchIds.add(batchId);
                cache.submitBatchForTranslation(batchId, batch, collectedAtNanos);
            }
            int batchCount = (int) Math.ceil((double) items.size() / batchSize);
            Translate_AllinOne.LOGGER.info("Collected and submitted {} batch(es) for translation.", batchCount);
            logCollectorCycle(config, collectedAtNanos, items.size(), batchCount, batchSize, batchIds);
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error in collector thread", e);
        }
    }

    private void requeueErroredItems() {
        try {
            java.util.Set<String> erroredKeys = cache.getErroredKeys();
            if (!erroredKeys.isEmpty()) {
                Translate_AllinOne.LOGGER.info("Re-queueing {} errored items for another attempt.", erroredKeys.size());
                erroredKeys.forEach(cache::requeueFromError);
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error during scheduled re-queue of errored items", e);
        }
    }

    private void translateBatch(
            List<String> originalTexts,
            ItemTranslateConfig config,
            BatchRequestContext batchContext,
            long batchSessionEpoch
    ) throws InterruptedException {
        translateBatch(originalTexts, config, batchContext, 0, batchSessionEpoch);
    }

    private void translateBatch(
            List<String> originalTexts,
            ItemTranslateConfig config,
            BatchRequestContext batchContext,
            int keyMismatchRetryCount,
            long batchSessionEpoch
    ) throws InterruptedException {
        if (originalTexts.isEmpty()) {
            return;
        }

        if (!isSessionActive(batchSessionEpoch)) {
            cache.releaseInProgress(new java.util.HashSet<>(originalTexts));
            return;
        }
        
        Map<String, String> batchForAI = new java.util.LinkedHashMap<>();
        for (int i = 0; i < originalTexts.size(); i++) {
            batchForAI.put(String.valueOf(i + 1), originalTexts.get(i));
        }

        ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                ProviderRouteResolver.Route.ITEM
        );
        if (providerProfile == null) {
            Translate_AllinOne.LOGGER.warn("No routed provider/model configured for item translation; re-queueing {} items.", originalTexts.size());
            cache.requeueFailed(new java.util.HashSet<>(originalTexts), "No routed model selected");
            return;
        }

        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
        LLM llm = new LLM(settings);

        String systemPrompt = buildSystemPrompt(config.target_language, providerProfile.activeSystemPromptSuffix());
        String userPrompt = GSON.toJson(batchForAI);

        List<OpenAIRequest.Message> messages = PromptMessageBuilder.buildMessages(
                systemPrompt,
                userPrompt,
                providerProfile.activeSupportsSystemMessage(),
                providerProfile.model_id,
                providerProfile.activeInjectSystemPromptIntoUserMessage()
        );
        String requestContext = buildRequestContext(batchContext, providerProfile, config.target_language, originalTexts, messages);
        acquireRequestPermit();
        long requestStartedAtNanos = System.nanoTime();
        int inFlightAtSend = inFlightRequests.incrementAndGet();
        logDispatchEvent(config, "send", batchContext, originalTexts, inFlightAtSend);
        CompletableFuture<String> completionFuture;
        try {
            completionFuture = llm.getCompletion(messages);
        } catch (Throwable error) {
            int inFlightAfter = decrementInFlightRequests();
            releaseRequestPermit();
            long callbackStartedAtNanos = System.nanoTime();
            long finishedAtNanos = System.nanoTime();
            cache.requeueFailed(new java.util.HashSet<>(originalTexts), error.getMessage());
            logDevBatchTiming(
                    config,
                    "start-error",
                    batchContext,
                    requestContext,
                    requestStartedAtNanos,
                    callbackStartedAtNanos,
                    finishedAtNanos,
                    0L,
                    0L,
                    0L,
                    0L,
                    originalTexts.size(),
                    0,
                    originalTexts.size(),
                    originalTexts.size(),
                    inFlightAtSend,
                    inFlightAfter,
                    cache.snapshotQueues(),
                    error
            );
            Translate_AllinOne.LOGGER.error("Failed to start item translation request. context={}", requestContext, error);
            return;
        }
        completionFuture.whenComplete((response, error) -> {
            long callbackStartedAtNanos = System.nanoTime();
            long jsonExtractElapsedNanos = 0L;
            long jsonParseElapsedNanos = 0L;
            long validationElapsedNanos = 0L;
            long cacheUpdateElapsedNanos = 0L;
            int translatedCount = 0;
            int requeueCount = 0;
            int missingCount = 0;
            String phase = "callback";
            Throwable phaseError = error;
            List<RetryRequest> deferredRetries = new ArrayList<>();
            try {
                if (!isSessionActive(batchSessionEpoch)) {
                    cache.releaseInProgress(new java.util.HashSet<>(originalTexts));
                    phase = "stale";
                    Translate_AllinOne.LOGGER.debug(
                            "Dropping stale item translation callback. requestEpoch={}, activeEpoch={}, context={}",
                            batchSessionEpoch,
                            sessionEpoch.get(),
                            requestContext
                    );
                } else if (error != null) {
                    if (isInternalPostprocessError(error) && originalTexts.size() > 1) {
                        phase = "retry-single";
                        Translate_AllinOne.LOGGER.warn(
                                "Item batch translation hit internal post-process error, retrying as single-item batches. context={} batchSize={}",
                                requestContext,
                                originalTexts.size()
                        );
                        for (String text : originalTexts) {
                            deferredRetries.add(new RetryRequest(
                                    List.of(text),
                                    createRetryBatchRequestContext(batchContext, "retry-single"),
                                    0
                            ));
                        }
                    } else {
                        phase = "error";
                        requeueCount = originalTexts.size();
                        missingCount = originalTexts.size();
                        Translate_AllinOne.LOGGER.error("Failed to get translation from LLM. context={}", requestContext, error);
                        cache.requeueFailed(new java.util.HashSet<>(originalTexts), error.getMessage());
                    }
                } else {
                    try {
                        long jsonExtractStartedAtNanos = System.nanoTime();
                        Matcher matcher = JSON_EXTRACT_PATTERN.matcher(response);
                        if (!matcher.find()) {
                            throw new JsonSyntaxException("No JSON object found in the response.");
                        }
                        String jsonResponse = matcher.group();
                        jsonExtractElapsedNanos = System.nanoTime() - jsonExtractStartedAtNanos;

                        long jsonParseStartedAtNanos = System.nanoTime();
                        Type type = new TypeToken<Map<String, String>>() {}.getType();
                        Map<String, String> translatedMapFromAI = GSON.fromJson(jsonResponse, type);
                        jsonParseElapsedNanos = System.nanoTime() - jsonParseStartedAtNanos;
                        if (translatedMapFromAI == null) {
                            throw new JsonSyntaxException("Parsed translation result is null");
                        }

                        if (hasKeyMismatch(translatedMapFromAI, originalTexts.size())) {
                            if (keyMismatchRetryCount < MAX_KEY_MISMATCH_BATCH_RETRIES) {
                                int nextAttempt = keyMismatchRetryCount + 1;
                                phase = "retry-batch";
                                missingCount = originalTexts.size();
                                Translate_AllinOne.LOGGER.warn(
                                        "Item translation keys mismatched, retrying full batch. attempt={}/{} context={}",
                                        nextAttempt,
                                        MAX_KEY_MISMATCH_BATCH_RETRIES,
                                        requestContext
                                );
                                deferredRetries.add(new RetryRequest(
                                        new java.util.ArrayList<>(originalTexts),
                                        createRetryBatchRequestContext(batchContext, "retry-batch"),
                                        nextAttempt
                                ));
                            } else {
                                phase = "key-mismatch";
                                requeueCount = originalTexts.size();
                                missingCount = originalTexts.size();
                                Translate_AllinOne.LOGGER.warn(
                                        "Item translation keys mismatched after retries, re-queueing full batch. context={}",
                                        requestContext
                                );
                                cache.requeueFailed(new java.util.HashSet<>(originalTexts), "LLM response key mismatch");
                            }
                        } else {
                            long validationStartedAtNanos = System.nanoTime();
                            Map<String, String> finalTranslatedMap = new java.util.HashMap<>();
                            java.util.Set<String> itemsToRequeueForColor = new java.util.HashSet<>();
                            java.util.Set<String> itemsToRequeueForEmpty = new java.util.HashSet<>();

                            for (Map.Entry<String, String> entry : translatedMapFromAI.entrySet()) {
                                try {
                                    int index = Integer.parseInt(entry.getKey()) - 1;
                                    if (index >= 0 && index < originalTexts.size()) {
                                        String originalTemplate = originalTexts.get(index);
                                        String translatedTemplate = entry.getValue();

                                        if (translatedTemplate == null || translatedTemplate.trim().isEmpty()) {
                                            itemsToRequeueForEmpty.add(originalTemplate);
                                            continue;
                                        }

                                        if (originalTemplate.contains("§") && !translatedTemplate.contains("§")) {
                                            itemsToRequeueForColor.add(originalTemplate);
                                        } else {
                                            finalTranslatedMap.put(originalTemplate, translatedTemplate);
                                        }
                                    } else {
                                        Translate_AllinOne.LOGGER.warn("Received out-of-bounds index {} from LLM, skipping.", entry.getKey());
                                    }
                                } catch (NumberFormatException e) {
                                    Translate_AllinOne.LOGGER.warn("Received non-numeric key '{}' from LLM, skipping.", entry.getKey());
                                }
                            }

                            // Re-queue texts that were not successfully translated
                            java.util.Set<String> allOriginalTexts = new java.util.HashSet<>(originalTexts);
                            allOriginalTexts.removeAll(finalTranslatedMap.keySet());
                            allOriginalTexts.removeAll(itemsToRequeueForColor);
                            allOriginalTexts.removeAll(itemsToRequeueForEmpty);
                            validationElapsedNanos = System.nanoTime() - validationStartedAtNanos;

                            long cacheUpdateStartedAtNanos = System.nanoTime();
                            if (!finalTranslatedMap.isEmpty()) {
                                cache.updateTranslations(finalTranslatedMap);
                            }

                            if (!itemsToRequeueForColor.isEmpty()) {
                                Translate_AllinOne.LOGGER.warn("Re-queueing {} item translations that failed color code validation.", itemsToRequeueForColor.size());
                                cache.requeueFailed(itemsToRequeueForColor, "Missing color codes in translation");
                            }

                            if (!itemsToRequeueForEmpty.isEmpty()) {
                                Translate_AllinOne.LOGGER.warn("Re-queueing {} item translations that returned empty values.", itemsToRequeueForEmpty.size());
                                cache.requeueFailed(itemsToRequeueForEmpty, "Empty translation response");
                            }

                            if (!allOriginalTexts.isEmpty()) {
                                Translate_AllinOne.LOGGER.warn("LLM response did not contain all original keys. Re-queueing {} missing translations.", allOriginalTexts.size());
                                cache.requeueFailed(allOriginalTexts, "LLM response missing keys");
                            }
                            cacheUpdateElapsedNanos = System.nanoTime() - cacheUpdateStartedAtNanos;

                            phase = "success";
                            translatedCount = finalTranslatedMap.size();
                            requeueCount = itemsToRequeueForColor.size() + itemsToRequeueForEmpty.size();
                            missingCount = allOriginalTexts.size();
                        }
                    } catch (JsonSyntaxException e) {
                        phase = "json-error";
                        phaseError = e;
                        requeueCount = originalTexts.size();
                        missingCount = originalTexts.size();
                        Translate_AllinOne.LOGGER.error("Failed to parse JSON response from LLM. Response: {}", response, e);
                        cache.requeueFailed(new java.util.HashSet<>(originalTexts), "Invalid JSON response");
                    } catch (Throwable t) {
                        phase = "postprocess-error";
                        phaseError = t;
                        requeueCount = originalTexts.size();
                        missingCount = originalTexts.size();
                        Translate_AllinOne.LOGGER.error("Unexpected item translation post-processing error. context={}", requestContext, t);
                        cache.requeueFailed(new java.util.HashSet<>(originalTexts), "Translation post-processing failure");
                    }
                }
            } catch (Throwable t) {
                phase = "callback-error";
                phaseError = t;
                requeueCount = originalTexts.size();
                missingCount = originalTexts.size();
                cache.requeueFailed(new java.util.HashSet<>(originalTexts), "Translation callback failure");
                Translate_AllinOne.LOGGER.error("Unexpected item translation callback error. context={}", requestContext, t);
            }
            int inFlightAfter = decrementInFlightRequests();
            releaseRequestPermit();
            long finishedAtNanos = System.nanoTime();
            logDevBatchTiming(
                    config,
                    phase,
                    batchContext,
                    requestContext,
                    requestStartedAtNanos,
                    callbackStartedAtNanos,
                    finishedAtNanos,
                    jsonExtractElapsedNanos,
                    jsonParseElapsedNanos,
                    validationElapsedNanos,
                    cacheUpdateElapsedNanos,
                    originalTexts.size(),
                    translatedCount,
                    requeueCount,
                    missingCount,
                    inFlightAtSend,
                    inFlightAfter,
                    cache.snapshotQueues(),
                    phaseError
            );
            if (!deferredRetries.isEmpty()) {
                for (RetryRequest retryRequest : deferredRetries) {
                    dispatchRetryRequest(retryRequest, config, batchSessionEpoch, requestContext);
                }
            }
        });
    }

    private boolean isSessionActive(long expectedEpoch) {
        return expectedEpoch == sessionEpoch.get();
    }

    private boolean hasKeyMismatch(Map<String, String> translatedMapFromAI, int expectedSize) {
        java.util.Set<String> expectedKeys = new java.util.LinkedHashSet<>();
        for (int i = 1; i <= expectedSize; i++) {
            expectedKeys.add(String.valueOf(i));
        }

        java.util.Set<String> actualKeys = new java.util.LinkedHashSet<>(translatedMapFromAI.keySet());
        java.util.Set<String> missingKeys = new java.util.LinkedHashSet<>(expectedKeys);
        missingKeys.removeAll(actualKeys);

        java.util.Set<String> extraKeys = new java.util.LinkedHashSet<>(actualKeys);
        extraKeys.removeAll(expectedKeys);

        if (missingKeys.isEmpty() && extraKeys.isEmpty()) {
            return false;
        }

        Translate_AllinOne.LOGGER.warn(
                "Item translation key mismatch. expectedCount={}, actualCount={}, missing={}, extra={}",
                expectedKeys.size(),
                actualKeys.size(),
                summarizeKeys(missingKeys),
                summarizeKeys(extraKeys)
        );
        return true;
    }

    private String summarizeKeys(java.util.Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "[]";
        }
        final int limit = 8;
        java.util.List<String> sample = new java.util.ArrayList<>(limit);
        int count = 0;
        for (String key : keys) {
            if (count++ >= limit) {
                break;
            }
            sample.add(key);
        }
        if (keys.size() <= limit) {
            return sample.toString();
        }
        return sample + "...(+" + (keys.size() - limit) + ")";
    }

    private void logDevBatchTiming(
            ItemTranslateConfig config,
            String phase,
            BatchRequestContext batchContext,
            String requestContext,
            long requestStartedAtNanos,
            long callbackStartedAtNanos,
            long finishedAtNanos,
            long jsonExtractElapsedNanos,
            long jsonParseElapsedNanos,
            long validationElapsedNanos,
            long cacheUpdateElapsedNanos,
            int batchSize,
            int translatedCount,
            int requeueCount,
            int missingCount,
            int inFlightAtSend,
            int inFlightAfterCompletion,
            ItemTemplateCache.QueueSnapshot queueSnapshot,
            Throwable error
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemBatchTiming(config)) {
            return;
        }

        BatchTimingMetrics metrics = buildTimingMetrics(
                batchContext,
                requestStartedAtNanos,
                callbackStartedAtNanos,
                finishedAtNanos,
                jsonExtractElapsedNanos,
                jsonParseElapsedNanos,
                validationElapsedNanos,
                cacheUpdateElapsedNanos
        );
        String errorSummary = error == null ? "" : truncate(normalizeWhitespace(String.valueOf(unwrapThrowable(error))), 220);
        Translate_AllinOne.LOGGER.info(
                "[ItemDev] phase={} elapsedMs={} endToEndMs={} collectToEnqueueMs={} queueWaitMs={} preflightMs={} networkMs={} callbackMs={} jsonExtractMs={} jsonParseMs={} validateMs={} cacheUpdateMs={} batchSize={} translated={} requeue={} missing={} inFlight={}=>{} queue={} context={} error=\"{}\"",
                phase,
                formatDurationMillis(safeElapsedNanos(requestStartedAtNanos, finishedAtNanos)),
                formatDurationMillis(metrics.endToEndElapsedNanos()),
                formatDurationMillis(metrics.collectToEnqueueElapsedNanos()),
                formatDurationMillis(metrics.queueWaitElapsedNanos()),
                formatDurationMillis(metrics.preflightElapsedNanos()),
                formatDurationMillis(metrics.networkElapsedNanos()),
                formatDurationMillis(metrics.callbackElapsedNanos()),
                formatDurationMillis(metrics.jsonExtractElapsedNanos()),
                formatDurationMillis(metrics.jsonParseElapsedNanos()),
                formatDurationMillis(metrics.validationElapsedNanos()),
                formatDurationMillis(metrics.cacheUpdateElapsedNanos()),
                batchSize,
                translatedCount,
                requeueCount,
                missingCount,
                inFlightAtSend,
                inFlightAfterCompletion,
                ItemTemplateCache.formatQueueSnapshot(queueSnapshot),
                requestContext,
                errorSummary
        );
    }

    private String buildSystemPrompt(String targetLanguage, String suffix) {
        String basePrompt = "You are a deterministic JSON value translator.\n"
                + "Target language: " + targetLanguage + ".\n"
                + "\n"
                + "Input is a JSON object with string keys and string values.\n"
                + "Output must be one valid JSON object only.\n"
                + "\n"
                + "Rules:\n"
                + "1) Keep all keys unchanged.\n"
                + "2) Keep key count unchanged.\n"
                + "3) Translate values only.\n"
                + "4) Preserve style tags exactly: <s0>...</s0>, <s1>...</s1>, ... Keep the same tag ids, counts, and order.\n"
                + "5) Preserve tokens exactly: §a §l §r %s %d %f {d1} URLs numbers <...> {...} \\n \\t.\n"
                + "6) If a value contains line breaks, preserve the full meaning and paragraph order. Keep intentional structural breaks when they matter, but soft-wrapped tooltip text may be reflowed naturally. Do not omit words or leave connector fragments like 'by' or 'to' untranslated inside an otherwise translated sentence.\n"
                + "7) If unsure for a value, keep that value unchanged.\n"
                + "8) No extra text outside JSON.";
        return PromptMessageBuilder.appendSystemPromptSuffix(basePrompt, suffix);
    }

    private boolean isInternalPostprocessError(Throwable throwable) {
        Throwable root = unwrapThrowable(throwable);
        if (root == null || root.getMessage() == null) {
            return false;
        }
        String message = root.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("internalpostprocesserror")
                || message.contains("internal error during model post-process")
                || message.contains("translation failed due to internal error");
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String buildRequestContext(
            BatchRequestContext batchContext,
            ApiProviderProfile profile,
            String targetLanguage,
            List<String> originalTexts,
            List<OpenAIRequest.Message> messages
    ) {
        String providerId = profile == null ? "" : profile.id;
        String modelId = profile == null ? "" : profile.model_id;
        int messageCount = messages == null ? 0 : messages.size();
        String roles = messages == null
                ? "[]"
                : messages.stream().map(message -> message == null ? "null" : String.valueOf(message.role)).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        int customParamCount = profile == null ? 0 : profile.activeCustomParameters().size();
        String sample = originalTexts == null || originalTexts.isEmpty() ? "" : truncate(normalizeWhitespace(originalTexts.get(0)), 160);
        return "route=item"
                + ", batch_id=" + (batchContext == null ? 0 : batchContext.batchId())
                + ", request_id=" + (batchContext == null ? 0 : batchContext.requestId())
                + ", attempt=" + (batchContext == null ? 0 : batchContext.attempt())
                + ", reason=" + (batchContext == null ? "" : batchContext.reason())
                + ", parent_request_id=" + (batchContext == null || batchContext.parentRequestId() == null ? "" : batchContext.parentRequestId())
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + (targetLanguage == null ? "" : targetLanguage)
                + ", batch=" + (originalTexts == null ? 0 : originalTexts.size())
                + ", messages=" + messageCount
                + ", roles=" + roles
                + ", custom_params=" + customParamCount
                + ", sample=\"" + sample + "\"";
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void logSessionSnapshot(String phase, long epoch, ItemTranslateConfig config) {
        if (config == null) {
            return;
        }
        Translate_AllinOne.LOGGER.info(
                "[ItemDev:session] phase={} epoch={} enabled={} customName={} lore={} wynnCompat={} target={} configuredConcurrent={} activeWorkers={} maxBatchSize={} requestsPerMinute={} debugEnabled={} logBatchTiming={} queue={}",
                phase,
                epoch,
                config.enabled,
                config.enabled_translate_item_custom_name,
                config.enabled_translate_item_lore,
                config.wynn_item_compatibility,
                config.target_language,
                config.max_concurrent_requests,
                currentConcurrentRequests > 0 ? currentConcurrentRequests : Math.max(1, config.max_concurrent_requests),
                config.max_batch_size,
                config.requests_per_minute,
                config.debug != null && config.debug.enabled,
                TooltipTextMatcherSupport.shouldLogItemBatchTiming(config),
                ItemTemplateCache.formatQueueSnapshot(cache.snapshotQueues())
        );
    }

    private void logCollectorCycle(
            ItemTranslateConfig config,
            long collectedAtNanos,
            int drainedItemCount,
            int batchCount,
            int batchSize,
            List<Long> batchIds
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemBatchTiming(config)) {
            return;
        }

        Translate_AllinOne.LOGGER.info(
                "[ItemDev:collector] drainedItems={} batchCount={} configuredBatchSize={} batchIds={} elapsedMs={} queue={}",
                drainedItemCount,
                batchCount,
                batchSize,
                summarizeBatchIds(batchIds),
                formatDurationMillis(System.nanoTime() - collectedAtNanos),
                ItemTemplateCache.formatQueueSnapshot(cache.snapshotQueues())
        );
    }

    private void logDispatchEvent(
            ItemTranslateConfig config,
            String phase,
            BatchRequestContext batchContext,
            List<String> batch,
            int inFlightCount
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemBatchTiming(config) || batchContext == null) {
            return;
        }

        Translate_AllinOne.LOGGER.info(
                "[ItemDev:dispatch] phase={} batchId={} requestId={} attempt={} reason={} parentRequestId={} batchSize={} collectToEnqueueMs={} queueWaitMs={} inFlight={} queue={} sample=\"{}\"",
                phase,
                batchContext.batchId(),
                batchContext.requestId(),
                batchContext.attempt(),
                batchContext.reason(),
                batchContext.parentRequestId() == null ? "" : batchContext.parentRequestId(),
                batch == null ? 0 : batch.size(),
                formatDurationMillis(safeElapsedNanos(batchContext.collectedAtNanos(), batchContext.enqueuedAtNanos())),
                formatDurationMillis(safeElapsedNanos(batchContext.enqueuedAtNanos(), batchContext.dequeuedAtNanos())),
                inFlightCount < 0 ? "" : Integer.toString(inFlightCount),
                ItemTemplateCache.formatQueueSnapshot(cache.snapshotQueues()),
                batch == null || batch.isEmpty() ? "" : truncate(normalizeWhitespace(batch.get(0)), 160)
        );
    }

    private BatchRequestContext createInitialBatchRequestContext(ItemTemplateCache.BatchWorkItem workItem) {
        long dequeuedAtNanos = System.nanoTime();
        return new BatchRequestContext(
                workItem.batchId(),
                requestIdSequence.incrementAndGet(),
                1,
                "initial",
                null,
                workItem.collectedAtNanos(),
                workItem.enqueuedAtNanos(),
                dequeuedAtNanos
        );
    }

    private BatchRequestContext createRetryBatchRequestContext(BatchRequestContext parentContext, String reason) {
        long now = System.nanoTime();
        return new BatchRequestContext(
                parentContext == null ? 0L : parentContext.batchId(),
                requestIdSequence.incrementAndGet(),
                parentContext == null ? 1 : parentContext.attempt() + 1,
                reason == null ? "" : reason,
                parentContext == null ? null : parentContext.requestId(),
                now,
                now,
                now
        );
    }

    private void dispatchRetryRequest(
            RetryRequest retryRequest,
            ItemTranslateConfig config,
            long batchSessionEpoch,
            String parentRequestContext
    ) {
        if (retryRequest == null || retryRequest.originalTexts() == null || retryRequest.originalTexts().isEmpty()) {
            return;
        }
        try {
            translateBatch(
                    retryRequest.originalTexts(),
                    config,
                    retryRequest.batchContext(),
                    retryRequest.keyMismatchRetryCount(),
                    batchSessionEpoch
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cache.requeueFailed(new java.util.HashSet<>(retryRequest.originalTexts()), "Retry dispatch interrupted");
            Translate_AllinOne.LOGGER.warn(
                    "Interrupted while dispatching item translation retry. parentContext={} retryRequestId={} retryReason={}",
                    parentRequestContext,
                    retryRequest.batchContext() == null ? 0L : retryRequest.batchContext().requestId(),
                    retryRequest.batchContext() == null ? "" : retryRequest.batchContext().reason(),
                    e
            );
        }
    }

    private void scheduleRuntimeRefreshCheck(long delayMillis) {
        if (!runtimeRefreshPending.get()) {
            return;
        }
        if (!runtimeRefreshScheduled.compareAndSet(false, true)) {
            return;
        }
        runtimeRefreshExecutor.schedule(() -> {
            try {
                applyRuntimeRefreshIfNeeded();
            } finally {
                runtimeRefreshScheduled.set(false);
                if (runtimeRefreshPending.get()) {
                    scheduleRuntimeRefreshCheck(250L);
                }
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    private synchronized void applyRuntimeRefreshIfNeeded() {
        if (!runtimeRefreshPending.get()) {
            return;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        int desiredConcurrentRequests = config == null ? 1 : Math.max(1, config.max_concurrent_requests);
        if (desiredConcurrentRequests == currentConcurrentRequests) {
            runtimeRefreshPending.set(false);
            return;
        }

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            runtimeRefreshPending.set(false);
            return;
        }

        ItemTemplateCache.QueueSnapshot queueSnapshot = cache.snapshotQueues();
        if (inFlightRequests.get() > 0 || queueSnapshot.inProgress() > 0) {
            Translate_AllinOne.LOGGER.info(
                    "[ItemDev:session] phase=runtime-refresh-deferred desiredConcurrent={} activeConcurrent={} inFlight={} queue={}",
                    desiredConcurrentRequests,
                    currentConcurrentRequests,
                    inFlightRequests.get(),
                    ItemTemplateCache.formatQueueSnapshot(queueSnapshot)
            );
            return;
        }

        ExecutorService previousExecutor = workerExecutor;
        if (previousExecutor != null && !previousExecutor.isShutdown()) {
            previousExecutor.shutdownNow();
            try {
                if (!previousExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    Translate_AllinOne.LOGGER.warn("Old item translation worker executor did not terminate cleanly during runtime refresh.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Translate_AllinOne.LOGGER.warn("Interrupted while waiting for item translation executor runtime refresh.", e);
                return;
            }
        }

        currentConcurrentRequests = desiredConcurrentRequests;
        requestPermitLimiter = new Semaphore(currentConcurrentRequests, true);
        workerExecutor = Executors.newFixedThreadPool(currentConcurrentRequests);
        for (int i = 0; i < currentConcurrentRequests; i++) {
            workerExecutor.submit(this::processingLoop);
        }
        runtimeRefreshPending.set(false);
        Translate_AllinOne.LOGGER.info(
                "[ItemDev:session] phase=runtime-refresh-applied activeConcurrent={} queue={}",
                currentConcurrentRequests,
                ItemTemplateCache.formatQueueSnapshot(cache.snapshotQueues())
        );
    }

    private BatchTimingMetrics buildTimingMetrics(
            BatchRequestContext batchContext,
            long requestStartedAtNanos,
            long callbackStartedAtNanos,
            long finishedAtNanos,
            long jsonExtractElapsedNanos,
            long jsonParseElapsedNanos,
            long validationElapsedNanos,
            long cacheUpdateElapsedNanos
    ) {
        if (batchContext == null) {
            return new BatchTimingMetrics(
                    0L,
                    0L,
                    0L,
                    0L,
                    safeElapsedNanos(requestStartedAtNanos, callbackStartedAtNanos),
                    safeElapsedNanos(callbackStartedAtNanos, finishedAtNanos),
                    jsonExtractElapsedNanos,
                    jsonParseElapsedNanos,
                    validationElapsedNanos,
                    cacheUpdateElapsedNanos
            );
        }
        return new BatchTimingMetrics(
                safeElapsedNanos(batchContext.collectedAtNanos(), finishedAtNanos),
                safeElapsedNanos(batchContext.collectedAtNanos(), batchContext.enqueuedAtNanos()),
                safeElapsedNanos(batchContext.enqueuedAtNanos(), batchContext.dequeuedAtNanos()),
                safeElapsedNanos(batchContext.dequeuedAtNanos(), requestStartedAtNanos),
                safeElapsedNanos(requestStartedAtNanos, callbackStartedAtNanos),
                safeElapsedNanos(callbackStartedAtNanos, finishedAtNanos),
                jsonExtractElapsedNanos,
                jsonParseElapsedNanos,
                validationElapsedNanos,
                cacheUpdateElapsedNanos
        );
    }

    private int decrementInFlightRequests() {
        return inFlightRequests.updateAndGet(value -> Math.max(0, value - 1));
    }

    private void acquireRequestPermit() throws InterruptedException {
        Semaphore limiter = requestPermitLimiter;
        if (limiter == null) {
            return;
        }
        limiter.acquire();
    }

    private void releaseRequestPermit() {
        Semaphore limiter = requestPermitLimiter;
        if (limiter == null) {
            return;
        }
        limiter.release();
    }

    private long safeElapsedNanos(long startedAtNanos, long finishedAtNanos) {
        if (startedAtNanos <= 0L || finishedAtNanos <= 0L) {
            return 0L;
        }
        return Math.max(0L, finishedAtNanos - startedAtNanos);
    }

    private String formatDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
    }

    private String summarizeBatchIds(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return "[]";
        }
        if (batchIds.size() <= 8) {
            return batchIds.toString();
        }
        List<Long> sample = new ArrayList<>(batchIds.subList(0, 8));
        return sample + "...(+" + (batchIds.size() - 8) + ")";
    }
}
