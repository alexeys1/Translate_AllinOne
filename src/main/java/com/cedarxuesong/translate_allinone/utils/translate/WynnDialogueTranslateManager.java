package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.WynnDialogueTextCache;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.LlmPayloadJsonSupport;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WynnDialogueTranslateManager {
    private static final WynnDialogueTranslateManager INSTANCE = new WynnDialogueTranslateManager();
    private static final Gson GSON = LlmPayloadJsonSupport.gson();
    private static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final int MAX_KEY_MISMATCH_BATCH_RETRIES = 1;
    private static final int COLLECT_INTERVAL_MILLIS = 200;
    private static final int RETRY_INTERVAL_SECONDS = 20;
    private static final int MAX_BATCH_SIZE = 4;
    private static final int WORKER_COUNT = 1;

    private final WynnDialogueTextCache cache = WynnDialogueTextCache.getInstance();
    private final AtomicLong sessionEpoch = new AtomicLong(0L);

    private ExecutorService workerExecutor;
    private ScheduledExecutorService collectorExecutor;
    private ScheduledExecutorService retryExecutor;

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

        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor();
            retryExecutor.scheduleAtFixedRate(
                    this::requeueErroredItems,
                    RETRY_INTERVAL_SECONDS,
                    RETRY_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
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

        if (retryExecutor != null && !retryExecutor.isShutdown()) {
            retryExecutor.shutdownNow();
        }
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

    private void requeueErroredItems() {
        try {
            if (!WynnDialogueTranslationSupport.hasConfiguredRoute()) {
                return;
            }

            for (String key : cache.getErroredKeys()) {
                cache.requeueFromError(key);
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error while re-queueing Wynn dialogue cache errors", e);
        }
    }

    private void translateBatch(List<String> originalKeys, String targetLanguage, long batchSessionEpoch) {
        translateBatch(originalKeys, targetLanguage, 0, batchSessionEpoch);
    }

    private void translateBatch(
            List<String> originalKeys,
            String targetLanguage,
            int keyMismatchRetryCount,
            long batchSessionEpoch
    ) {
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
            cache.requeueFailed(Set.copyOf(originalKeys), "No routed model selected");
            WynnDialogueTranslationSupport.throttledDevLog(
                    "translate_skipped_no_route",
                    1500L,
                    "translate_skipped reason=no_route batchSize={}",
                    originalKeys.size()
            );
            return;
        }

        Map<String, String> batchForAI = new LinkedHashMap<>();
        for (int i = 0; i < originalKeys.size(); i++) {
            batchForAI.put(String.valueOf(i + 1), WynnDialogueTranslationSupport.extractTranslatableValue(originalKeys.get(i)));
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

        llm.getCompletion(messages, requestContext).whenComplete((response, error) -> {
            if (!isSessionActive(batchSessionEpoch)) {
                cache.releaseInProgress(Set.copyOf(originalKeys));
                return;
            }

            if (error != null) {
                if (isInternalPostprocessError(error) && originalKeys.size() > 1) {
                    for (String key : originalKeys) {
                        translateBatch(List.of(key), targetLanguage, batchSessionEpoch);
                    }
                    return;
                }

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
                return;
            }

            try {
                Matcher matcher = JSON_EXTRACT_PATTERN.matcher(response);
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
                    if (keyMismatchRetryCount < MAX_KEY_MISMATCH_BATCH_RETRIES) {
                        translateBatch(originalKeys, targetLanguage, keyMismatchRetryCount + 1, batchSessionEpoch);
                    } else {
                        cache.requeueFailed(Set.copyOf(originalKeys), "LLM response key mismatch");
                    }
                    return;
                }

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
                    cache.requeueFailed(missingTranslations, "LLM response missing keys");
                }
            } catch (JsonSyntaxException e) {
                cache.requeueFailed(Set.copyOf(originalKeys), "Invalid JSON response");
                Translate_AllinOne.LOGGER.error(
                        "Failed to parse Wynn dialogue translation response. context={}",
                        requestContext,
                        e
                );
            } catch (Throwable t) {
                cache.requeueFailed(Set.copyOf(originalKeys), "Translation post-processing failure");
                Translate_AllinOne.LOGGER.error(
                        "Unexpected Wynn dialogue post-processing error. context={}",
                        requestContext,
                        t
                );
            }
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
            WynnDialogueTextCache.LookupResult lookupResult = cache.peek(key);
            if (lookupResult.status() == WynnDialogueTextCache.TranslationStatus.TRANSLATED
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

    private String buildSystemPrompt(String targetLanguage, String suffix) {
        String basePrompt = "Translate WynnCraft NPC dialogue or quest text into " + targetLanguage + ".\n"
                + "\n"
                + "Input is a JSON object; output one valid JSON object only.\n"
                + "\n"
                + "Rules:\n"
                + "1) Keep all keys and key count unchanged; translate values only.\n"
                + "2) Preserve original semantics, logic, and paragraph format. Use natural target-language phrasing — no mixed-language output.\n"
                + "3) Do not translate proper nouns: place names (e.g. Ragni, Troms, Detlas), character names, item names, and [bracketed] item references.\n"
                + "4) Render poetic or literary dialogue with rhythmic expression (e.g. \"To what end?\").\n"
                + "5) Preserve special characters including zalgo/corrupted text exactly as-is.\n"
                + "6) Preserve tokens exactly: § codes {player} {d1} %s %d %f URLs numbers <...> {...} <s0> </s0> \\n \\t.\n"
                + "7) If unsure, keep the value unchanged.\n"
                + "8) No extra text outside JSON.";
        return PromptMessageBuilder.appendSystemPromptSuffix(basePrompt, suffix);
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
                : truncate(normalizeWhitespace(WynnDialogueTranslationSupport.extractTranslatableValue(originalKeys.getFirst())), 160);
        return "route=wynn_npc_dialogue"
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + targetLanguage
                + ", batch=" + (originalKeys == null ? 0 : originalKeys.size())
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
}
