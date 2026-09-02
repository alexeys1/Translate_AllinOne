package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import com.alexeys.translate_allinone.utils.cache.WynnDialogueTextCache;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public final class WynnDialogueTranslateManager {
    private static final WynnDialogueTranslateManager INSTANCE = new WynnDialogueTranslateManager();
    private static final Gson GSON = LlmPayloadJsonSupport.gson();
    private static final int COLLECT_INTERVAL_MILLIS = 200;
    private static final int MAX_BATCH_SIZE = 4;
    private static final int WORKER_COUNT = 1;
    private static final int MAX_IN_FLIGHT_REQUESTS = 1;

    private final WynnDialogueTextCache cache = WynnDialogueTextCache.getInstance();
    private final AtomicLong sessionEpoch = new AtomicLong(0L);
    private final TranslationRequestLimiter requestLimiter = new TranslationRequestLimiter(MAX_IN_FLIGHT_REQUESTS);

    private ExecutorService workerExecutor;
    private ScheduledExecutorService collectorExecutor;

    private WynnDialogueTranslateManager() {
    }

    public static WynnDialogueTranslateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        sessionEpoch.incrementAndGet();

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            workerExecutor = Executors.newFixedThreadPool(WORKER_COUNT);
            for (int i = 0; i < WORKER_COUNT; i++) {
                workerExecutor.submit(this::processingLoop);
            }
        }

        if (collectorExecutor == null || collectorExecutor.isShutdown()) {
            collectorExecutor = Executors.newSingleThreadScheduledExecutor();
            collectorExecutor.scheduleAtFixedRate(
                    this::collectAndBatchItems,
                    0,
                    COLLECT_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS
            );
        }

    }

    public synchronized void stop() {
        sessionEpoch.incrementAndGet();

        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdownNow();
            try {
                workerExecutor.awaitTermination(5, TimeUnit.SECONDS);
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
                if (!WynnDialogueTranslationSupport.isTranslationFeatureEnabled()) {
                    TimeUnit.SECONDS.sleep(1);
                    continue;
                }

                batch = cache.takeBatchForTranslation();
                cache.markAsInProgress(batch);

                if (!isSessionActive(batchSessionEpoch)) {
                    cache.releaseInProgress(Set.copyOf(batch));
                    continue;
                }

                translateBatch(batch, WynnDialogueTranslationSupport.getTargetLanguage(), batchSessionEpoch);
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
                Translate_AllinOne.LOGGER.error("Unexpected error in Wynn dialogue processing loop.", e);
            }
        }
    }

    private void collectAndBatchItems() {
        try {
            if (!WynnDialogueTranslationSupport.isTranslationFeatureEnabled()) {
                return;
            }

            List<String> items = cache.drainAllPendingItems();
            if (items.isEmpty()) {
                return;
            }

            for (int i = 0; i < items.size(); i += MAX_BATCH_SIZE) {
                int end = Math.min(items.size(), i + MAX_BATCH_SIZE);
                cache.submitBatchForTranslation(List.copyOf(items.subList(i, end)));
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error in Wynn dialogue collector thread", e);
        }
    }

    private void translateBatch(
            List<String> originalKeys,
            String targetLanguage,
            long batchSessionEpoch
    ) throws InterruptedException {
        if (originalKeys == null || originalKeys.isEmpty()) {
            return;
        }

        if (!isSessionActive(batchSessionEpoch)) {
            cache.releaseInProgress(Set.copyOf(originalKeys));
            return;
        }

        ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                ProviderRouteResolver.Route.WYNN_NPC_DIALOGUE
        );
        if (providerProfile == null) {
            Translate_AllinOne.LOGGER.warn(
                    "No routed provider/model configured for Wynn dialogue translation; re-queueing {} items.",
                    originalKeys.size()
            );
            cache.requeueFailed(Set.copyOf(originalKeys), "No routed model selected");
            return;
        }
        if (ProviderRouteResolver.hasApiKeyDecryptFailure(Translate_AllinOne.getConfig(), ProviderRouteResolver.Route.WYNN_NPC_DIALOGUE)) {
            ApiKeyDecryptFailureNotifier.notifyRuntimeIfPresent();
            cache.requeueFailed(Set.copyOf(originalKeys), "API key decryption failed");
            return;
        }

        Map<String, String> batchForAI = new LinkedHashMap<>();
        for (int i = 0; i < originalKeys.size(); i++) {
            batchForAI.put(String.valueOf(i + 1), WynnDialogueTranslationSupport.extractTranslatableValue(originalKeys.get(i)));
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
                providerProfile.activeInjectSystemPromptIntoUserMessage()
        );
        String requestContext = buildRequestContext(providerProfile, targetLanguage, originalKeys, messages);
        WynnDialogueTranslationSupport.throttledDevLog(
                "llm_submit",
                1000L,
                "llm_submit context={} payload={}",
                requestContext,
                userPrompt == null ? "" : userPrompt.replace("\n", "\\n")
        );

        TranslationRequestLimiter.Permit requestPermit = requestLimiter.acquire();
        if (!isSessionActive(batchSessionEpoch)) {
            requestPermit.close();
            cache.releaseInProgress(Set.copyOf(originalKeys));
            return;
        }

        CompletableFuture<String> completion;
        long watchdogRequestId;
        try {
            completion = llm.getCompletion(messages, requestContext);
            watchdogRequestId = TranslationQueueWatchdog.requestStarted("wynn_dialogue", originalKeys);
        } catch (RuntimeException | Error startError) {
            requestPermit.close();
            throw startError;
        }
        completion.whenComplete((response, error) -> {
            requestPermit.close();
            if (!isSessionActive(batchSessionEpoch)) {
                TranslationQueueWatchdog.requestSuperseded(watchdogRequestId);
                cache.releaseInProgress(Set.copyOf(originalKeys));
                return;
            }

            Set<String> failedTaskKeys = ConcurrentHashMap.newKeySet();

            if (error != null) {
                failedTaskKeys.addAll(originalKeys);
                cache.requeueFailed(Set.copyOf(originalKeys), error.getMessage());
                WynnDialogueTranslationSupport.throttledDevLog(
                        "llm_error",
                        1000L,
                        "llm_error context={} error=\"{}\"",
                        requestContext,
                        error.getMessage() == null ? "" : error.getMessage()
                );
                Translate_AllinOne.LOGGER.error(
                        "Failed to translate Wynn dialogue batch. context={}",
                        requestContext,
                        error
                );
            } else {
                try {
                    Matcher matcher = TranslateStringUtils.JSON_EXTRACT_PATTERN.matcher(response);
                    if (!matcher.find()) {
                        throw new JsonSyntaxException("No JSON object found in the translation response.");
                    }

                    String jsonResponse = matcher.group();
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> translatedMapFromAI = GSON.fromJson(jsonResponse, type);
                    if (translatedMapFromAI == null) {
                        throw new JsonSyntaxException("Parsed translation result is null");
                    }
                    WynnDialogueTranslationSupport.throttledDevLog(
                            "llm_response",
                            1000L,
                            "llm_response context={} response={}",
                            requestContext,
                            response == null ? "" : response.replace("\n", "\\n")
                    );

                    if (hasKeyMismatch(translatedMapFromAI, originalKeys.size())) {
                        failedTaskKeys.addAll(originalKeys);
                        Translate_AllinOne.LOGGER.warn(
                                "Wynn dialogue response keys mismatched. context={}",
                                requestContext
                        );
                        cache.requeueFailed(Set.copyOf(originalKeys), "LLM response key mismatch");
                    } else {
                        Map<String, String> finalTranslatedMap = new ConcurrentHashMap<>();
                        Set<String> missingTranslations = ConcurrentHashMap.newKeySet();
                        missingTranslations.addAll(originalKeys);

                        for (Map.Entry<String, String> entry : translatedMapFromAI.entrySet()) {
                            int index;
                            try {
                                index = Integer.parseInt(entry.getKey()) - 1;
                            } catch (NumberFormatException e) {
                                continue;
                            }

                            if (index < 0 || index >= originalKeys.size()) {
                                continue;
                            }

                            String originalKey = originalKeys.get(index);
                            String translatedValue = entry.getValue();
                            if (translatedValue == null || translatedValue.trim().isEmpty()) {
                                continue;
                            }

                            finalTranslatedMap.put(originalKey, translatedValue);
                            missingTranslations.remove(originalKey);
                        }

                        if (!finalTranslatedMap.isEmpty()) {
                            cache.updateTranslations(finalTranslatedMap);
                            Map<String, String> acceptedTranslatedMap = retainAcceptedCacheTranslations(finalTranslatedMap);
                            WynnDialogueTranslationSupport.onCacheTranslationsUpdated(acceptedTranslatedMap);
                            WynnDialogueTranslationSupport.throttledDevLog(
                                    "cache_updated",
                                    1000L,
                                    "cache_updated count={} accepted={} context={}",
                                    finalTranslatedMap.size(),
                                    acceptedTranslatedMap.size(),
                                    requestContext
                            );
                        }

                        if (!missingTranslations.isEmpty()) {
                            failedTaskKeys.addAll(missingTranslations);
                            Translate_AllinOne.LOGGER.warn(
                                    "Wynn dialogue LLM response missing {} keys. context={}",
                                    missingTranslations.size(),
                                    requestContext
                            );
                            cache.requeueFailed(missingTranslations, "LLM response missing keys");
                        }
                    }
                } catch (JsonSyntaxException e) {
                    failedTaskKeys.addAll(originalKeys);
                    cache.requeueFailed(Set.copyOf(originalKeys), "Invalid JSON response");
                    Translate_AllinOne.LOGGER.error(
                            "Failed to parse Wynn dialogue translation response. context={}",
                            requestContext,
                            e
                    );
                } catch (Throwable t) {
                    failedTaskKeys.addAll(originalKeys);
                    cache.requeueFailed(Set.copyOf(originalKeys), "Translation post-processing failure");
                    Translate_AllinOne.LOGGER.error(
                            "Unexpected Wynn dialogue post-processing error. context={}",
                            requestContext,
                            t
                    );
                }
            }

            TranslationQueueWatchdog.requestCompleted(
                    watchdogRequestId,
                    failedTaskKeys,
                    false
            );
        });
    }

    private Map<String, String> retainAcceptedCacheTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return Map.of();
        }

        Map<String, String> acceptedTranslations = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            LookupResult lookupResult = cache.peek(key);
            if (lookupResult.status() == TranslationStatus.TRANSLATED
                    && Objects.equals(lookupResult.translation(), value)) {
                acceptedTranslations.put(key, value);
            }
        }
        return acceptedTranslations;
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
        String basePrompt = PromptMessageBuilder.getDefaultPrompt("wynn_npc_dialogue", targetLanguage);
        String resolved = PromptMessageBuilder.applyPromptOverride("wynn_npc_dialogue", basePrompt, overrides, targetLanguage);
        return PromptMessageBuilder.appendForcedProtectedDataContract(
                PromptMessageBuilder.appendSystemPromptSuffix(resolved, suffix),
                "wynn_npc_dialogue"
        );
    }

    private String buildRequestContext(
            ApiProviderProfile profile,
            String targetLanguage,
            List<String> originalKeys,
            List<OpenAIRequest.Message> messages
    ) {
        String providerId = profile == null ? "" : profile.id;
        String modelId = profile == null ? "" : profile.model_id;
        int messageCount = messages == null ? 0 : messages.size();
        String sample = originalKeys == null || originalKeys.isEmpty()
                ? ""
                : TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(WynnDialogueTranslationSupport.extractTranslatableValue(originalKeys.getFirst())), 160);
        return "route=wynn_npc_dialogue"
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + targetLanguage
                + ", batch=" + (originalKeys == null ? 0 : originalKeys.size())
                + ", messages=" + messageCount
                + ", sample=\"" + sample + "\"";
    }

}
