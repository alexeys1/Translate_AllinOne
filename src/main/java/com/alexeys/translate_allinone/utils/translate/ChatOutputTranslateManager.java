package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.mixin.mixinChatHud.ChatHudAccessor;
import com.alexeys.translate_allinone.utils.TranslateStringUtils;
import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.MessageUtils;
import com.alexeys.translate_allinone.utils.TranslateExceptionUtils;
import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.ChatOutputTranslationCache;
import com.alexeys.translate_allinone.utils.cache.SkyblockNpcTranslationCache;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.llmapi.LLM;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import com.alexeys.translate_allinone.utils.text.TemplateProcessor;
import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class ChatOutputTranslateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatOutputTranslateManager.class);
    private static final String CHAT_TRANSLATE_ACTION = "translate";
    private static final String CHAT_RESTORE_ACTION = "restore";
    private static final Map<UUID, GuiMessage> activeTranslationLines = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> translationGenerations = new ConcurrentHashMap<>();
    private static final AtomicLong translationGeneration = new AtomicLong();
    private static final Map<UUID, Integer> lineLocateRetryCounts = new ConcurrentHashMap<>();
    private static final Map<UUID, Component> pendingAnimationSources = new ConcurrentHashMap<>();
    private static final Map<UUID, AnimationManager.AnimatedSegment[]> preparedAnimationCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Component> pendingAutoTranslateMessages = new ConcurrentHashMap<>();
    private static ExecutorService translationExecutor;
    private static int currentConcurrentRequests = -1;
    private static final int MAX_LINE_LOCATE_RETRIES = 4;
    private static final long LINE_LOCATE_RETRY_DELAY_MS = 40L;
    private static final int MAX_TRIMMED_LINES = 100;
    private static final long STREAMING_UPDATE_INTERVAL_MS = 100L;
    private static final Map<UUID, Long> streamingUpdateLastApplied = new ConcurrentHashMap<>();
    private static final long ROUTE_ERROR_DISPLAY_MS = 2_000L;
    private static final String TRANSLATION_ERROR_KEY = "text.translate_allinone.chat.output_translation_error";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL);
    private static final Pattern CHAT_IGNORABLE_PLACEHOLDER_PATTERN = Pattern.compile("\\{c(\\d+)}");
    private static final String SKYBLOCK_NPC_FORMATTED_UNIT = "(?:§[0-9a-fk-or]|[^§\\r\\n])";
    private static final String SKYBLOCK_NPC_NAME_TOKEN = "[\\p{L}\\p{N}_.'-]+";
    private static final Pattern SKYBLOCK_NPC_CHAT_PATTERN = Pattern.compile(
            "^(?:\\[CHAT\\] )?(?:§r)?(?:§e)?\\[NPC\\] (?:§r)?(?:§[0-9a-f])?"
                    + SKYBLOCK_NPC_NAME_TOKEN + "(?: " + SKYBLOCK_NPC_NAME_TOKEN + ")*"
                    + "(?:§r)?(?:§f)?: (?:(?:§r)?§f)?"
                    + "(?<body>" + SKYBLOCK_NPC_FORMATTED_UNIT + "+?)(?:§[0-9a-fk-or])*"
                    + "(?:\\s+\\[T\\])?$"
    );
    private static final Pattern STYLE_TAG_MARKER_PATTERN = Pattern.compile("</?s\\d+>");
    private static final String SPEAKER_NAME_TOKEN = "[\\p{L}\\p{N}_.'’\\-]+";
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

    public static Component buildOriginalMessageWithToggle(UUID messageId, Component originalMessage) {
        return appendToggleButton(messageId, originalMessage, CHAT_TRANSLATE_ACTION, "text.translate_allinone.translate_button_hover");
    }

    public static Component buildTranslatedMessageWithToggle(UUID messageId, Component translatedMessage) {
        return buildTranslatedMessageWithToggle(messageId, translatedMessage, null);
    }

    public static Component buildTranslatedMessageWithToggle(UUID messageId, Component translatedMessage, Component originalMessage) {
        return appendToggleButton(
                messageId,
                applyOriginalDisplayMode(translatedMessage, originalMessage),
                CHAT_RESTORE_ACTION,
                "text.translate_allinone.restore_button_hover"
        );
    }

    public static void logInterceptedMessage(UUID messageId, Component originalMessage, String plainText, boolean autoTranslate) {
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

    public static boolean isSkyblockNpcMessage(Component message) {
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

    public static void translate(UUID messageId, Component originalMessage) {
        translate(messageId, originalMessage, originalMessage, false);
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

    private static void translate(UUID messageId, Component originalMessage, Component lineToLocate, boolean forceRefresh) {
        if (!TranslationFeatureGate.isEnabled() || messageId == null || originalMessage == null || lineToLocate == null) {
            return;
        }
        if (activeTranslationLines.containsKey(messageId)) {
            lineLocateRetryCounts.remove(messageId);
            return; // Already being translated
        }

        Minecraft client = Minecraft.getInstance();
        ChatComponent chatHud = client.gui.hud.getChat();
        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<GuiMessage> messages = chatHudAccessor.getMessages();
        LineSearchResult searchResult = findTargetLine(messages, lineToLocate);
        GuiMessage targetLine;
        int lineIndex = -1;
        if (searchResult != null) {
            targetLine = searchResult.line();
            lineIndex = searchResult.lineIndex();
        } else {
            targetLine = findTrimmedTargetByCommandPrefix(chatHudAccessor, messageId, CHAT_TRANSLATE_ACTION);
            if (targetLine == null) {
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
            lineIndex = messages.indexOf(targetLine);
        }
        lineLocateRetryCounts.remove(messageId);
        logChatLineMapping(messageId, "locate_original", lineIndex, targetLine.content());

        updateExecutorServiceIfNeeded();

        AnimationManager.AnimatedSegment[] preparedSegments = preparedAnimationCache.computeIfAbsent(messageId, id -> AnimationManager.prepareAnimatedSegments(originalMessage));
        Component placeholderText = AnimationManager.getAnimatedStyledText(preparedSegments);
        GuiMessage newLine = new GuiMessage(targetLine.addedTime(), placeholderText, targetLine.signature(), targetLine.source(), targetLine.tag());
        if (lineIndex != -1) {
            messages.set(lineIndex, newLine);
        }
        activeTranslationLines.put(messageId, newLine);
        pendingAnimationSources.put(messageId, originalMessage);
        long requestGeneration = translationGeneration.incrementAndGet();
        translationGenerations.put(messageId, requestGeneration);
        replaceTrimmedLines(chatHudAccessor, targetLine, newLine);

        final long finalRequestGeneration = requestGeneration;
        final boolean finalForceRefresh = forceRefresh;
        final int finalLineIndex = lineIndex;
        final GuiMessage finalTargetLine = targetLine;
        translationExecutor.submit(() -> {
            String requestContext = "route=chat_output,messageId=" + messageId;
            long watchdogRequestId = 0L;
            try {
                if (!isTranslationActive(messageId, finalRequestGeneration)) {
                    return;
                }
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
                if (finalForceRefresh) {
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
                ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                        Translate_AllinOne.getConfig(),
                        ProviderRouteResolver.Route.CHAT_OUTPUT
                );

                if (skyblockCacheLookup != null
                        && skyblockCacheLookup.status() == TranslationStatus.TRANSLATED) {
                    String cachedTranslation = skyblockCacheLookup.translation();
                    Component finalStyledText = rebuildTranslatedText(cachedTranslation, preparedTranslation);
                    logReflowResult(
                            messageId,
                            false,
                            cachedTranslation,
                            cachedTranslation,
                            finalStyledText,
                            preparedTranslation.styleMap()
                    );
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                    return;
                }

                if (chatOutputCacheLookup != null
                        && chatOutputCacheLookup.status() == TranslationStatus.TRANSLATED) {
                    String cachedTranslation = chatOutputCacheLookup.translation();
                    Component finalStyledText = rebuildTranslatedText(cachedTranslation, preparedTranslation);
                    logReflowResult(
                            messageId,
                            false,
                            cachedTranslation,
                            cachedTranslation,
                            finalStyledText,
                            preparedTranslation.styleMap()
                    );
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                    return;
                }

                if (providerProfile == null) {
                    LOGGER.warn("No routed model selected for chat output translation; showing temporary error for messageId={}", messageId);
                    showTemporaryRouteError(messageId, chatHudAccessor, messages, finalLineIndex, finalTargetLine);
                    return;
                }

                ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
                LLM llm = new LLM(settings);

                String textToTranslate = preparedTranslation.textToTranslate();
                Map<Integer, Style> styleMap = preparedTranslation.styleMap();

                List<OpenAIRequest.Message> apiMessages = getMessages(providerProfile, chatOutputConfig.target_language, textToTranslate);
                requestContext = buildRequestContext(providerProfile, chatOutputConfig.target_language, textToTranslate, apiMessages, chatOutputConfig.streaming_response, messageId);
                logLlmSubmission(messageId, providerProfile, chatOutputConfig, originalMessage, textToTranslate, styleMap, apiMessages, requestContext);

                if (shouldLogReflowMapping()) {
                    LOGGER.info("Starting translation for message ID: {}. Marked text: {}", messageId, textToTranslate);
                }
                watchdogRequestId = TranslationQueueWatchdog.requestStarted(
                        "chat_output",
                        List.of(messageId.toString())
                );

                if (chatOutputConfig.streaming_response) {
                    final StringBuilder rawResponseBuffer = new StringBuilder();
                    final StringBuilder fullResponseBuffer = new StringBuilder();
                    final StringBuilder visibleContentBuffer = new StringBuilder();
                    final AtomicBoolean inThinkTag = new AtomicBoolean(false);

                    llm.getStreamingCompletion(apiMessages, requestContext).forEach(chunk -> {
                        fullResponseBuffer.append(chunk);
                        rawResponseBuffer.append(chunk);

                        while (true) {
                            if (inThinkTag.get()) {
                                int endTagIndex = rawResponseBuffer.indexOf("</think>");
                                if (endTagIndex != -1) {
                                    inThinkTag.set(false);
                                    rawResponseBuffer.delete(0, endTagIndex + "</think>".length());
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    continue;
                                } else {
                                    int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                    if (startTagIndex != -1) {
                                        String thinkContent = rawResponseBuffer.substring(startTagIndex + "<think>".length());
                                        scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Component.literal("Thinking: ").append(thinkContent).withStyle(ChatFormatting.GRAY));
                                    }
                                    break;
                                }
                            } else {
                                int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                if (startTagIndex != -1) {
                                    String translationPart = rawResponseBuffer.substring(0, startTagIndex);
                                    visibleContentBuffer.append(translationPart);
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));

                                    rawResponseBuffer.delete(0, startTagIndex);
                                    inThinkTag.set(true);
                                    continue;
                                } else {
                                    visibleContentBuffer.append(rawResponseBuffer.toString());
                                    rawResponseBuffer.setLength(0);
                                    scheduleInProgressChatLineUpdate(messageId, finalRequestGeneration, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    break;
                                }
                            }
                        }
                    });

                    if (!isTranslationActive(messageId, finalRequestGeneration)) {
                        TranslationQueueWatchdog.requestSuperseded(watchdogRequestId);
                        watchdogRequestId = 0L;
                        return;
                    }
                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                    watchdogRequestId = 0L;
                    if (!isTranslationActive(messageId, finalRequestGeneration)) {
                        return;
                    }

                    Component finalStyledText = rebuildTranslatedText(visibleContentBuffer.toString().stripLeading(), preparedTranslation);
                    String finalTranslation = visibleContentBuffer.toString().stripLeading();
                    logReflowResult(
                            messageId,
                            true,
                            fullResponseBuffer.toString(),
                            visibleContentBuffer.toString().stripLeading(),
                            finalStyledText,
                            styleMap
                    );
                    if (skyblockCacheKey != null) {
                        cacheSkyblockNpcTranslation(skyblockCacheKey, finalTranslation);
                    } else {
                        cacheChatOutputTranslation(chatOutputCacheKey, finalTranslation);
                    }
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                } else {
                    String result = llm.getCompletion(apiMessages, requestContext).join();
                    if (!isTranslationActive(messageId, finalRequestGeneration)) {
                        TranslationQueueWatchdog.requestSuperseded(watchdogRequestId);
                        watchdogRequestId = 0L;
                        return;
                    }
                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                    watchdogRequestId = 0L;
                    if (!isTranslationActive(messageId, finalRequestGeneration)) {
                        return;
                    }
                    if (shouldLogReflowMapping()) {
                        LOGGER.info("Finished translation for message ID: {}. Result: {}", messageId, result);
                    }
                    final String finalTranslation = result.stripLeading();
                    Component finalStyledText = rebuildTranslatedText(finalTranslation, preparedTranslation);
                    logReflowResult(messageId, false, result, finalTranslation, finalStyledText, styleMap);
                    if (skyblockCacheKey != null) {
                        cacheSkyblockNpcTranslation(skyblockCacheKey, finalTranslation);
                    } else {
                        cacheChatOutputTranslation(chatOutputCacheKey, finalTranslation);
                    }
                    updateChatLineWithFinalText(messageId, finalRequestGeneration, finalStyledText);
                }
            } catch (Exception e) {
                if (watchdogRequestId != 0L) {
                    TranslationQueueWatchdog.requestFailed(
                            watchdogRequestId,
                            TranslateExceptionUtils.isInternalPostprocessError(e)
                    );
                }
                LOGGER.error("[Translate-Thread] Exception for message ID: {}. context={}", messageId, requestContext, e);
                Component errorText = Component.translatable(TRANSLATION_ERROR_KEY, TranslationErrorTextSupport.localizeReason(e.getMessage())).withStyle(ChatFormatting.RED);
                updateChatLineWithFinalText(messageId, finalRequestGeneration, errorText);
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

        Component originalMessage = trackedMessage.originalMessage();
        Component translatedMessage = trackedMessage.translatedMessage();
        if (originalMessage == null || translatedMessage == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            ChatComponent chatHud = client.gui == null ? null : client.gui.hud.getChat();
            if (chatHud == null) {
                return;
            }

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<GuiMessage> messages = chatHudAccessor.getMessages();
            LineSearchResult searchResult = findTargetLine(messages, translatedMessage);
            GuiMessage targetLine;
            if (searchResult != null) {
                targetLine = searchResult.line();
            } else {
                targetLine = findTrimmedTargetByCommandPrefix(chatHudAccessor, messageId, CHAT_RESTORE_ACTION);
                if (targetLine == null) {
                    return;
                }
            }
            int lineIndex = messages.indexOf(targetLine);

            Component restoredContent = buildOriginalMessageWithToggle(messageId, originalMessage);
            GuiMessage restoredLine = new GuiMessage(targetLine.addedTime(), restoredContent, targetLine.signature(), targetLine.source(), targetLine.tag());
            if (lineIndex != -1) {
                messages.set(lineIndex, restoredLine);
            }
            replaceTrimmedLines(chatHudAccessor, targetLine, restoredLine);
            MessageUtils.markShowingOriginal(messageId);
        });
    }

    private static void scheduleInProgressChatLineUpdate(UUID messageId, long requestGeneration, Component newContent) {
        long now = System.currentTimeMillis();
        Long lastApplied = streamingUpdateLastApplied.get(messageId);
        if (lastApplied != null && now - lastApplied < STREAMING_UPDATE_INTERVAL_MS) {
            return;
        }
        streamingUpdateLastApplied.put(messageId, now);
        updateInProgressChatLine(messageId, requestGeneration, newContent);
    }

    private static void updateInProgressChatLine(UUID messageId, long requestGeneration, Component newContent) {
        if (!isTranslationActive(messageId, requestGeneration)) {
            return;
        }
        pendingAnimationSources.remove(messageId);
        preparedAnimationCache.remove(messageId);
        GuiMessage lineToUpdate = activeTranslationLines.get(messageId);
        if (lineToUpdate == null) return;

        Minecraft.getInstance().execute(() -> {
            if (!isTranslationActive(messageId, requestGeneration)) {
                return;
            }
            ChatComponent chatHud = Minecraft.getInstance().gui.hud.getChat();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<GuiMessage> messages = chatHudAccessor.getMessages();

            int lineIndex = messages.indexOf(lineToUpdate);
            GuiMessage newLine = new GuiMessage(lineToUpdate.addedTime(), newContent, lineToUpdate.signature(), lineToUpdate.source(), lineToUpdate.tag());
            if (lineIndex != -1) {
                messages.set(lineIndex, newLine);
            }
            activeTranslationLines.put(messageId, newLine);
            replaceTrimmedLines(chatHudAccessor, lineToUpdate, newLine);
        });
    }

    public static void animatePendingChatLines() {
        if (pendingAnimationSources.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null) {
            return;
        }

        ChatComponent chatHud = client.gui.hud.getChat();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<GuiMessage> messages = chatHudAccessor.getMessages();

        for (Map.Entry<UUID, Component> entry : pendingAnimationSources.entrySet()) {
            UUID messageId = entry.getKey();
            if (!isTranslationActive(messageId)) {
                pendingAnimationSources.remove(messageId);
                preparedAnimationCache.remove(messageId);
                continue;
            }

            GuiMessage activeLine = activeTranslationLines.get(messageId);
            Component source = entry.getValue();
            if (activeLine == null || source == null) {
                continue;
            }

            int lineIndex = messages.indexOf(activeLine);
            GuiMessage animatedLine = new GuiMessage(
                    activeLine.addedTime(),
                    AnimationManager.getAnimatedStyledText(preparedAnimationCache.computeIfAbsent(messageId, id -> AnimationManager.prepareAnimatedSegments(source))),
                    activeLine.signature(),
                    activeLine.source(),
                    activeLine.tag()
            );
            if (lineIndex != -1) {
                messages.set(lineIndex, animatedLine);
            }
            activeTranslationLines.put(messageId, animatedLine);
            replaceTrimmedLines(chatHudAccessor, activeLine, animatedLine);
        }
    }

    private static boolean replaceTrimmedLines(ChatHudAccessor chatHudAccessor, GuiMessage oldLine, GuiMessage newLine) {
        List<GuiMessage.Line> trimmedMessages = chatHudAccessor.getTrimmedMessages();
        int firstIndex = -1;
        for (int i = 0; i < trimmedMessages.size(); i++) {
            if (trimmedMessages.get(i).parent() == oldLine) {
                firstIndex = i;
                break;
            }
        }
        if (firstIndex == -1) {
            return false;
        }
        int lastIndex = firstIndex;
        while (lastIndex + 1 < trimmedMessages.size() && trimmedMessages.get(lastIndex + 1).parent() == oldLine) {
            lastIndex++;
        }
        Minecraft client = Minecraft.getInstance();
        int lineWidth = Mth.floor(
                ChatComponent.getWidth(client.options.chatWidth().get())
                        / client.options.chatScale().get()
        );
        List<GuiMessage.Line> replacement = new ArrayList<>();
        List<FormattedCharSequence> splitLines = newLine.splitLines(client.font, lineWidth);
        for (int i = splitLines.size() - 1; i >= 0; i--) {
            replacement.add(new GuiMessage.Line(newLine, splitLines.get(i), i == splitLines.size() - 1));
        }
        trimmedMessages.subList(firstIndex, lastIndex + 1).clear();
        trimmedMessages.addAll(firstIndex, replacement);
        while (trimmedMessages.size() > MAX_TRIMMED_LINES) {
            trimmedMessages.remove(trimmedMessages.size() - 1);
        }
        return true;
    }


    private static void updateChatLineWithFinalText(UUID messageId, long requestGeneration, Component finalContent) {
        if (!isTranslationActive(messageId, requestGeneration)) {
            return;
        }
        pendingAnimationSources.remove(messageId);
        preparedAnimationCache.remove(messageId);
        lineLocateRetryCounts.remove(messageId);
        streamingUpdateLastApplied.remove(messageId);
        GuiMessage lineToUpdate = activeTranslationLines.remove(messageId);
        translationGenerations.remove(messageId, requestGeneration);
        if (lineToUpdate == null) {
            logChatLineMapping(messageId, "final_update_missing_active_line", -1, finalContent);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (!TranslationFeatureGate.isEnabled()) {
                return;
            }
            ChatComponent chatHud = Minecraft.getInstance().gui.hud.getChat();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<GuiMessage> messages = chatHudAccessor.getMessages();

            int lineIndex = messages.indexOf(lineToUpdate);
            Component finalLineContent = buildTranslatedMessageWithToggle(messageId, finalContent, MessageUtils.getTrackedMessage(messageId));
            GuiMessage newLine = new GuiMessage(lineToUpdate.addedTime(), finalLineContent, lineToUpdate.signature(), lineToUpdate.source(), lineToUpdate.tag());
            if (lineIndex != -1) {
                messages.set(lineIndex, newLine);
            }
            if (replaceTrimmedLines(chatHudAccessor, lineToUpdate, newLine)) {
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
            List<GuiMessage> messages,
            int lineIndex,
            GuiMessage originalLine
    ) {
        Component errorText = Component.translatable(NO_ROUTED_MODEL_ERROR_KEY).withStyle(ChatFormatting.RED);
        GuiMessage errorLine = new GuiMessage(originalLine.addedTime(), errorText, originalLine.signature(), originalLine.source(), originalLine.tag());
        if (lineIndex != -1) {
            messages.set(lineIndex, errorLine);
        }
        replaceTrimmedLines(chatHudAccessor, originalLine, errorLine);
        CompletableFuture.delayedExecutor(ROUTE_ERROR_DISPLAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> restoreLineAfterTemporaryError(messageId, errorLine, originalLine));
        });
    }

    private static void restoreLineAfterTemporaryError(UUID messageId, GuiMessage errorLine, GuiMessage originalLine) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null) {
            return;
        }

        ChatComponent chatHud = client.gui.hud.getChat();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<GuiMessage> messages = chatHudAccessor.getMessages();
        int lineIndex = messages.indexOf(errorLine);
        if (lineIndex != -1) {
            messages.set(lineIndex, originalLine);
        }
        replaceTrimmedLines(chatHudAccessor, errorLine, originalLine);

        lineLocateRetryCounts.remove(messageId);
    }

    private static LineSearchResult findTargetLine(List<GuiMessage> messages, Component originalMessage) {
        for (int i = 0; i < messages.size(); i++) {
            GuiMessage line = messages.get(i);
            if (matchesTargetTextReference(line.content(), originalMessage)) {
                return new LineSearchResult(i, line);
            }
        }

        for (int i = 0; i < messages.size(); i++) {
            GuiMessage line = messages.get(i);
            if (matchesTargetTextByContent(line.content(), originalMessage)) {
                return new LineSearchResult(i, line);
            }
        }
        return null;
    }

    private static boolean matchesTargetTextReference(Component lineContent, Component originalMessage) {
        if (lineContent.equals(originalMessage)) {
            return true;
        }
        return !lineContent.getSiblings().isEmpty() && lineContent.getSiblings().get(0).equals(originalMessage);
    }

    private static boolean matchesTargetTextByContent(Component lineContent, Component originalMessage) {
        String original = originalMessage.getString();
        if (lineContent.getString().equals(original)) {
            return true;
        }
        return !lineContent.getSiblings().isEmpty() && lineContent.getSiblings().get(0).getString().equals(original);
    }

    private static boolean isLineContentStillPresent(List<GuiMessage> messages, Component lineToLocate) {
        if (messages == null || lineToLocate == null) {
            return false;
        }
        String target = lineToLocate.getString();
        if (target == null || target.isEmpty()) {
            return false;
        }
        for (GuiMessage line : messages) {
            Component content = line.content();
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

    private static boolean scheduleLineLocateRetry(UUID messageId, Component originalMessage, Component lineToLocate, boolean forceRefresh) {
        if (!TranslationFeatureGate.isEnabled()) {
            return false;
        }
        int attempt = lineLocateRetryCounts.merge(messageId, 1, Integer::sum);
        if (attempt > MAX_LINE_LOCATE_RETRIES) {
            return false;
        }
        logLocateRetry(messageId, attempt, originalMessage);

        CompletableFuture.delayedExecutor(LINE_LOCATE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> translate(messageId, originalMessage, lineToLocate, forceRefresh));
        });
        return true;
    }

    private record LineSearchResult(int lineIndex, GuiMessage line) {
    }

    static PreparedChatTranslation prepareTranslationPayload(Component originalMessage) {
        Component sourceMessage = stripTrailingTranslationMarker(originalMessage);
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

    private static void cacheSkyblockNpcTranslation(String cacheKey, String translation) {
        if (!TranslationFeatureGate.isEnabled() || cacheKey == null || translation == null || translation.isBlank()) {
            return;
        }
        SkyblockNpcTranslationCache.getInstance().updateTranslations(Map.of(cacheKey, translation));
    }

    private static void cacheChatOutputTranslation(String cacheKey, String translation) {
        if (!TranslationFeatureGate.isEnabled() || cacheKey == null || translation == null || translation.isBlank()) {
            return;
        }
        ChatOutputTranslationCache.getInstance().updateTranslations(Map.of(cacheKey, translation));
    }

    public static void queuePendingAutoTranslation(UUID messageId, Component originalMessage) {
        if (messageId == null || originalMessage == null) {
            return;
        }
        pendingAutoTranslateMessages.put(messageId, originalMessage);
    }

    public static void flushPendingAutoTranslations() {
        if (pendingAutoTranslateMessages.isEmpty()) {
            return;
        }
        Map<UUID, Component> pending = Map.copyOf(pendingAutoTranslateMessages);
        pendingAutoTranslateMessages.clear();
        pending.forEach((messageId, originalMessage) -> translate(messageId, originalMessage));
    }

    public static void clearPendingAutoTranslations() {
        pendingAutoTranslateMessages.clear();
    }

    public static void cancelPendingTranslations() {
        Map<UUID, GuiMessage> pendingLines = Map.copyOf(activeTranslationLines);
        lineLocateRetryCounts.clear();
        translationGenerations.clear();
        activeTranslationLines.clear();
        pendingAnimationSources.clear();
        preparedAnimationCache.clear();
        streamingUpdateLastApplied.clear();
        pendingAutoTranslateMessages.clear();
        if (pendingLines.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
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
    }

    private static void restorePendingOriginalLines(Map<UUID, GuiMessage> pendingLines) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null) {
            return;
        }

        ChatComponent chatHud = client.gui.hud.getChat();
        if (chatHud == null) {
            return;
        }

        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<GuiMessage> messages = chatHudAccessor.getMessages();
        for (Map.Entry<UUID, GuiMessage> entry : pendingLines.entrySet()) {
            MessageUtils.TrackedChatMessage trackedMessage = MessageUtils.getTrackedChatMessage(entry.getKey());
            if (trackedMessage == null || trackedMessage.originalMessage() == null) {
                continue;
            }

            GuiMessage pendingLine = entry.getValue();
            GuiMessage restoredLine = new GuiMessage(
                    pendingLine.addedTime(),
                    trackedMessage.originalMessage().copy(),
                    pendingLine.signature(),
                    pendingLine.source(),
                    pendingLine.tag()
            );
            int lineIndex = messages.indexOf(pendingLine);
            if (lineIndex != -1) {
                messages.set(lineIndex, restoredLine);
            }
            if (replaceTrimmedLines(chatHudAccessor, pendingLine, restoredLine)) {
                MessageUtils.markShowingOriginal(entry.getKey());
            }
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

    private static Component stripTrailingTranslationMarker(Component message) {
        if (message == null || !message.getString().endsWith(" [T]")) {
            return message;
        }

        int retainedLength = message.getString().length() - 4;
        MutableComponent retained = Component.empty();
        int[] remaining = {retainedLength};
        message.visit((style, string) -> {
            if (remaining[0] <= 0) {
                return Optional.of(Boolean.TRUE);
            }
            int length = Math.min(remaining[0], string.length());
            if (length > 0) {
                retained.append(Component.literal(string.substring(0, length)).setStyle(style));
                remaining[0] -= length;
            }
            return remaining[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return retained;
    }

    static Component rebuildTranslatedText(String translatedText, PreparedChatTranslation preparedTranslation) {
        if (preparedTranslation == null) {
            return Component.literal(translatedText == null ? "" : translatedText);
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

    // Keep the leading speaker/NPC header out of the LLM payload and reassemble it verbatim.
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

    private static Component appendToggleButton(UUID messageId, Component messageContent, String action, String hoverTranslationKey) {
        MutableComponent root = Component.empty().append(messageContent.copy());
        MutableComponent toggleButton = Component.literal(" [T]");
        Style toggleStyle = Style.EMPTY
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/translate_allinone translatechatline " + messageId + " " + action))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverTranslationKey)));
        toggleButton.setStyle(toggleStyle);
        root.append(toggleButton);
        return root;
    }

    private static Component applyOriginalDisplayMode(Component translatedMessage, Component originalMessage) {
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

    private static Component buildSubtitleContent(Component translatedMessage, Component originalMessage) {
        MutableComponent root = Component.empty();
        root.append(translatedMessage.copy());
        root.append(Component.literal("\n"));
        root.append(muteForSubtitle(originalMessage));
        return root;
    }

    private static Component muteForSubtitle(Component original) {
        MutableComponent muted = Component.empty();
        original.visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Style base = style == null ? Style.EMPTY : style;
            StylePreserver.fromLegacyText(text).visit((resolvedStyle, resolvedText) -> {
                if (!resolvedText.isEmpty()) {
                    muted.append(Component.literal(resolvedText).setStyle(
                            resolvedStyle.withColor(ChatFormatting.GRAY).withItalic(true)
                    ));
                }
                return Optional.empty();
            }, base);
            return Optional.empty();
        }, Style.EMPTY);
        return muted;
    }

    private static Component attachOriginalHover(Component translatedMessage, Component originalMessage) {
        MutableComponent copy = translatedMessage.copy();
        copy.setStyle(copy.getStyle().withHoverEvent(new HoverEvent.ShowText(originalMessage)));
        return copy;
    }

    private static boolean isSamePlainText(Component a, Component b) {
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.gui.hud.getChat() == null) {
            return false;
        }
        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) client.gui.hud.getChat();
        List<GuiMessage> messages = chatHudAccessor.getMessages();
        String commandPrefix = "/translate_allinone translatechatline " + messageId + " " + action;
        for (GuiMessage line : messages) {
            if (handleToggleLine(messageId, action, chatHudAccessor, messages, line, commandPrefix)) {
                return true;
            }
        }
        GuiMessage displayLine = findTrimmedTargetByCommandPrefix(chatHudAccessor, messageId, action);
        return displayLine != null
                && handleToggleLine(messageId, action, chatHudAccessor, messages, displayLine, commandPrefix);
    }

    private static boolean handleToggleLine(
            UUID messageId,
            String action,
            ChatHudAccessor chatHudAccessor,
            List<GuiMessage> messages,
            GuiMessage line,
            String commandPrefix
    ) {
        Component content = line.content();
        if (content == null || !containsToggleCommand(content, commandPrefix)) {
            return false;
        }
        if (CHAT_TRANSLATE_ACTION.equalsIgnoreCase(action)) {
            Component original = extractToggleMessageContent(content);
            if (original != null) {
                MessageUtils.putTrackedMessage(messageId, original);
                translate(messageId, original);
                return true;
            }
            return true;
        } else if (CHAT_RESTORE_ACTION.equalsIgnoreCase(action)) {
            restoreShownLineAsOriginal(messageId, line, chatHudAccessor, messages);
            return true;
        }
        return true;
    }

    private static void restoreShownLineAsOriginal(UUID messageId, GuiMessage line, ChatHudAccessor chatHudAccessor, List<GuiMessage> messages) {
        Component subtitleOriginal = extractSubtitleOriginal(line.content());
        if (subtitleOriginal == null) {
            return;
        }
        MessageUtils.putTrackedMessage(messageId, subtitleOriginal);
        MessageUtils.markShowingOriginal(messageId);
        int lineIndex = messages.indexOf(line);
        GuiMessage restoredLine = new GuiMessage(
                line.addedTime(),
                buildOriginalMessageWithToggle(messageId, subtitleOriginal),
                line.signature(),
                line.source(),
                line.tag()
        );
        if (lineIndex != -1) {
            messages.set(lineIndex, restoredLine);
        }
        replaceTrimmedLines(chatHudAccessor, line, restoredLine);
    }

    private static GuiMessage findTrimmedTargetByCommandPrefix(ChatHudAccessor chatHudAccessor, UUID messageId, String action) {
        if (chatHudAccessor == null || messageId == null || action == null) {
            return null;
        }
        String commandPrefix = "/translate_allinone translatechatline " + messageId + " " + action;
        List<GuiMessage.Line> trimmedLines = chatHudAccessor.getTrimmedMessages();
        if (trimmedLines == null) {
            return null;
        }
        for (GuiMessage.Line trimmedLine : trimmedLines) {
            GuiMessage parent = trimmedLine.parent();
            if (parent != null && parent.content() != null && containsToggleCommand(parent.content(), commandPrefix)) {
                return parent;
            }
        }
        return null;
    }

    private static boolean containsToggleCommand(Component content, String commandPrefix) {
        if (hasToggleCommand(content.getStyle(), commandPrefix)) {
            return true;
        }
        for (Component sibling : content.getSiblings()) {
            if (hasToggleCommand(sibling.getStyle(), commandPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasToggleCommand(Style style, String commandPrefix) {
        if (style == null) {
            return false;
        }
        ClickEvent clickEvent = style.getClickEvent();
        return clickEvent instanceof ClickEvent.RunCommand runCommand
                && runCommand.command() != null
                && runCommand.command().startsWith(commandPrefix);
    }

    private static Component extractToggleMessageContent(Component root) {
        if (root == null || root.getSiblings().isEmpty()) {
            return null;
        }
        Component subtitleOriginal = extractSubtitleOriginal(root);
        if (subtitleOriginal != null) {
            return subtitleOriginal;
        }
        Component first = root.getSiblings().get(0);
        return first == null ? null : first.copy();
    }

    private static Component extractSubtitleOriginal(Component root) {
        if (root == null || root.getSiblings().isEmpty()) {
            return null;
        }
        Component first = root.getSiblings().get(0);
        List<Component> parts = first.getSiblings();
        if (parts.size() < 3 || !"\n".equals(parts.get(1).getString())) {
            return null;
        }
        Component subtitle = parts.get(2);
        return subtitle == null || subtitle.getString().isEmpty() ? null : subtitle.copy();
    }

    @NotNull
    private static List<OpenAIRequest.Message> getMessages(ApiProviderProfile providerProfile, String targetLanguage, String textToTranslate) {
        String basePrompt = PromptMessageBuilder.getDefaultPrompt("chat_output", targetLanguage);
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
            Component originalMessage,
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
            Component finalStyledText,
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

    private static void logChatLineMapping(UUID messageId, String action, int lineIndex, Component content) {
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

    private static void logLocateRetry(UUID messageId, int attempt, Component originalMessage) {
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

    private static String describeTextSegments(Component text) {
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
            fields.add("color=" + formatRgb(style.getColor().getValue()));
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
            fields.add("click=" + style.getClickEvent().action().getSerializedName());
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
