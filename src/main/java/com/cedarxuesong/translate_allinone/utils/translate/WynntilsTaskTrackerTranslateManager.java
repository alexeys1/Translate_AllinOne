package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WynntilsTaskTrackerTranslateManager {
    private static final WynntilsTaskTrackerTranslateManager INSTANCE = new WynntilsTaskTrackerTranslateManager();
    private static final Gson GSON = new Gson();
    private static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final int MAX_KEY_MISMATCH_BATCH_RETRIES = 1;

    private final WynntilsTaskTrackerTextCache cache = WynntilsTaskTrackerTextCache.getInstance();
    private final AtomicLong sessionEpoch = new AtomicLong(0L);

    private ExecutorService workerExecutor;
    private ScheduledExecutorService collectorExecutor;
    private ScheduledExecutorService retryExecutor;
    private int currentConcurrentRequests = -1;

    private WynntilsTaskTrackerTranslateManager() {
    }

    public static WynntilsTaskTrackerTranslateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        long newSessionEpoch = sessionEpoch.incrementAndGet();
        WynntilsTaskTrackerTranslationSupport.devLog("session_started epoch={}", newSessionEpoch);

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            ScoreboardConfig scoreboardConfig = Translate_AllinOne.getConfig().scoreboardTranslate;
            currentConcurrentRequests = scoreboardConfig == null ? 1 : Math.max(1, scoreboardConfig.max_concurrent_requests);
            workerExecutor = Executors.newFixedThreadPool(currentConcurrentRequests);
            for (int i = 0; i < currentConcurrentRequests; i++) {
                workerExecutor.submit(this::processingLoop);
            }
            WynntilsTaskTrackerTranslationSupport.devLog(
                    "workers_started count={}",
                    currentConcurrentRequests);
        }

        if (collectorExecutor == null || collectorExecutor.isShutdown()) {
            collectorExecutor = Executors.newSingleThreadScheduledExecutor();
            collectorExecutor.scheduleAtFixedRate(this::collectAndBatchItems, 0, 1, TimeUnit.SECONDS);
        }

        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor();
            retryExecutor.scheduleAtFixedRate(this::requeueErroredItems, 15, 15, TimeUnit.SECONDS);
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

        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdownNow();
        }
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

            int batchSize = 10;
            ScoreboardConfig scoreboardConfig = Translate_AllinOne.getConfig().scoreboardTranslate;
            if (scoreboardConfig != null) {
                batchSize = Math.max(1, scoreboardConfig.max_batch_size);
            }

            for (int i = 0; i < items.size(); i += batchSize) {
                int end = Math.min(items.size(), i + batchSize);
                cache.submitBatchForTranslation(items.subList(i, end));
            }
            WynntilsTaskTrackerTranslationSupport.devLog(
                    "collector_submitted items={} batch_size={}",
                    items.size(),
                    batchSize);
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error in Wynntils task tracker collector thread.", e);
        }
    }

    private void requeueErroredItems() {
        try {
            Set<String> erroredKeys = cache.getErroredKeys();
            for (String key : erroredKeys) {
                cache.requeueFromError(key);
            }
            if (!erroredKeys.isEmpty()) {
                WynntilsTaskTrackerTranslationSupport.devLog("requeued_errors count={}", erroredKeys.size());
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error while re-queueing Wynntils task tracker translation errors.", e);
        }
    }

    private void translateBatch(List<String> originalTexts, String targetLanguage, long batchSessionEpoch) {
        translateBatch(originalTexts, targetLanguage, 0, batchSessionEpoch);
    }

    private void translateBatch(
            List<String> originalTexts,
            String targetLanguage,
            int keyMismatchRetryCount,
            long batchSessionEpoch
    ) {
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
            cache.requeueFailed(Set.copyOf(originalTexts), "No routed model selected");
            WynntilsTaskTrackerTranslationSupport.devLog(
                    "missing_route batch_size={}",
                    originalTexts.size());
            return;
        }

        Map<String, String> batchForAI = new LinkedHashMap<>();
        for (int i = 0; i < originalTexts.size(); i++) {
            batchForAI.put(String.valueOf(i + 1), originalTexts.get(i));
        }

        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
        LLM llm = new LLM(settings);

        String systemPrompt = buildSystemPrompt(targetLanguage, providerProfile.activeSystemPromptSuffix());
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

        llm.getCompletion(messages, requestContext).whenComplete((response, error) -> {
            if (!isSessionActive(batchSessionEpoch)) {
                cache.releaseInProgress(Set.copyOf(originalTexts));
                return;
            }

            if (error != null) {
                if (isInternalPostprocessError(error) && originalTexts.size() > 1) {
                    for (String text : originalTexts) {
                        translateBatch(List.of(text), targetLanguage, batchSessionEpoch);
                    }
                    return;
                }

                cache.requeueFailed(Set.copyOf(originalTexts), error.getMessage());
                Translate_AllinOne.LOGGER.error(
                        "Failed to translate Wynntils task tracker batch. context={}",
                        requestContext,
                        error);
                return;
            }

            try {
                Matcher matcher = JSON_EXTRACT_PATTERN.matcher(response);
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
                    if (keyMismatchRetryCount < MAX_KEY_MISMATCH_BATCH_RETRIES) {
                        translateBatch(originalTexts, targetLanguage, keyMismatchRetryCount + 1, batchSessionEpoch);
                    } else {
                        cache.requeueFailed(Set.copyOf(originalTexts), "LLM response key mismatch");
                    }
                    return;
                }

                Map<String, String> finalTranslatedMap = new ConcurrentHashMap<>();
                Set<String> itemsToRequeue = ConcurrentHashMap.newKeySet();

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
                        itemsToRequeue.add(originalTemplate);
                        continue;
                    }

                    if (originalTemplate.contains("§") && !translatedTemplate.contains("§")) {
                        itemsToRequeue.add(originalTemplate);
                        continue;
                    }

                    finalTranslatedMap.put(originalTemplate, translatedTemplate);
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
                missingTranslations.addAll(itemsToRequeue);
                if (!missingTranslations.isEmpty()) {
                    cache.requeueFailed(missingTranslations, "LLM response missing keys");
                }
            } catch (JsonSyntaxException e) {
                cache.requeueFailed(Set.copyOf(originalTexts), "Invalid JSON response");
                Translate_AllinOne.LOGGER.error(
                        "Failed to parse Wynntils task tracker translation response. context={}",
                        requestContext,
                        e);
            } catch (Throwable t) {
                cache.requeueFailed(Set.copyOf(originalTexts), "Translation post-processing failure");
                Translate_AllinOne.LOGGER.error(
                        "Unexpected Wynntils task tracker post-processing error. context={}",
                        requestContext,
                        t);
            }
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
                + "4) Preserve tokens exactly: §a §l §r %s %d %f {d1} URLs numbers <...> {...} <s0> </s0> \\n \\t.\n"
                + "5) If unsure for a value, keep that value unchanged.\n"
                + "6) No extra text outside JSON.";
        return PromptMessageBuilder.appendSystemPromptSuffix(basePrompt, suffix);
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
                : truncate(normalizeWhitespace(originalTexts.getFirst()), 160);
        return "route=wynntils_task_tracker"
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + targetLanguage
                + ", batch=" + (originalTexts == null ? 0 : originalTexts.size())
                + ", messages=" + messageCount
                + ", sample=\"" + sample + "\"";
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

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String getTargetLanguage() {
        return WynntilsTaskTrackerTranslationSupport.getTargetLanguage();
    }
}
