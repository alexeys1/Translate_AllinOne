package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.mixin.mixinChatHud.ChatHudAccessor;
import com.alexeys.translate_allinone.utils.TranslateStringUtils;
import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.MessageUtils;
import com.alexeys.translate_allinone.utils.TranslateExceptionUtils;
import com.alexeys.translate_allinone.utils.cache.ChatOutputTranslationCache;
import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.SkyblockNpcTranslationCache;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.llmapi.LLM;
import com.alexeys.translate_allinone.utils.llmapi.LlmRequestLifecycle;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import com.alexeys.translate_allinone.utils.text.TemplateProcessor;
import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatOutputTranslateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatOutputTranslateManager.class);
    private static final String CHAT_TRANSLATE_ACTION = "translate";
    private static final String CHAT_RESTORE_ACTION = "restore";
    private static final Map<UUID, ChatHudLine> activeTranslationLines = new ConcurrentHashMap<>();
    private static final Map<UUID, Text> pendingAnimationSources = new ConcurrentHashMap<>();
    private static final Map<UUID, Text> pendingAutoTranslateMessages = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> streamingUpdateLastApplied = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> translationGenerations = new ConcurrentHashMap<>();
    private static final AtomicLong translationGeneration = new AtomicLong();
    private static final Map<UUID, Integer> lineLocateRetryCounts = new ConcurrentHashMap<>();
    private static final TranslationRequestSingleFlight<String, String> inFlightTranslations =
            new TranslationRequestSingleFlight<>();
    private static ExecutorService translationExecutor;
    private static int currentConcurrentRequests = -1;
    private static final int MAX_LINE_LOCATE_RETRIES = 4;
    private static final long LINE_LOCATE_RETRY_DELAY_MS = 40L;
    private static final long ROUTE_ERROR_DISPLAY_MS = 2_000L;
    private static final String TRANSLATION_ERROR_KEY = "text.translate_allinone.chat.output_translation_error";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL);
    private static final Pattern CHAT_IGNORABLE_PLACEHOLDER_PATTERN = Pattern.compile("\\{c(\\d+)}");
    private static final String SKYBLOCK_NPC_FORMATTED_UNIT = "(?:\u00A7[0-9a-fk-or]|[^\u00A7\\r\\n])";
    private static final String SKYBLOCK_NPC_NAME_TOKEN = "[\\p{L}\\p{N}_.'-]+";
    private static final Pattern SKYBLOCK_NPC_CHAT_PATTERN = Pattern.compile(
            "^(?:\\[CHAT\\] )?(?=[^:\\r\\n]*\u00A7[0-9a-fk-or])(?:\u00A7r)?(?:\u00A7e)?\\[NPC\\] (?:\u00A7r)?(?:\u00A7[0-9a-f])?"
                    + SKYBLOCK_NPC_NAME_TOKEN + "(?: " + SKYBLOCK_NPC_NAME_TOKEN + ")*"
                    + "(?:\u00A7r)?(?:\u00A7f)?: (?:(?:\u00A7r)?\u00A7f)?"
                    + "(?<body>" + SKYBLOCK_NPC_FORMATTED_UNIT + "+?)(?:\u00A7[0-9a-fk-or])*"
                    + "(?:\\s+\\[T\\])?$"
    );

    private static final Pattern STYLE_TAG_MARKER_PATTERN = Pattern.compile("</?s\\d+>");
    private static final String SPEAKER_NAME_TOKEN = "[\\p{L}\\p{N}_.'\u2019\\-]+";
    private static final String SPEAKER_NAME_PATTERN = "\\s*" + SPEAKER_NAME_TOKEN + "(?:\\s+" + SPEAKER_NAME_TOKEN + "){0,3}\\s*";
    private static final Pattern SPEAKER_HEADER_PATTERN = Pattern.compile(
            "^(?:"
                    + "<[^<>\\r\\n]+>\\s+"
                    + "|"
                    + "(?:\\[[^\\]\\r\\n]+]\\s*)*"
                    + "(?:"
                    + "<[^<>\\r\\n]+>"
                    + "|"
                    + SPEAKER_NAME_PATTERN
                    + ")"
                    + ":\\s+"
                    + ")"
    );

    public static Text buildOriginalMessageWithToggle(UUID messageId, Text originalMessage) {
        return appendToggleButton(messageId, originalMessage, CHAT_TRANSLATE_ACTION, "text.translate_allinone.translate_button_hover");
    }

    public static Text buildTranslatedMessageWithToggle(UUID messageId, Text translatedMessage) {
        return buildTranslatedMessageWithToggle(messageId, translatedMessage, null);
    }

    public static Text buildTranslatedMessageWithToggle(UUID messageId, Text translatedMessage, Text originalMessage) {
        return appendToggleButton(
                messageId,
                applyOriginalDisplayMode(translatedMessage, originalMessage),
                CHAT_RESTORE_ACTION,
                "text.translate_allinone.restore_button_hover"
        );
    }

    public static void logInterceptedMessage(UUID messageId, Text originalMessage, String plainText, boolean autoTranslate) {
        if (!shouldLogInterceptedMessage()) {
            return;
        }

        LOGGER.info(
                "[ChatOutputDev:intercept] messageId={} autoTranslate={} rawText=\"{}\" plainText=\"{}\" segments={}",
                messageId,
                autoTranslate,
                escapeForLog(originalMessage == null ? "" : originalMessage.getString()),
                escapeForLog(plainText),
                describeTextSegments(originalMessage)
        );
    }

    public static boolean isSkyblockNpcMessage(Text message) {
        if (message == null) {
            return false;
        }

        Matcher matcher = SKYBLOCK_NPC_CHAT_PATTERN.matcher(LegacyComponentTextCodec.encode(message));
        if (!matcher.matches()) {
            return false;
        }

        String body = AnimationManager.stripFormatting(matcher.group("body"));
        return body != null && !body.trim().isEmpty();
    }

    private static synchronized void updateExecutorServiceIfNeeded() {
        int configuredConcurrentRequests = Translate_AllinOne.getConfig().chatTranslate.output.max_concurrent_requests;
        if (translationExecutor == null || configuredConcurrentRequests != currentConcurrentRequests) {
            if (translationExecutor != null) {
                translationExecutor.shutdown();
                LOGGER.info("Shutting down old translation executor service...");
            }
            translationExecutor = Executors.newFixedThreadPool(Math.max(1, configuredConcurrentRequests), r -> {
                Thread t = new Thread(r, "Translate-Queue-Processor");
                t.setDaemon(true);
                return t;
            });
            currentConcurrentRequests = configuredConcurrentRequests;
            LOGGER.info("Translation executor service configured with {} concurrent threads.", currentConcurrentRequests);
        }
    }

    public static void translate(UUID messageId, Text originalMessage) {
        translate(messageId, originalMessage, originalMessage, false);
    }

    public static void translateManually(UUID messageId, Text originalMessage) {
        Text lineToLocate = originalMessage;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null && client.inGameHud.getChatHud() != null) {
            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) client.inGameHud.getChatHud();
            LineSearchResult searchResult = findTargetLine(
                    chatHudAccessor.getMessages(),
                    messageId,
                    CHAT_TRANSLATE_ACTION
            );
            if (searchResult != null) {
                lineToLocate = searchResult.line().content();
            }
        }
        translate(messageId, originalMessage, lineToLocate, false);
    }

    public static void forceRefreshTranslation(UUID messageId) {
        if (!TranslationFeatureGate.isEnabled() || messageId == null) {
            return;
        }

        MessageUtils.TrackedChatMessage trackedMessage = MessageUtils.getTrackedChatMessage(messageId);
        if (trackedMessage == null
                || !trackedMessage.showingTranslated()
                || trackedMessage.originalMessage() == null
                || trackedMessage.translatedMessage() == null) {
            return;
        }

        translate(messageId, trackedMessage.originalMessage(), trackedMessage.translatedMessage(), true);
    }

    private static void translate(UUID messageId, Text originalMessage, Text lineToLocate, boolean forceRefresh) {
        if (!TranslationFeatureGate.isEnabled() || messageId == null || originalMessage == null || lineToLocate == null) {
            return;
        }
        if (activeTranslationLines.containsKey(messageId)) {
            lineLocateRetryCounts.remove(messageId);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ChatHud chatHud = client.inGameHud.getChatHud();
        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<ChatHudLine> messages = chatHudAccessor.getMessages();
        LineSearchResult searchResult = findTargetLine(messages, lineToLocate);

        if (searchResult == null) {
            if (scheduleLineLocateRetry(messageId, originalMessage, lineToLocate, forceRefresh)) {
                return;
            }
            LOGGER.error("Could not find chat line to update for messageId: {} after {} retries", messageId, MAX_LINE_LOCATE_RETRIES);
            lineLocateRetryCounts.remove(messageId);
            if (!isLineContentStillPresent(messages, lineToLocate)) {
                MessageUtils.removeTrackedMessage(messageId);
            }
            return;
        }
        lineLocateRetryCounts.remove(messageId);
        int lineIndex = searchResult.lineIndex();
        ChatHudLine targetLine = searchResult.line();
        logChatLineMapping(messageId, "locate_original", lineIndex, targetLine.content());

        ChatTranslateConfig.ChatOutputTranslateConfig chatOutputConfig = Translate_AllinOne.getConfig().chatTranslate.output;
        boolean skyblockNpcMessage = isSkyblockNpcMessage(originalMessage);
        PreparedChatTranslation preparedTranslation = prepareTranslationPayload(originalMessage);
        String chatOutputCacheKey = buildChatOutputCacheKey(
                chatOutputConfig.target_language,
                preparedTranslation.textToTranslate()
        );
        String skyblockCacheKey = !skyblockNpcMessage
                ? null
                : buildSkyblockNpcCacheKey(chatOutputConfig.target_language, preparedTranslation.textToTranslate());
        if (forceRefresh) {
            if (chatOutputCacheKey != null) {
                ChatOutputTranslationCache.getInstance().forceRefresh(List.of(chatOutputCacheKey));
            }
            if (skyblockCacheKey != null) {
                SkyblockNpcTranslationCache.getInstance().forceRefresh(List.of(skyblockCacheKey));
            }
        }
        LookupResult chatOutputCacheLookup = chatOutputCacheKey == null
                ? null
                : ChatOutputTranslationCache.getInstance().peek(chatOutputCacheKey);
        LookupResult skyblockCacheLookup = skyblockCacheKey == null
                ? null
                : SkyblockNpcTranslationCache.getInstance().peek(skyblockCacheKey);
        String cachedTranslation = resolveCachedTranslation(skyblockCacheLookup, chatOutputCacheLookup);
        if (cachedTranslation != null) {
            long requestGeneration = translationGeneration.incrementAndGet();
            activeTranslationLines.put(messageId, targetLine);
            translationGenerations.put(messageId, requestGeneration);
            completeCachedTranslation(messageId, requestGeneration, cachedTranslation, preparedTranslation);
            return;
        }
        String cachedFailure = resolveCachedFailure(skyblockCacheLookup, chatOutputCacheLookup);
        if (cachedFailure != null) {
            long requestGeneration = translationGeneration.incrementAndGet();
            activeTranslationLines.put(messageId, targetLine);
            translationGenerations.put(messageId, requestGeneration);
            completeCachedFailure(messageId, requestGeneration, cachedFailure);
            return;
        }

        updateExecutorServiceIfNeeded();

        Text placeholderText = AnimationManager.getAnimatedStyledText(originalMessage);

        ChatHudLine newLine = new ChatHudLine(targetLine.creationTick(), placeholderText, targetLine.signature(), targetLine.indicator());
        int scrolledLines = chatHudAccessor.getScrolledLines();
        messages.set(lineIndex, newLine);
        activeTranslationLines.put(messageId, newLine);
        pendingAnimationSources.put(messageId, originalMessage);
        long requestGeneration = translationGeneration.incrementAndGet();
        translationGenerations.put(messageId, requestGeneration);
        chatHudAccessor.invokeRefresh();
        chatHudAccessor.setScrolledLines(scrolledLines);

        final long finalRequestGeneration = requestGeneration;
        final String requestSingleFlightKey = chatOutputCacheKey == null
                ? "uncached\u001f" + preparedTranslation.textToTranslate()
                : chatOutputCacheKey;
        translationExecutor.submit(() -> {
            String requestContext = "route=chat_output,messageId=" + messageId;
            long watchdogRequestId = 0L;
            TranslationRequestSingleFlight.Claim<String> sharedClaim = null;
            try {
                if (!isTranslationActive(messageId, finalRequestGeneration)) {
                    return;
                }
                LookupResult lateChatOutputCacheLookup = chatOutputCacheKey == null
                        ? null
                        : ChatOutputTranslationCache.getInstance().peek(chatOutputCacheKey);
                LookupResult lateSkyblockCacheLookup = skyblockCacheKey == null
                        ? null
                        : SkyblockNpcTranslationCache.getInstance().peek(skyblockCacheKey);
                String lateCachedTranslation = resolveCachedTranslation(lateSkyblockCacheLookup, lateChatOutputCacheLookup);
                if (lateCachedTranslation != null) {
                    completeCachedTranslation(messageId, finalRequestGeneration, lateCachedTranslation, preparedTranslation);
                    return;
                }
                String lateCachedFailure = resolveCachedFailure(lateSkyblockCacheLookup, lateChatOutputCacheLookup);
                if (lateCachedFailure != null) {
                    completeCachedFailure(messageId, finalRequestGeneration, lateCachedFailure);
                    return;
                }
                ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                        Translate_AllinOne.getConfig(),
                        ProviderRouteResolver.Route.CHAT_OUTPUT
                );

                if (providerProfile == null) {
                    LOGGER.warn("No routed model selected for chat output translation; showing temporary error for messageId={}", messageId);
                    showTemporaryRouteError(messageId, chatHudAccessor, messages, lineIndex, targetLine);
                    return;
                }

                String textToTranslate = preparedTranslation.textToTranslate();
                Map<Integer, Style> styleMap = preparedTranslation.styleMap();
                sharedClaim = inFlightTranslations.acquire(requestSingleFlightKey);
                if (!sharedClaim.owner()) {
                    String finalTranslation = sharedClaim.future().join();
                    cacheTranslation(skyblockCacheKey, chatOutputCacheKey, finalTranslation);
                    Text finalStyledText = rebuildTranslatedText(finalTranslation, preparedTranslation);
                    logReflowResult(
                            messageId,
                            false,
                            finalTranslation,
                            finalTranslation,
                            finalStyledText,
                            styleMap
                    );
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                    return;
                }

                ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
                LLM llm = new LLM(settings);

                List<OpenAIRequest.Message> apiMessages = getMessages(providerProfile, chatOutputConfig.target_language, textToTranslate);
                requestContext = buildRequestContext(providerProfile, chatOutputConfig.target_language, textToTranslate, apiMessages, chatOutputConfig.streaming_response, messageId);
                logLlmSubmission(messageId, providerProfile, chatOutputConfig, originalMessage, textToTranslate, styleMap, apiMessages, requestContext);

                LOGGER.info("Starting translation for message ID: {}. Marked text: {}", messageId, textToTranslate);
                watchdogRequestId = TranslationQueueWatchdog.requestStarted(
                        "chat_output",
                        List.of(requestSingleFlightKey)
                );

                if (chatOutputConfig.streaming_response) {
                    final StringBuilder rawResponseBuffer = new StringBuilder();
                    final StringBuilder fullResponseBuffer = new StringBuilder();
                    final StringBuilder visibleContentBuffer = new StringBuilder();
                    final AtomicBoolean inThinkTag = new AtomicBoolean(false);

                    LlmRequestLifecycle.consume(llm.getStreamingCompletion(apiMessages, requestContext), chunk -> {
                        fullResponseBuffer.append(chunk);
                        rawResponseBuffer.append(chunk);

                        while (true) {
                            if (inThinkTag.get()) {
                                int endTagIndex = rawResponseBuffer.indexOf("</think>");
                                if (endTagIndex != -1) {
                                    inThinkTag.set(false);
                                    rawResponseBuffer.delete(0, endTagIndex + "</think>".length());
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Text.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    continue;
                                } else {
                                    int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                    if (startTagIndex != -1) {
                                        String thinkContent = rawResponseBuffer.substring(startTagIndex + "<think>".length());
                                        scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Text.literal("Thinking: ").append(thinkContent).formatted(Formatting.GRAY));
                                    }
                                    break;
                                }
                            } else {
                                int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                if (startTagIndex != -1) {
                                    String translationPart = rawResponseBuffer.substring(0, startTagIndex);
                                    visibleContentBuffer.append(translationPart);
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Text.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));

                                    rawResponseBuffer.delete(0, startTagIndex);
                                    inThinkTag.set(true);
                                    continue;
                                } else {
                                    visibleContentBuffer.append(rawResponseBuffer.toString());
                                    rawResponseBuffer.setLength(0);
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Text.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    break;
                                }
                            }
                        }
                    });

                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                    watchdogRequestId = 0L;

                    Text finalStyledText = rebuildTranslatedText(visibleContentBuffer.toString().stripLeading(), preparedTranslation);
                    String finalTranslation = visibleContentBuffer.toString().stripLeading();
                    if (finalTranslation.isBlank()) {
                        throw new IllegalStateException("Provider returned an empty translation");
                    }
                    logReflowResult(
                            messageId,
                            true,
                            fullResponseBuffer.toString(),
                            visibleContentBuffer.toString().stripLeading(),
                            finalStyledText,
                            styleMap
                    );
                    cacheTranslation(skyblockCacheKey, chatOutputCacheKey, finalTranslation);
                    inFlightTranslations.complete(requestSingleFlightKey, sharedClaim, finalTranslation);
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                } else {
                    String result = llm.getCompletion(apiMessages, requestContext).join();
                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                    watchdogRequestId = 0L;
                    LOGGER.info("Finished translation for message ID: {}. Result: {}", messageId, result);
                    final String finalTranslation = result.stripLeading();
                    if (finalTranslation.isBlank()) {
                        throw new IllegalStateException("Provider returned an empty translation");
                    }
                    Text finalStyledText = rebuildTranslatedText(finalTranslation, preparedTranslation);
                    logReflowResult(messageId, false, result, finalTranslation, finalStyledText, styleMap);
                    cacheTranslation(skyblockCacheKey, chatOutputCacheKey, finalTranslation);
                    inFlightTranslations.complete(requestSingleFlightKey, sharedClaim, finalTranslation);
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                }
            } catch (Exception e) {
                Throwable cause = TranslateExceptionUtils.unwrapThrowable(e);
                if (sharedClaim != null && sharedClaim.owner() && !sharedClaim.future().isDone()) {
                    if (isTranslationActive(messageId, finalRequestGeneration)) {
                        cacheTranslationFailure(skyblockCacheKey, chatOutputCacheKey, cause.getMessage());
                    }
                    inFlightTranslations.fail(requestSingleFlightKey, sharedClaim, cause);
                } else if (sharedClaim != null
                        && sharedClaim.future().isCompletedExceptionally()
                        && isTranslationActive(messageId, finalRequestGeneration)) {
                    cacheTranslationFailure(skyblockCacheKey, chatOutputCacheKey, cause.getMessage());
                }
                if (watchdogRequestId != 0L) {
                    TranslationQueueWatchdog.requestFailed(watchdogRequestId, false);
                }
                LOGGER.error("[Translate-Thread] Exception for message ID: {}. context={}", messageId, requestContext, cause);
                completeCachedFailure(messageId, finalRequestGeneration, cause.getMessage());
            }
        });
    }

    public static void restoreOriginal(UUID messageId) {
        if (messageId == null || activeTranslationLines.containsKey(messageId)) {
            return;
        }

        MessageUtils.TrackedChatMessage trackedMessage = MessageUtils.getTrackedChatMessage(messageId);
        if (trackedMessage == null) {
            return;
        }

        Text originalMessage = trackedMessage.originalMessage();
        Text translatedMessage = trackedMessage.translatedMessage();
        if (originalMessage == null || translatedMessage == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            ChatHud chatHud = client.inGameHud == null ? null : client.inGameHud.getChatHud();
            if (chatHud == null) {
                return;
            }

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<ChatHudLine> messages = chatHudAccessor.getMessages();
            LineSearchResult searchResult = findTargetLine(messages, translatedMessage);
            if (searchResult == null) {
                return;
            }

            int scrolledLines = chatHudAccessor.getScrolledLines();
            Text restoredContent = buildOriginalMessageWithToggle(messageId, originalMessage);
            ChatHudLine targetLine = searchResult.line();
            ChatHudLine restoredLine = new ChatHudLine(targetLine.creationTick(), restoredContent, targetLine.signature(), targetLine.indicator());
            messages.set(searchResult.lineIndex(), restoredLine);
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
            MessageUtils.markShowingOriginal(messageId);
        });
    }

    private static void scheduleInProgressChatLineUpdate(UUID messageId, long requestGeneration, Text newContent) {
        long now = System.currentTimeMillis();
        Long lastApplied = streamingUpdateLastApplied.get(messageId);
        if (lastApplied != null && now - lastApplied < 100L) {
            return;
        }
        streamingUpdateLastApplied.put(messageId, now);
        updateInProgressChatLine(messageId, requestGeneration, newContent);
    }

    private static void updateInProgressChatLine(UUID messageId, long requestGeneration, Text newContent) {
        if (!isTranslationActive(messageId, requestGeneration)) {
            return;
        }
        pendingAnimationSources.remove(messageId);
        ChatHudLine lineToUpdate = activeTranslationLines.get(messageId);
        if (lineToUpdate == null) return;

        MinecraftClient.getInstance().execute(() -> {
            if (!isTranslationActive(messageId, requestGeneration)) {
                return;
            }
            ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<ChatHudLine> messages = chatHudAccessor.getMessages();
            int scrolledLines = chatHudAccessor.getScrolledLines();

            int lineIndex = messages.indexOf(lineToUpdate);

            if (lineIndex != -1) {
                ChatHudLine newLine = new ChatHudLine(lineToUpdate.creationTick(), newContent, lineToUpdate.signature(), lineToUpdate.indicator());
                messages.set(lineIndex, newLine);
                activeTranslationLines.put(messageId, newLine);
                chatHudAccessor.invokeRefresh();
                chatHudAccessor.setScrolledLines(scrolledLines);
            }
        });
    }

    public static void animatePendingChatLines() {
        if (pendingAnimationSources.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }

        ChatHud chatHud = client.inGameHud.getChatHud();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<ChatHudLine> messages = chatHudAccessor.getMessages();
        int scrolledLines = chatHudAccessor.getScrolledLines();
        boolean updated = false;

        for (Map.Entry<UUID, Text> entry : pendingAnimationSources.entrySet()) {
            UUID messageId = entry.getKey();
            if (!isTranslationActive(messageId)) {
                pendingAnimationSources.remove(messageId);
                continue;
            }

            ChatHudLine activeLine = activeTranslationLines.get(messageId);
            Text source = entry.getValue();
            if (activeLine == null || source == null) {
                continue;
            }

            int lineIndex = messages.indexOf(activeLine);
            if (lineIndex == -1) {
                continue;
            }

            ChatHudLine animatedLine = new ChatHudLine(
                    activeLine.creationTick(),
                    AnimationManager.getAnimatedStyledText(source),
                    activeLine.signature(),
                    activeLine.indicator()
            );
            messages.set(lineIndex, animatedLine);
            activeTranslationLines.put(messageId, animatedLine);
            updated = true;
        }

        if (updated) {
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
        }
    }

    private static void updateChatLineWithFinalText(UUID messageId, long requestGeneration, Text finalContent) {
        if (!isTranslationActive(messageId, requestGeneration)) {
            return;
        }
        pendingAnimationSources.remove(messageId);
        lineLocateRetryCounts.remove(messageId);
        streamingUpdateLastApplied.remove(messageId);
        ChatHudLine lineToUpdate = activeTranslationLines.remove(messageId);
        translationGenerations.remove(messageId, requestGeneration);
        if (lineToUpdate == null) {
            logChatLineMapping(messageId, "final_update_missing_active_line", -1, finalContent);
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            if (!TranslationFeatureGate.isEnabled()) {
                return;
            }
            ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<ChatHudLine> messages = chatHudAccessor.getMessages();
            int scrolledLines = chatHudAccessor.getScrolledLines();

            int lineIndex = messages.indexOf(lineToUpdate);

            if (lineIndex != -1) {
                Text finalLineContent = buildTranslatedMessageWithToggle(messageId, finalContent, MessageUtils.getTrackedMessage(messageId));
                ChatHudLine newLine = new ChatHudLine(lineToUpdate.creationTick(), finalLineContent, lineToUpdate.signature(), lineToUpdate.indicator());
                messages.set(lineIndex, newLine);
                chatHudAccessor.invokeRefresh();
                chatHudAccessor.setScrolledLines(scrolledLines);
                MessageUtils.setTranslatedMessage(messageId, finalLineContent);
                logChatLineMapping(messageId, "final_update", lineIndex, finalLineContent);
            } else {
                logChatLineMapping(messageId, "final_update_line_missing", -1, finalContent);
            }
        });
    }

    private static void showTemporaryRouteError(
            UUID messageId,
            ChatHudAccessor chatHudAccessor,
            List<ChatHudLine> messages,
            int lineIndex,
            ChatHudLine originalLine
    ) {
        int scrolledLines = chatHudAccessor.getScrolledLines();
        Text errorText = Text.translatable(NO_ROUTED_MODEL_ERROR_KEY).formatted(Formatting.RED);
        ChatHudLine errorLine = new ChatHudLine(originalLine.creationTick(), errorText, originalLine.signature(), originalLine.indicator());
        messages.set(lineIndex, errorLine);
        chatHudAccessor.invokeRefresh();
        chatHudAccessor.setScrolledLines(scrolledLines);

        CompletableFuture.delayedExecutor(ROUTE_ERROR_DISPLAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> restoreLineAfterTemporaryError(messageId, errorLine, originalLine));
        });
    }

    private static void restoreLineAfterTemporaryError(UUID messageId, ChatHudLine errorLine, ChatHudLine originalLine) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }

        ChatHud chatHud = client.inGameHud.getChatHud();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<ChatHudLine> messages = chatHudAccessor.getMessages();
        int lineIndex = messages.indexOf(errorLine);
        if (lineIndex != -1) {
            int scrolledLines = chatHudAccessor.getScrolledLines();
            messages.set(lineIndex, originalLine);
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
        }

        lineLocateRetryCounts.remove(messageId);
    }

    private static LineSearchResult findTargetLine(List<ChatHudLine> messages, Text originalMessage) {
        int lineIndex = findTargetLineIndex(messages, originalMessage);
        return lineIndex == -1 ? null : new LineSearchResult(lineIndex, messages.get(lineIndex));
    }

    static int findTargetLineIndex(List<ChatHudLine> messages, Text originalMessage) {
        if (messages == null || originalMessage == null) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatHudLine line = messages.get(i);
            if (matchesTargetTextReference(line.content(), originalMessage)) {
                return i;
            }
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatHudLine line = messages.get(i);
            if (matchesTargetTextByContent(line.content(), originalMessage)) {
                return i;
            }
        }
        return -1;
    }

    private static LineSearchResult findTargetLine(List<ChatHudLine> messages, UUID messageId, String action) {
        int lineIndex = findTargetLineIndex(messages, messageId, action);
        return lineIndex == -1 ? null : new LineSearchResult(lineIndex, messages.get(lineIndex));
    }

    static int findTargetLineIndex(List<ChatHudLine> messages, UUID messageId, String action) {
        if (messages == null || messageId == null || action == null) {
            return -1;
        }
        String commandPrefix = "/translate_allinone translatechatline " + messageId + " " + action;
        for (int i = 0; i < messages.size(); i++) {
            if (containsToggleCommandDeep(messages.get(i).content(), commandPrefix)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesTargetTextReference(Text lineContent, Text originalMessage) {
        if (lineContent.equals(originalMessage)) {
            return true;
        }
        for (Text sibling : lineContent.getSiblings()) {
            if (sibling.equals(originalMessage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTargetTextByContent(Text lineContent, Text originalMessage) {
        String original = originalMessage.getString();
        if (lineContent.getString().equals(original)) {
            return true;
        }
        for (Text sibling : lineContent.getSiblings()) {
            if (sibling.getString().equals(original)) {
                return true;
            }
        }
        return false;
    }
    private static boolean isLineContentStillPresent(List<ChatHudLine> messages, Text lineToLocate) {
        if (messages == null || lineToLocate == null) {
            return false;
        }
        String target = lineToLocate.getString();
        if (target == null || target.isEmpty()) {
            return false;
        }
        for (ChatHudLine line : messages) {
            Text content = line.content();
            if (content == null) {
                continue;
            }
            if (content.getString().contains(target)) {
                return true;
            }
            if (!content.getSiblings().isEmpty()
                    && content.getSiblings().get(0).getString().contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean scheduleLineLocateRetry(UUID messageId, Text originalMessage, Text lineToLocate, boolean forceRefresh) {
        if (!TranslationFeatureGate.isEnabled()) {
            return false;
        }
        int attempt = lineLocateRetryCounts.merge(messageId, 1, Integer::sum);
        if (attempt > MAX_LINE_LOCATE_RETRIES) {
            return false;
        }
        logLocateRetry(messageId, attempt, originalMessage);

        CompletableFuture.delayedExecutor(LINE_LOCATE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> translate(messageId, originalMessage, lineToLocate, forceRefresh));
        });
        return true;
    }

    private record LineSearchResult(int lineIndex, ChatHudLine line) {
    }

    static PreparedChatTranslation prepareTranslationPayload(Text originalMessage) {
        Text sourceMessage = stripTrailingTranslationMarker(originalMessage);
        StylePreserver.ExtractionResult extraction = StylePreserver.extractAndMarkWithTags(sourceMessage);
        SpeakerHeaderExtractionResult headerResult = extractSpeakerHeader(extraction.markedText);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(headerResult.bodyMarkedText());
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = TemplateProcessor.extractDecorativeGlyphTags(templateResult.template());
        String normalizedTemplate = TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(glyphResult.template());
        IgnorableChatSegmentExtractionResult ignorableSegments = extractIgnorableChatSegments(normalizedTemplate);
        return new PreparedChatTranslation(
                ignorableSegments.template(),
                extraction.styleMap,
                templateResult.values(),
                glyphResult.values(),
                ignorableSegments.values(),
                headerResult.header()
        );
    }

    static SpeakerHeaderExtractionResult extractSpeakerHeader(String taggedText) {
        if (taggedText == null || taggedText.isEmpty()) {
            return new SpeakerHeaderExtractionResult("", taggedText == null ? "" : taggedText);
        }
        StyleTagStrippedView view = stripStyleTags(taggedText);
        Matcher matcher = SPEAKER_HEADER_PATTERN.matcher(view.plainText());
        if (!matcher.find()) {
            return new SpeakerHeaderExtractionResult("", taggedText);
        }
        int originalEnd = view.originalIndex(matcher.end());
        String header = taggedText.substring(0, originalEnd);
        String body = taggedText.substring(originalEnd);
        if (stripStyleTags(body).plainText().isBlank()) {
            return new SpeakerHeaderExtractionResult("", taggedText);
        }
        return new SpeakerHeaderExtractionResult(header, body);
    }

    private static StyleTagStrippedView stripStyleTags(String text) {
        StringBuilder plain = new StringBuilder(text.length());
        List<Integer> sourceIndexes = new ArrayList<>();
        Matcher matcher = STYLE_TAG_MARKER_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            for (int i = lastEnd; i < matcher.start(); i++) {
                plain.append(text.charAt(i));
                sourceIndexes.add(i);
            }
            lastEnd = matcher.end();
        }
        for (int i = lastEnd; i < text.length(); i++) {
            plain.append(text.charAt(i));
            sourceIndexes.add(i);
        }
        int[] indexes = new int[sourceIndexes.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = sourceIndexes.get(i);
        }
        return new StyleTagStrippedView(plain.toString(), indexes, text);
    }

    private record StyleTagStrippedView(String plainText, int[] sourceIndexes, String sourceText) {
        int originalIndex(int plainLength) {
            if (plainLength <= 0) {
                return 0;
            }
            int index = sourceIndexes[plainLength - 1] + 1;
            Matcher matcher = STYLE_TAG_MARKER_PATTERN.matcher(sourceText);
            matcher.region(index, sourceText.length());
            while (matcher.lookingAt()) {
                if (!matcher.group().startsWith("</")) {
                    break;
                }
                index = matcher.end();
                matcher.region(index, sourceText.length());
            }
            return index;
        }
    }

    private record SpeakerHeaderExtractionResult(String header, String bodyMarkedText) {
    }
    private static String buildChatOutputCacheKey(String targetLanguage, String textToTranslate) {
        if (textToTranslate == null || textToTranslate.isBlank()) {
            return null;
        }
        String normalizedLanguage = targetLanguage == null
                ? ""
                : targetLanguage.trim().toLowerCase(Locale.ROOT);
        return "target=" + normalizedLanguage + "\u001f" + textToTranslate;
    }

    private static String buildSkyblockNpcCacheKey(String targetLanguage, String textToTranslate) {
        return buildChatOutputCacheKey(targetLanguage, textToTranslate);
    }

    static String resolveCachedTranslation(LookupResult skyblockCacheLookup, LookupResult chatOutputCacheLookup) {
        if (skyblockCacheLookup != null && skyblockCacheLookup.status() == TranslationStatus.TRANSLATED) {
            return skyblockCacheLookup.translation();
        }
        if (chatOutputCacheLookup != null && chatOutputCacheLookup.status() == TranslationStatus.TRANSLATED) {
            return chatOutputCacheLookup.translation();
        }
        return null;
    }

    static String resolveCachedFailure(LookupResult skyblockCacheLookup, LookupResult chatOutputCacheLookup) {
        if (skyblockCacheLookup != null && skyblockCacheLookup.status() == TranslationStatus.ERROR) {
            return skyblockCacheLookup.errorMessage();
        }
        if (chatOutputCacheLookup != null && chatOutputCacheLookup.status() == TranslationStatus.ERROR) {
            return chatOutputCacheLookup.errorMessage();
        }
        return null;
    }

    private static void completeCachedTranslation(
            UUID messageId,
            long requestGeneration,
            String cachedTranslation,
            PreparedChatTranslation preparedTranslation
    ) {
        Text finalStyledText = rebuildTranslatedText(cachedTranslation, preparedTranslation);
        logReflowResult(
                messageId,
                false,
                cachedTranslation,
                cachedTranslation,
                finalStyledText,
                preparedTranslation.styleMap()
        );
        updateChatLineWithFinalText(messageId, requestGeneration, finalStyledText);
    }

    private static void completeCachedFailure(UUID messageId, long requestGeneration, String reason) {
        Text errorText = Text.translatable(
                TRANSLATION_ERROR_KEY,
                TranslationErrorTextSupport.localizeReason(reason)
        ).formatted(Formatting.RED);
        updateChatLineWithFinalText(messageId, requestGeneration, errorText);
    }

    private static void cacheChatOutputTranslation(String cacheKey, String translation) {
        if (!TranslationFeatureGate.isEnabled() || cacheKey == null || translation == null || translation.isBlank()) {
            return;
        }
        ChatOutputTranslationCache.getInstance().updateTranslations(Map.of(cacheKey, translation));
    }

    private static void cacheSkyblockNpcTranslation(String cacheKey, String translation) {
        if (!TranslationFeatureGate.isEnabled() || cacheKey == null || translation == null || translation.isBlank()) {
            return;
        }
        SkyblockNpcTranslationCache.getInstance().updateTranslations(Map.of(cacheKey, translation));
    }

    private static void cacheTranslation(String skyblockCacheKey, String chatOutputCacheKey, String translation) {
        if (skyblockCacheKey != null) {
            cacheSkyblockNpcTranslation(skyblockCacheKey, translation);
        } else {
            cacheChatOutputTranslation(chatOutputCacheKey, translation);
        }
    }

    private static void cacheTranslationFailure(String skyblockCacheKey, String chatOutputCacheKey, String errorMessage) {
        String resolvedMessage = errorMessage == null || errorMessage.isBlank()
                ? "Translation request failed"
                : errorMessage;
        if (skyblockCacheKey != null) {
            SkyblockNpcTranslationCache.getInstance().requeueFailed(Set.of(skyblockCacheKey), resolvedMessage);
        } else if (chatOutputCacheKey != null) {
            ChatOutputTranslationCache.getInstance().requeueFailed(Set.of(chatOutputCacheKey), resolvedMessage);
        }
    }

    public static void queuePendingAutoTranslation(UUID messageId, Text originalMessage) {
        if (messageId == null || originalMessage == null) {
            return;
        }
        pendingAutoTranslateMessages.put(messageId, originalMessage);
    }

    public static void flushPendingAutoTranslations() {
        if (pendingAutoTranslateMessages.isEmpty()) {
            return;
        }
        Map<UUID, Text> pending = Map.copyOf(pendingAutoTranslateMessages);
        pendingAutoTranslateMessages.clear();
        pending.forEach((messageId, originalMessage) -> translate(messageId, originalMessage));
    }

    public static void clearPendingAutoTranslations() {
        pendingAutoTranslateMessages.clear();
    }

    public static void cancelPendingTranslations() {
        inFlightTranslations.cancelAll();
        Map<UUID, ChatHudLine> pendingLines = Map.copyOf(activeTranslationLines);
        lineLocateRetryCounts.clear();
        translationGenerations.clear();
        activeTranslationLines.clear();
        pendingAnimationSources.clear();
        pendingAutoTranslateMessages.clear();
        streamingUpdateLastApplied.clear();
        if (pendingLines.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> restorePendingOriginalLines(pendingLines));
    }

    public static synchronized void clearTranslationQueue() {
        ExecutorService executor = translationExecutor;
        translationExecutor = null;
        currentConcurrentRequests = -1;
        if (executor != null) {
            executor.shutdownNow();
        }
        cancelPendingTranslations();
        ChatOutputTranslationCache.getInstance().clearTranslationQueue();
        SkyblockNpcTranslationCache.getInstance().clearTranslationQueue();
    }

    private static void restorePendingOriginalLines(Map<UUID, ChatHudLine> pendingLines) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }

        ChatHud chatHud = client.inGameHud.getChatHud();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<ChatHudLine> messages = chatHudAccessor.getMessages();
        int scrolledLines = chatHudAccessor.getScrolledLines();
        boolean restored = false;
        for (Map.Entry<UUID, ChatHudLine> entry : pendingLines.entrySet()) {
            MessageUtils.TrackedChatMessage trackedMessage = MessageUtils.getTrackedChatMessage(entry.getKey());
            if (trackedMessage == null || trackedMessage.originalMessage() == null) {
                continue;
            }

            ChatHudLine pendingLine = entry.getValue();
            int lineIndex = messages.indexOf(pendingLine);
            if (lineIndex == -1) {
                continue;
            }

            ChatHudLine restoredLine = new ChatHudLine(
                    pendingLine.creationTick(),
                    trackedMessage.originalMessage().copy(),
                    pendingLine.signature(),
                    pendingLine.indicator()
            );
            messages.set(lineIndex, restoredLine);
            MessageUtils.markShowingOriginal(entry.getKey());
            restored = true;
        }
        if (restored) {
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
        }
    }

    private static boolean isTranslationActive(UUID messageId) {
        return TranslationFeatureGate.isEnabled() && translationGenerations.containsKey(messageId);
    }

    private static boolean isTranslationActive(UUID messageId, long requestGeneration) {
        Long activeGeneration = translationGenerations.get(messageId);
        return TranslationFeatureGate.isEnabled()
                && activeGeneration != null
                && activeGeneration == requestGeneration;
    }

    private static Text stripTrailingTranslationMarker(Text message) {
        if (message == null || !message.getString().endsWith(" [T]")) {
            return message;
        }

        int retainedLength = message.getString().length() - 4;
        MutableText retained = Text.empty();
        int[] remaining = {retainedLength};
        message.visit((style, string) -> {
            if (remaining[0] <= 0) {
                return Optional.of(Boolean.TRUE);
            }
            int length = Math.min(remaining[0], string.length());
            if (length > 0) {
                retained.append(Text.literal(string.substring(0, length)).setStyle(style));
                remaining[0] -= length;
            }
            return remaining[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return retained;
    }

    static Text rebuildTranslatedText(String translatedText, PreparedChatTranslation preparedTranslation) {
        if (preparedTranslation == null) {
            return Text.literal(translatedText == null ? "" : translatedText);
        }

        String reassembled = TemplateProcessor.reassemble(
                preparedTranslation.header() + (translatedText == null ? "" : translatedText),
                preparedTranslation.templateValues()
        );
        reassembled = TemplateProcessor.reassembleDecorativeGlyphs(
                reassembled,
                preparedTranslation.decorativeGlyphValues(),
                true
        );
        reassembled = reassembleIgnorableChatSegments(reassembled, preparedTranslation.ignorableSegments());
        return StylePreserver.reapplyStylesFromTags(reassembled, preparedTranslation.styleMap(), true);
    }

    private static IgnorableChatSegmentExtractionResult extractIgnorableChatSegments(String text) {
        if (text == null || text.isEmpty()) {
            return new IgnorableChatSegmentExtractionResult(text == null ? "" : text, List.of());
        }

        List<String> values = new ArrayList<>();
        Matcher matcher = STYLE_TAG_PATTERN.matcher(text);
        StringBuilder template = new StringBuilder(text.length());
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                template.append(text, lastEnd, matcher.start());
            }

            String taggedSegment = matcher.group();
            String content = matcher.group(2);
            if (shouldExtractIgnorableChatSegment(content)) {
                values.add(taggedSegment);
                template.append("{c").append(values.size()).append("}");
            } else {
                template.append(taggedSegment);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            template.append(text, lastEnd, text.length());
        }

        return new IgnorableChatSegmentExtractionResult(template.toString(), values);
    }

    private static String reassembleIgnorableChatSegments(String text, List<String> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }

        Matcher matcher = CHAT_IGNORABLE_PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder reassembled = new StringBuilder(text.length());
        int lastEnd = 0;

        while (matcher.find()) {
            reassembled.append(text, lastEnd, matcher.start());
            int placeholderIndex = Integer.parseInt(matcher.group(1)) - 1;
            String replacement = placeholderIndex >= 0 && placeholderIndex < values.size()
                    ? values.get(placeholderIndex)
                    : matcher.group();
            reassembled.append(replacement);
            lastEnd = matcher.end();
        }

        reassembled.append(text, lastEnd, text.length());
        return reassembled.toString();
    }

    private static boolean shouldExtractIgnorableChatSegment(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        boolean sawIgnorable = false;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (!isIgnorableChatCodePoint(codePoint) && !isDecorativeGlyphCodePoint(codePoint)) {
                return false;
            }
            sawIgnorable = true;
        }
        return sawIgnorable;
    }

    private static boolean isIgnorableChatCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.FORMAT
                || type == Character.CONTROL
                || codePoint == 0xFFFC;
    }

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        if (unicodeType == Character.PRIVATE_USE || unicodeType == Character.UNASSIGNED) {
            return true;
        }

        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    record PreparedChatTranslation(
            String textToTranslate,
            Map<Integer, Style> styleMap,
            List<String> templateValues,
            List<String> decorativeGlyphValues,
            List<String> ignorableSegments,
            String header
    ) {
    }

    private record IgnorableChatSegmentExtractionResult(String template, List<String> values) {
    }

    private static Text appendToggleButton(UUID messageId, Text messageContent, String action, String hoverTranslationKey) {
        MutableText root = Text.empty().append(messageContent.copy());
        MutableText toggleButton = Text.literal(" [T]");
        Style toggleStyle = Style.EMPTY
                .withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/translate_allinone translatechatline " + messageId + " " + action
                ))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.translatable(hoverTranslationKey)
                ));
        toggleButton.setStyle(toggleStyle);
        root.append(toggleButton);
        return root;
    }
    private static Text applyOriginalDisplayMode(Text translatedMessage, Text originalMessage) {
        if (translatedMessage == null) {
            return null;
        }
        if (originalMessage == null || originalMessage.getString().isBlank()) {
            return translatedMessage;
        }
        String mode = resolveOriginalDisplayMode();
        if (ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_OFF.equals(mode)
                || isSamePlainText(translatedMessage, originalMessage)) {
            return translatedMessage;
        }
        boolean useSubtitle = ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_SUBTITLE.equals(mode)
                && originalMessage.getString().length() <= Translate_AllinOne.getConfig().chatTranslate.output.original_subtitle_max_length;
        if (useSubtitle) {
            return buildSubtitleContent(translatedMessage, originalMessage);
        }
        return attachOriginalHover(translatedMessage, originalMessage);
    }

    private static String resolveOriginalDisplayMode() {
        ChatTranslateConfig.ChatOutputTranslateConfig output = Translate_AllinOne.getConfig().chatTranslate.output;
        if (output == null || output.original_display_mode == null || output.original_display_mode.isBlank()) {
            return ChatTranslateConfig.ChatOutputTranslateConfig.DEFAULT_ORIGINAL_DISPLAY_MODE;
        }
        return output.original_display_mode;
    }

    private static Text buildSubtitleContent(Text translatedMessage, Text originalMessage) {
        MutableText root = Text.empty();
        root.append(translatedMessage.copy());
        root.append(Text.literal("\n"));
        root.append(muteForSubtitle(originalMessage));
        return root;
    }

    private static Text muteForSubtitle(Text original) {
        MutableText muted = Text.empty();
        original.visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Style base = style == null ? Style.EMPTY : style;
            StylePreserver.fromLegacyText(text).visit((resolvedStyle, resolvedText) -> {
                if (!resolvedText.isEmpty()) {
                    muted.append(Text.literal(resolvedText).setStyle(
                            resolvedStyle.withColor(Formatting.GRAY).withItalic(true)
                    ));
                }
                return Optional.empty();
            }, base);
            return Optional.empty();
        }, Style.EMPTY);
        return muted;
    }

    private static Text attachOriginalHover(Text translatedMessage, Text originalMessage) {
        MutableText copy = translatedMessage.copy();
        copy.setStyle(copy.getStyle().withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, originalMessage)));
        return copy;
    }

    private static boolean isSamePlainText(Text a, Text b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.getString().trim();
        String right = b.getString().trim();
        return !left.isEmpty() && left.equals(right);
    }

    public static boolean handleToggleCommandWithMissingTracking(UUID messageId, String action) {
        if (messageId == null || action == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null || client.inGameHud.getChatHud() == null) {
            return false;
        }
        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) client.inGameHud.getChatHud();
        List<ChatHudLine> messages = chatHudAccessor.getMessages();
        String commandPrefix = "/translate_allinone translatechatline " + messageId + " " + action;
        for (ChatHudLine line : messages) {
            Text content = line.content();
            if (content == null || !containsToggleCommand(content, commandPrefix)) {
                continue;
            }
            if (CHAT_TRANSLATE_ACTION.equalsIgnoreCase(action)) {
                Text original = extractToggleMessageContent(content);
                if (original != null) {
                    MessageUtils.putTrackedMessage(messageId, original);
                    translate(messageId, original);
                    return true;
                }
            } else if (CHAT_RESTORE_ACTION.equalsIgnoreCase(action)) {
                restoreShownLineAsOriginal(messageId, line, chatHudAccessor, messages);
            }
            return true;
        }
        return false;
    }

    private static void restoreShownLineAsOriginal(UUID messageId, ChatHudLine line, ChatHudAccessor chatHudAccessor, List<ChatHudLine> messages) {
        Text subtitleOriginal = extractSubtitleOriginal(line.content());
        if (subtitleOriginal == null) {
            return;
        }
        MessageUtils.putTrackedMessage(messageId, subtitleOriginal);
        MessageUtils.markShowingOriginal(messageId);
        int scrolledLines = chatHudAccessor.getScrolledLines();
        int lineIndex = messages.indexOf(line);
        if (lineIndex == -1) {
            return;
        }
        ChatHudLine restoredLine = new ChatHudLine(
                line.creationTick(),
                buildOriginalMessageWithToggle(messageId, subtitleOriginal),
                line.signature(),
                line.indicator()
        );
        messages.set(lineIndex, restoredLine);
        chatHudAccessor.invokeRefresh();
        chatHudAccessor.setScrolledLines(scrolledLines);
    }

    private static boolean containsToggleCommand(Text content, String commandPrefix) {
        if (hasToggleCommand(content.getStyle(), commandPrefix)) {
            return true;
        }
        for (Text sibling : content.getSiblings()) {
            if (hasToggleCommand(sibling.getStyle(), commandPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsToggleCommandDeep(Text content, String commandPrefix) {
        if (content == null) {
            return false;
        }
        return content.visit(
                (style, text) -> hasToggleCommand(style, commandPrefix)
                        ? Optional.of(Boolean.TRUE)
                        : Optional.empty(),
                Style.EMPTY
        ).orElse(false);
    }

    private static boolean hasToggleCommand(Style style, String commandPrefix) {
        if (style == null) {
            return false;
        }
        ClickEvent clickEvent = style.getClickEvent();
        return clickEvent != null
                && clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND
                && clickEvent.getValue() != null
                && clickEvent.getValue().startsWith(commandPrefix);
    }

    private static Text extractToggleMessageContent(Text root) {
        if (root == null || root.getSiblings().isEmpty()) {
            return null;
        }
        Text subtitleOriginal = extractSubtitleOriginal(root);
        if (subtitleOriginal != null) {
            return subtitleOriginal;
        }
        Text first = root.getSiblings().get(0);
        return first == null ? null : first.copy();
    }

    private static Text extractSubtitleOriginal(Text root) {
        if (root == null || root.getSiblings().isEmpty()) {
            return null;
        }
        Text first = root.getSiblings().get(0);
        List<Text> parts = first.getSiblings();
                if (parts.size() < 3 || !"\n".equals(parts.get(1).getString())) {
            return null;
        }
        Text subtitle = parts.get(2);
        return subtitle == null || subtitle.getString().isEmpty() ? null : subtitle.copy();
    }

    @NotNull
    private static List<OpenAIRequest.Message> getMessages(ApiProviderProfile providerProfile, String targetLanguage, String textToTranslate) {
        String basePrompt = "You are a deterministic translation engine.\n"
                + "Target language: " + targetLanguage + ".\n"
                + "\n"
                + "Rules (highest priority first):\n"
                + "1) Output only the final translated text. No explanation, markdown, or quotes.\n"
                + "2) Preserve style tags exactly: <s0>...</s0>, <s1>...</s1>, ... Keep the same tag ids, counts, and order.\n"
                + "3) Preserve tokens exactly: § color/style codes, placeholders (%s %d %f {d1}), URLs, numbers, <...>, {...}, \\n, \\t.\n"
                + "4) If a term is uncertain, keep only that term unchanged and still translate surrounding text.\n"
                + "5) If any rule cannot be guaranteed, return the original input unchanged.";
        String resolved = PromptMessageBuilder.applyPromptOverride("chat_output", basePrompt, providerProfile.system_prompt_overrides, targetLanguage);
        String systemPrompt = PromptMessageBuilder.appendSystemPromptSuffix(
                resolved,
                providerProfile.activeSystemPromptSuffix()
        );
        return PromptMessageBuilder.buildMessages(
                systemPrompt,
                textToTranslate,
                providerProfile.activeSupportsSystemMessage(),
                providerProfile.model_id,
                providerProfile.activeInjectSystemPromptIntoUserMessage()
        );
    }

    private static String buildRequestContext(
            ApiProviderProfile profile,
            String targetLanguage,
            String markedText,
            List<OpenAIRequest.Message> messages,
            boolean streaming,
            UUID messageId
    ) {
        String providerId = profile == null ? "" : profile.id;
        String modelId = profile == null ? "" : profile.model_id;
        int messageCount = messages == null ? 0 : messages.size();
        String roles = messages == null
                ? "[]"
                : messages.stream().map(message -> message == null ? "null" : String.valueOf(message.role)).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String sample = TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(markedText), 160);
        return "route=chat_output"
                + ", messageId=" + messageId
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + (targetLanguage == null ? "" : targetLanguage)
                + ", streaming=" + streaming
                + ", messages=" + messageCount
                + ", roles=" + roles
                + ", sample=\"" + sample + "\"";
    }

    private static void logLlmSubmission(
            UUID messageId,
            ApiProviderProfile providerProfile,
            ChatTranslateConfig.ChatOutputTranslateConfig chatOutputConfig,
            Text originalMessage,
            String markedText,
            Map<Integer, Style> styleMap,
            List<OpenAIRequest.Message> apiMessages,
            String requestContext
    ) {
        if (!shouldLogLlmSubmission()) {
            return;
        }

        LOGGER.info(
                "[ChatOutputDev:llm_submit] messageId={} provider={} model={} target={} streaming={} originalText=\"{}\" markedText=\"{}\" styleMap={} apiMessages={} context={}",
                messageId,
                providerProfile == null ? "" : providerProfile.id,
                providerProfile == null ? "" : providerProfile.model_id,
                chatOutputConfig == null || chatOutputConfig.target_language == null ? "" : chatOutputConfig.target_language,
                chatOutputConfig != null && chatOutputConfig.streaming_response,
                escapeForLog(originalMessage == null ? "" : originalMessage.getString()),
                escapeForLog(markedText),
                describeStyleMap(styleMap),
                describeApiMessages(apiMessages),
                requestContext == null ? "" : requestContext
        );
    }

    private static void logReflowResult(
            UUID messageId,
            boolean streaming,
            String rawModelOutput,
            String visibleTranslation,
            Text finalStyledText,
            Map<Integer, Style> styleMap
    ) {
        if (!shouldLogReflowMapping()) {
            return;
        }

        LOGGER.info(
                "[ChatOutputDev:reflow] messageId={} streaming={} rawModelOutput=\"{}\" visibleTranslation=\"{}\" finalText=\"{}\" styleMap={} finalSegments={}",
                messageId,
                streaming,
                escapeForLog(rawModelOutput),
                escapeForLog(visibleTranslation),
                escapeForLog(finalStyledText == null ? "" : finalStyledText.getString()),
                describeStyleMap(styleMap),
                describeTextSegments(finalStyledText)
        );
    }

    private static void logChatLineMapping(UUID messageId, String action, int lineIndex, Text content) {
        if (!shouldLogReflowMapping()) {
            return;
        }

        LOGGER.info(
                "[ChatOutputDev:chat_map] messageId={} action={} lineIndex={} text=\"{}\" segments={}",
                messageId,
                action,
                lineIndex,
                escapeForLog(content == null ? "" : content.getString()),
                describeTextSegments(content)
        );
    }

    private static void logLocateRetry(UUID messageId, int attempt, Text originalMessage) {
        if (!shouldLogReflowMapping()) {
            return;
        }

        LOGGER.info(
                "[ChatOutputDev:chat_map] messageId={} action=retry_locate attempt={} maxRetries={} originalText=\"{}\"",
                messageId,
                attempt,
                MAX_LINE_LOCATE_RETRIES,
                escapeForLog(originalMessage == null ? "" : originalMessage.getString())
        );
    }

    private static boolean shouldLogInterceptedMessage() {
        ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig debugConfig = getDebugConfig();
        return debugConfig != null && debugConfig.enabled && debugConfig.log_intercepted_message;
    }

    private static boolean shouldLogLlmSubmission() {
        ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig debugConfig = getDebugConfig();
        return debugConfig != null && debugConfig.enabled && debugConfig.log_llm_submission;
    }

    private static boolean shouldLogReflowMapping() {
        ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig debugConfig = getDebugConfig();
        return debugConfig != null && debugConfig.enabled && debugConfig.log_reflow_mapping;
    }

    private static ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig getDebugConfig() {
        try {
            if (Translate_AllinOne.getConfig() == null
                    || Translate_AllinOne.getConfig().chatTranslate == null
                    || Translate_AllinOne.getConfig().chatTranslate.output == null) {
                return null;
            }
            return Translate_AllinOne.getConfig().chatTranslate.output.debug;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String describeApiMessages(List<OpenAIRequest.Message> apiMessages) {
        if (apiMessages == null || apiMessages.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>();
        for (int index = 0; index < apiMessages.size(); index++) {
            OpenAIRequest.Message message = apiMessages.get(index);
            String role = message == null || message.role == null ? "" : message.role;
            String content = message == null || message.content == null ? "" : message.content;
            parts.add("#" + index + "{role=" + role + ",content=\"" + escapeForLog(content) + "\"}");
        }
        return parts.toString();
    }

    private static String describeStyleMap(Map<Integer, Style> styleMap) {
        if (styleMap == null || styleMap.isEmpty()) {
            return "{}";
        }

        return styleMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + describeStyle(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String describeTextSegments(Text text) {
        if (text == null) {
            return "[]";
        }

        List<String> segments = new ArrayList<>();
        text.visit((style, string) -> {
            if (string != null && !string.isEmpty()) {
                segments.add("{text=\"" + escapeForLog(string) + "\",style=" + describeStyle(style) + "}");
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments.toString();
    }

    private static String describeStyle(Style style) {
        if (style == null || style.isEmpty()) {
            return "plain";
        }

        List<String> fields = new ArrayList<>();
        if (style.getColor() != null) {
            fields.add("color=" + formatRgb(style.getColor().getRgb()));
        }
        if (style.isBold()) {
            fields.add("bold");
        }
        if (style.isItalic()) {
            fields.add("italic");
        }
        if (style.isUnderlined()) {
            fields.add("underline");
        }
        if (style.isStrikethrough()) {
            fields.add("strikethrough");
        }
        if (style.isObfuscated()) {
            fields.add("obfuscated");
        }
        if (style.getFont() != null) {
            fields.add("font=" + style.getFont());
        }
        if (style.getClickEvent() != null) {
            fields.add("click=" + style.getClickEvent().getAction().asString());
        }
        if (style.getHoverEvent() != null) {
            fields.add("hover");
        }
        return fields.isEmpty() ? "plain" : String.join("|", fields);
    }

    private static String formatRgb(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static String escapeForLog(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
