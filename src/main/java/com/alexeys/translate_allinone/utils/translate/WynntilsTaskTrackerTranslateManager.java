package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.llmapi.LLM;
import com.alexeys.translate_allinone.utils.llmapi.LlmPayloadJsonSupport;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.TranslateStringUtils;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public final class WynntilsTaskTrackerTranslateManager {
    private static final WynntilsTaskTrackerTranslateManager INSTANCE = new WynntilsTaskTrackerTranslateManager();
    private static final Gson GSON = LlmPayloadJsonSupport.gson();
    private static final int MAX_BATCH_SIZE = 4;
    private static final int WORKER_COUNT = 1;
    private static final int MAX_IN_FLIGHT_REQUESTS = 1;

    private final WynntilsTaskTrackerTextCache cache = WynntilsTaskTrackerTextCache.getInstance();
    private final AtomicLong sessionEpoch = new AtomicLong(0L);
    private final TranslationRequestLimiter requestLimiter = new TranslationRequestLimiter(MAX_IN_FLIGHT_REQUESTS);

    private ExecutorService workerExecutor;
    private ScheduledExecutorService collectorExecutor;

    private WynntilsTaskTrackerTranslateManager() {
    }

    public static WynntilsTaskTrackerTranslateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        long newSessionEpoch = sessionEpoch.incrementAndGet();
        WynntilsTaskTrackerTranslationSupport.devLog("session_started epoch={}", newSessionEpoch);

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            workerExecutor = Executors.newFixedThreadPool(WORKER_COUNT);
            for (int i = 0; i < WORKER_COUNT; i++) {
                workerExecutor.submit(this::processingLoop);
            }
            WynntilsTaskTrackerTranslationSupport.devLog(
                    "workers_started count={}",
                    WORKER_COUNT);
        }

        if (collectorExecutor == null || collectorExecutor.isShutdown()) {
            collectorExecutor = Executors.newSingleThreadScheduledExecutor();
            collectorExecutor.scheduleAtFixedRate(this::collectAndBatchItems, 0, 1, TimeUnit.SECONDS);
        }

    }

    public synchronized void stop() {
        long invalidatedSessionEpoch = sessionEpoch.incrementAndGet();
        WynntilsTaskTrackerTranslationSupport.devLog(
                "session_invalidated epoch={}",
                invalidatedSessionEpoch);

        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdownNow();
            try {
                if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    WynntilsTaskTrackerTranslationSupport.devLog("workers_termination_timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (collectorExecutor != null && !collectorExecutor.isShutdown()) {
            collectorExecutor.shutdownNow();
        }

    }

    public synchronized void cancelPendingTranslations() {
        stop();
        cache.clearPendingAndInProgress();
    }

    public synchronized void clearTranslationQueue() {
        sessionEpoch.incrementAndGet();
        cache.clearTranslationQueue();
    }

    private void processingLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<String> batch = null;
            try {
                long batchSessionEpoch = sessionEpoch.get();
                if (!WynntilsTaskTrackerTranslationSupport.isTrackerTranslationEnabled()) {
                    TimeUnit.SECONDS.sleep(5);
                    continue;
                }

                batch = cache.takeBatchForTranslation();
                cache.markAsInProgress(batch);

                if (!isSessionActive(batchSessionEpoch)) {
                    cache.releaseInProgress(Set.copyOf(batch));
                    continue;
                }

                translateBatch(batch, getTargetLanguage(), batchSessionEpoch);
            } catch (InterruptedException e) {
                if (batch != null && !batch.isEmpty()) {
                    cache.requeueFailed(Set.copyOf(batch), "Processing thread interrupted");
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (batch != null && !batch.isEmpty()) {
                    cache.requeueFailed(Set.copyOf(batch), "Processing loop failure: " + e.getMessage());
                }
                Translate_AllinOne.LOGGER.error("Unexpected error in Wynntils task tracker processing loop.", e);
            }
        }
    }

    private void collectAndBatchItems() {
        try {
            if (!WynntilsTaskTrackerTranslationSupport.isTrackerTranslationEnabled()) {
                return;
            }

            List<String> items = cache.drainAllPendingItems();
            if (items.isEmpty()) {
                return;
            }

            for (int i = 0; i < items.size(); i += MAX_BATCH_SIZE) {
                int end = Math.min(items.size(), i + MAX_BATCH_SIZE);
                cache.submitBatchForTranslation(items.subList(i, end));
            }
            WynntilsTaskTrackerTranslationSupport.devLog(
                    "collector_submitted items={} batch_size={}",
                    items.size(),
                    MAX_BATCH_SIZE);
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error in Wynntils task tracker collector thread.", e);
        }
    }

    private void translateBatch(
            List<String> originalTexts,
            String targetLanguage,
            long batchSessionEpoch
    ) throws InterruptedException {
        if (originalTexts == null || originalTexts.isEmpty()) {
            return;
        }

        if (!isSessionActive(batchSessionEpoch)) {
            cache.releaseInProgress(Set.copyOf(originalTexts));
            return;
        }

        ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                ProviderRouteResolver.Route.WYNNTILS_TASK_TRACKER);
        if (providerProfile == null) {
            Translate_AllinOne.LOGGER.warn(
                    "No routed provider/model configured for Wynntils task tracker translation; re-queueing {} items.",
                    originalTexts.size()
            );
            cache.requeueFailed(Set.copyOf(originalTexts), "No routed model selected");
            return;
        }
        if (ProviderRouteResolver.hasApiKeyDecryptFailure(Translate_AllinOne.getConfig(), ProviderRouteResolver.Route.WYNNTILS_TASK_TRACKER)) {
            ApiKeyDecryptFailureNotifier.notifyRuntimeIfPresent();
            cache.requeueFailed(Set.copyOf(originalTexts), "API key decryption failed");
            return;
        }

        Map<String, String> batchForAI = new LinkedHashMap<>();
        for (int i = 0; i < originalTexts.size(); i++) {
            batchForAI.put(String.valueOf(i + 1), originalTexts.get(i));
        }

        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
        LLM llm = new LLM(settings);

        String systemPrompt = buildSystemPrompt(targetLanguage, providerProfile.activeSystemPromptSuffix(), providerProfile.system_prompt_overrides);
        String userPrompt = GSON.toJson(batchForAI);
        List<OpenAIRequest.Message> messages = PromptMessageBuilder.buildMessages(
                systemPrompt,
                userPrompt,
                providerProfile.activeSupportsSystemMessage(),
                providerProfile.model_id,
                providerProfile.activeInjectSystemPromptIntoUserMessage());
        String requestContext = buildRequestContext(providerProfile, targetLanguage, originalTexts, messages);
        WynntilsTaskTrackerTranslationSupport.devLog(
                "llm_submit context={} payload={}",
                requestContext,
                userPrompt);

        TranslationRequestLimiter.Permit requestPermit = requestLimiter.acquire();
        if (!isSessionActive(batchSessionEpoch)) {
            requestPermit.close();
            cache.releaseInProgress(Set.copyOf(originalTexts));
            return;
        }

        CompletableFuture<String> completion;
        long watchdogRequestId;
        try {
            completion = llm.getCompletion(messages, requestContext);
            watchdogRequestId = TranslationQueueWatchdog.requestStarted("wynntils_task_tracker", originalTexts);
        } catch (RuntimeException | Error startError) {
            requestPermit.close();
            throw startError;
        }
        completion.whenComplete((response, error) -> {
            requestPermit.close();
            if (!isSessionActive(batchSessionEpoch)) {
                TranslationQueueWatchdog.requestSuperseded(watchdogRequestId);
                cache.releaseInProgress(Set.copyOf(originalTexts));
                return;
            }

            Set<String> failedTaskKeys = ConcurrentHashMap.newKeySet();

            if (error != null) {
                failedTaskKeys.addAll(originalTexts);
                cache.requeueFailed(Set.copyOf(originalTexts), error.getMessage());
                Translate_AllinOne.LOGGER.error(
                        "Failed to translate Wynntils task tracker batch. context={}",
                        requestContext,
                        error);
            } else {
                try {
                    Matcher matcher = TranslateStringUtils.JSON_EXTRACT_PATTERN.matcher(response);
                    if (!matcher.find()) {
                        throw new JsonSyntaxException("No JSON object found in the translation response.");
                    }

                    String jsonResponse = matcher.group();
                    Type type = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> translatedMapFromAI = GSON.fromJson(jsonResponse, type);
                    WynntilsTaskTrackerTranslationSupport.devLog(
                            "llm_response context={} response={}",
                            requestContext,
                            response);
                    if (translatedMapFromAI == null) {
                        throw new JsonSyntaxException("Parsed translation result is null");
                    }

                    if (hasKeyMismatch(translatedMapFromAI, originalTexts.size())) {
                        failedTaskKeys.addAll(originalTexts);
                        Translate_AllinOne.LOGGER.warn(
                                "Wynntils task tracker response keys mismatched. context={}",
                                requestContext
                        );
                        cache.requeueFailed(Set.copyOf(originalTexts), "LLM response key mismatch");
                    } else {
                        Map<String, String> finalTranslatedMap = new ConcurrentHashMap<>();
                        Set<String> itemsToRequeueForEmpty = ConcurrentHashMap.newKeySet();
                        Set<String> itemsToRequeueForColor = ConcurrentHashMap.newKeySet();

                        for (Map.Entry<String, String> entry : translatedMapFromAI.entrySet()) {
                            int index;
                            try {
                                index = Integer.parseInt(entry.getKey()) - 1;
                            } catch (NumberFormatException e) {
                                continue;
                            }

                            if (index < 0 || index >= originalTexts.size()) {
                                continue;
                            }

                            String originalTemplate = originalTexts.get(index);
                            String translatedTemplate = entry.getValue();
                            if (translatedTemplate == null || translatedTemplate.trim().isEmpty()) {
                                itemsToRequeueForEmpty.add(originalTemplate);
                                continue;
                            }

                            if (originalTemplate.contains("§") && !translatedTemplate.contains("§")) {
                                itemsToRequeueForColor.add(originalTemplate);
                                continue;
                            }

                            finalTranslatedMap.put(originalTemplate, translatedTemplate);
                        }

                        if (!itemsToRequeueForColor.isEmpty()) {
                            Translate_AllinOne.LOGGER.warn(
                                    "Re-queueing {} Wynntils task tracker translations that failed color code validation. context={}",
                                    itemsToRequeueForColor.size(),
                                    requestContext
                            );
                        }

                        if (!itemsToRequeueForEmpty.isEmpty()) {
                            Translate_AllinOne.LOGGER.warn(
                                    "Re-queueing {} Wynntils task tracker translations that returned empty values. context={}",
                                    itemsToRequeueForEmpty.size(),
                                    requestContext
                            );
                        }

                        if (!finalTranslatedMap.isEmpty()) {
                            cache.updateTranslations(finalTranslatedMap);
                            WynntilsTaskTrackerTranslationSupport.devLog(
                                    "cache_updated count={} context={}",
                                    finalTranslatedMap.size(),
                                    requestContext);
                        }

                        Set<String> missingTranslations = ConcurrentHashMap.newKeySet();
                        missingTranslations.addAll(originalTexts);
                        missingTranslations.removeAll(finalTranslatedMap.keySet());
                        missingTranslations.addAll(itemsToRequeueForColor);
                        missingTranslations.addAll(itemsToRequeueForEmpty);
                        if (!missingTranslations.isEmpty()) {
                            failedTaskKeys.addAll(missingTranslations);
                            Translate_AllinOne.LOGGER.warn(
                                    "Wynntils task tracker LLM response missing {} keys. context={}",
                                    missingTranslations.size(),
                                    requestContext
                            );
                            cache.requeueFailed(missingTranslations, "LLM response missing keys");
                        }
                    }
                } catch (JsonSyntaxException e) {
                    failedTaskKeys.addAll(originalTexts);
                    cache.requeueFailed(Set.copyOf(originalTexts), "Invalid JSON response");
                    Translate_AllinOne.LOGGER.error(
                            "Failed to parse Wynntils task tracker translation response. context={}",
                            requestContext,
                            e);
                } catch (Throwable t) {
                    failedTaskKeys.addAll(originalTexts);
                    cache.requeueFailed(Set.copyOf(originalTexts), "Translation post-processing failure");
                    Translate_AllinOne.LOGGER.error(
                            "Unexpected Wynntils task tracker post-processing error. context={}",
                            requestContext,
                            t);
                }
            }

            TranslationQueueWatchdog.requestCompleted(
                    watchdogRequestId,
                    failedTaskKeys,
                    false
            );
        });
    }

    private boolean isSessionActive(long expectedEpoch) {
        return expectedEpoch == sessionEpoch.get();
    }

    private boolean hasKeyMismatch(Map<String, String> translatedMapFromAI, int expectedSize) {
        for (int i = 1; i <= expectedSize; i++) {
            if (!translatedMapFromAI.containsKey(String.valueOf(i))) {
                return true;
            }
        }
        return translatedMapFromAI.size() != expectedSize;
    }

    private String buildSystemPrompt(String targetLanguage, String suffix, java.util.Map<String, String> overrides) {
        String basePrompt = PromptMessageBuilder.getDefaultPrompt("wynntils_task_tracker", targetLanguage);
        String resolved = PromptMessageBuilder.applyPromptOverride("wynntils_task_tracker", basePrompt, overrides, targetLanguage);
        return PromptMessageBuilder.appendSystemPromptSuffix(resolved, suffix);
    }

    private String buildRequestContext(
            ApiProviderProfile profile,
            String targetLanguage,
            List<String> originalTexts,
            List<OpenAIRequest.Message> messages
    ) {
        String providerId = profile == null ? "" : profile.id;
        String modelId = profile == null ? "" : profile.model_id;
        int messageCount = messages == null ? 0 : messages.size();
        String sample = originalTexts == null || originalTexts.isEmpty()
                ? ""
                : TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(originalTexts.getFirst()), 160);
        return "route=wynntils_task_tracker"
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + targetLanguage
                + ", batch=" + (originalTexts == null ? 0 : originalTexts.size())
                + ", messages=" + messageCount
                + ", sample=\"" + sample + "\"";
    }

    private String getTargetLanguage() {
        return WynntilsTaskTrackerTranslationSupport.getTargetLanguage();
    }
}
