package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.mixin.mixinChatHud.ChatHudAccessor;
import com.cedarxuesong.translate_allinone.utils.TranslateStringUtils;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.MessageUtils;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.SkyblockNpcTranslationCache;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.cedarxuesong.translate_allinone.utils.text.LegacyComponentTextCodec;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
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
    private static final Map<UUID, Integer> lineLocateRetryCounts = new ConcurrentHashMap<>();
    private static ExecutorService translationExecutor;
    private static int currentConcurrentRequests = -1;
    private static final int MAX_LINE_LOCATE_RETRIES = 4;
    private static final long LINE_LOCATE_RETRY_DELAY_MS = 40L;
    private static final long ROUTE_ERROR_DISPLAY_MS = 3_000L;
    private static final String TRANSLATING_KEY = "text.translate_allinone.translation.status.translating";
    private static final String TRANSLATION_ERROR_KEY = "text.translate_allinone.chat.output_translation_error";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL);
    private static final Pattern CHAT_IGNORABLE_PLACEHOLDER_PATTERN = Pattern.compile("\\{c(\\d+)}");
    private static final String SKYBLOCK_NPC_FORMATTED_UNIT = "(?:§[0-9a-fk-or]|[^§\\r\\n])";
    private static final String SKYBLOCK_NPC_NAME_TOKEN = "[\\p{L}\\p{N}_.'-]+";
    private static final Pattern SKYBLOCK_NPC_CHAT_PATTERN = Pattern.compile(
            "^(?:\\[CHAT\\] )?(?:§r)?(?:§e)?\\[NPC\\] (?:§r)?(?:§[0-9a-f])"
                    + SKYBLOCK_NPC_NAME_TOKEN + "(?: " + SKYBLOCK_NPC_NAME_TOKEN + ")*"
                    + "(?:§r)?(?:§f)?: (?:(?:§r)?§f)?"
                    + "(?<body>" + SKYBLOCK_NPC_FORMATTED_UNIT + "+?)(?:§[0-9a-fk-or])*"
                    + "(?:\\s+\\[T\\])?$"
    );

    public static Component buildOriginalMessageWithToggle(UUID messageId, Component originalMessage) {
        return appendToggleButton(messageId, originalMessage, CHAT_TRANSLATE_ACTION, "text.translate_allinone.translate_button_hover");
    }

    public static Component buildTranslatedMessageWithToggle(UUID messageId, Component translatedMessage) {
        return appendToggleButton(messageId, translatedMessage, CHAT_RESTORE_ACTION, "text.translate_allinone.restore_button_hover");
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
        if (activeTranslationLines.containsKey(messageId)) {
            lineLocateRetryCounts.remove(messageId);
            return; // Already being translated
        }

        Minecraft client = Minecraft.getInstance();
        ChatComponent chatHud = client.gui.hud.getChat();
        ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
        List<GuiMessage> messages = chatHudAccessor.getMessages();
        LineSearchResult searchResult = findTargetLine(messages, originalMessage);

        if (searchResult == null) {
            if (scheduleLineLocateRetry(messageId, originalMessage)) {
                return;
            }
            LOGGER.error("Could not find chat line to update for messageId: {} after {} retries", messageId, MAX_LINE_LOCATE_RETRIES);
            lineLocateRetryCounts.remove(messageId);
            MessageUtils.removeTrackedMessage(messageId);
            return;
        }
        lineLocateRetryCounts.remove(messageId);
        int lineIndex = searchResult.lineIndex();
        GuiMessage targetLine = searchResult.line();
        logChatLineMapping(messageId, "locate_original", lineIndex, targetLine.content());

        updateExecutorServiceIfNeeded();

        ChatTranslateConfig.ChatOutputTranslateConfig chatOutputConfig = Translate_AllinOne.getConfig().chatTranslate.output;
        boolean skyblockNpcMessage = isSkyblockNpcMessage(originalMessage);
        PreparedChatTranslation cachedPreparedTranslation = skyblockNpcMessage
                ? prepareTranslationPayload(originalMessage)
                : null;
        String skyblockCacheKey = cachedPreparedTranslation == null
                ? null
                : buildSkyblockNpcCacheKey(chatOutputConfig.target_language, cachedPreparedTranslation.textToTranslate());
        LookupResult skyblockCacheLookup = skyblockCacheKey == null
                ? null
                : SkyblockNpcTranslationCache.getInstance().peek(skyblockCacheKey);
        ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                ProviderRouteResolver.Route.CHAT_OUTPUT
        );
        if (providerProfile == null
                && (skyblockCacheLookup == null || skyblockCacheLookup.status() != TranslationStatus.TRANSLATED)) {
            LOGGER.warn("No routed model selected for chat output translation; showing temporary error for messageId={}", messageId);
            showTemporaryRouteError(messageId, chatHudAccessor, messages, lineIndex, targetLine);
            lineLocateRetryCounts.remove(messageId);
            return;
        }

        boolean isAutoTranslate = chatOutputConfig.auto_translate;
        boolean isStreaming = chatOutputConfig.streaming_response;
        Component placeholderText;

        if (isStreaming) {
            placeholderText = Component.literal("Connecting...").withStyle(ChatFormatting.GRAY);
        } else if (isAutoTranslate) {
            String plainText = AnimationManager.stripFormatting(originalMessage.getString());
            MutableComponent newText = Component.literal(plainText);

            Style baseStyle = originalMessage.getStyle();
            Style newStyle = baseStyle.withColor(ChatFormatting.GRAY);
            newText.setStyle(newStyle);

            if (!originalMessage.getSiblings().isEmpty()) {
                MutableComponent fullText = Component.empty();
                originalMessage.getSiblings().forEach(sibling -> {
                    String plainSibling = AnimationManager.stripFormatting(sibling.getString());
                    fullText.append(Component.literal(plainSibling).setStyle(sibling.getStyle().withColor(ChatFormatting.GRAY)));
                });
                placeholderText = fullText;
            } else {
                placeholderText = newText;
            }
        } else {
            placeholderText = Component.translatable(TRANSLATING_KEY).withStyle(ChatFormatting.GRAY);
        }

        GuiMessage newLine = new GuiMessage(targetLine.addedTime(), placeholderText, targetLine.signature(), targetLine.source(), targetLine.tag());
        int scrolledLines = chatHudAccessor.getScrolledLines();
        messages.set(lineIndex, newLine);
        activeTranslationLines.put(messageId, newLine);
        chatHudAccessor.invokeRefresh();
        chatHudAccessor.setScrolledLines(scrolledLines);

        final String finalSkyblockCacheKey = skyblockCacheKey;
        final LookupResult finalSkyblockCacheLookup = skyblockCacheLookup;
        final PreparedChatTranslation finalCachedPreparedTranslation = cachedPreparedTranslation;
        translationExecutor.submit(() -> {
            String requestContext = "route=chat_output,messageId=" + messageId;
            try {
                if (finalSkyblockCacheLookup != null
                        && finalSkyblockCacheLookup.status() == TranslationStatus.TRANSLATED
                        && finalCachedPreparedTranslation != null) {
                    String cachedTranslation = finalSkyblockCacheLookup.translation();
                    Component finalStyledText = rebuildTranslatedText(cachedTranslation, finalCachedPreparedTranslation);
                    logReflowResult(
                            messageId,
                            false,
                            cachedTranslation,
                            cachedTranslation,
                            finalStyledText,
                            finalCachedPreparedTranslation.styleMap()
                    );
                    updateChatLineWithFinalText(messageId, finalStyledText);
                    return;
                }

                if (providerProfile == null) {
                    showTemporaryRouteError(messageId, chatHudAccessor, messages, lineIndex, targetLine);
                    return;
                }

                ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
                LLM llm = new LLM(settings);

                PreparedChatTranslation preparedTranslation = finalCachedPreparedTranslation == null
                        ? prepareTranslationPayload(originalMessage)
                        : finalCachedPreparedTranslation;
                String textToTranslate = preparedTranslation.textToTranslate();
                Map<Integer, Style> styleMap = preparedTranslation.styleMap();

                List<OpenAIRequest.Message> apiMessages = getMessages(providerProfile, chatOutputConfig.target_language, textToTranslate);
                requestContext = buildRequestContext(providerProfile, chatOutputConfig.target_language, textToTranslate, apiMessages, chatOutputConfig.streaming_response, messageId);
                logLlmSubmission(messageId, providerProfile, chatOutputConfig, originalMessage, textToTranslate, styleMap, apiMessages, requestContext);

                LOGGER.info("Starting translation for message ID: {}. Marked text: {}", messageId, textToTranslate);

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
                                    updateInProgressChatLine(messageId, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    continue;
                                } else {
                                    int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                    if (startTagIndex != -1) {
                                        String thinkContent = rawResponseBuffer.substring(startTagIndex + "<think>".length());
                                        updateInProgressChatLine(messageId, Component.literal("Thinking: ").append(thinkContent).withStyle(ChatFormatting.GRAY));
                                    }
                                    break;
                                }
                            } else {
                                int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                if (startTagIndex != -1) {
                                    String translationPart = rawResponseBuffer.substring(0, startTagIndex);
                                    visibleContentBuffer.append(translationPart);
                                    updateInProgressChatLine(messageId, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));

                                    rawResponseBuffer.delete(0, startTagIndex);
                                    inThinkTag.set(true);
                                    continue;
                                } else {
                                    visibleContentBuffer.append(rawResponseBuffer.toString());
                                    rawResponseBuffer.setLength(0);
                                    updateInProgressChatLine(messageId, Component.literal(visibleContentBuffer.toString().replaceAll("</?s\\d+>", "")));
                                    break;
                                }
                            }
                        }
                    });

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
                    cacheSkyblockNpcTranslation(finalSkyblockCacheKey, finalTranslation);
                    updateChatLineWithFinalText(messageId, finalStyledText);
                } else {
                    String result = llm.getCompletion(apiMessages, requestContext).join();
                    LOGGER.info("Finished translation for message ID: {}. Result: {}", messageId, result);
                    final String finalTranslation = result.stripLeading();
                    Component finalStyledText = rebuildTranslatedText(finalTranslation, preparedTranslation);
                    logReflowResult(messageId, false, result, finalTranslation, finalStyledText, styleMap);
                    cacheSkyblockNpcTranslation(finalSkyblockCacheKey, finalTranslation);
                    updateChatLineWithFinalText(messageId, finalStyledText);
                }
            } catch (Exception e) {
                LOGGER.error("[Translate-Thread] Exception for message ID: {}. context={}", messageId, requestContext, e);
                Component errorText = Component.translatable(TRANSLATION_ERROR_KEY, TranslationErrorTextSupport.localizeReason(e.getMessage())).withStyle(ChatFormatting.RED);
                updateChatLineWithFinalText(messageId, errorText);
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
            if (searchResult == null) {
                return;
            }

            int scrolledLines = chatHudAccessor.getScrolledLines();
            Component restoredContent = buildOriginalMessageWithToggle(messageId, originalMessage);
            GuiMessage targetLine = searchResult.line();
            GuiMessage restoredLine = new GuiMessage(targetLine.addedTime(), restoredContent, targetLine.signature(), targetLine.source(), targetLine.tag());
            messages.set(searchResult.lineIndex(), restoredLine);
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
            MessageUtils.markShowingOriginal(messageId);
        });
    }

    private static void updateInProgressChatLine(UUID messageId, Component newContent) {
        GuiMessage lineToUpdate = activeTranslationLines.get(messageId);
        if (lineToUpdate == null) return;

        Minecraft.getInstance().execute(() -> {
            ChatComponent chatHud = Minecraft.getInstance().gui.hud.getChat();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<GuiMessage> messages = chatHudAccessor.getMessages();
            int scrolledLines = chatHudAccessor.getScrolledLines();

            int lineIndex = messages.indexOf(lineToUpdate);

            if (lineIndex != -1) {
                GuiMessage newLine = new GuiMessage(lineToUpdate.addedTime(), newContent, lineToUpdate.signature(), lineToUpdate.source(), lineToUpdate.tag());
                messages.set(lineIndex, newLine);
                activeTranslationLines.put(messageId, newLine);
                chatHudAccessor.invokeRefresh();
                chatHudAccessor.setScrolledLines(scrolledLines);
            }
        });
    }

    private static void updateChatLineWithFinalText(UUID messageId, Component finalContent) {
        lineLocateRetryCounts.remove(messageId);
        GuiMessage lineToUpdate = activeTranslationLines.remove(messageId);
        if (lineToUpdate == null) {
            logChatLineMapping(messageId, "final_update_missing_active_line", -1, finalContent);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            ChatComponent chatHud = Minecraft.getInstance().gui.hud.getChat();
            if (chatHud == null) return;

            ChatHudAccessor chatHudAccessor = (ChatHudAccessor) chatHud;
            List<GuiMessage> messages = chatHudAccessor.getMessages();
            int scrolledLines = chatHudAccessor.getScrolledLines();

            int lineIndex = messages.indexOf(lineToUpdate);

            if (lineIndex != -1) {
                Component finalLineContent = buildTranslatedMessageWithToggle(messageId, finalContent);
                GuiMessage newLine = new GuiMessage(lineToUpdate.addedTime(), finalLineContent, lineToUpdate.signature(), lineToUpdate.source(), lineToUpdate.tag());
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
            List<GuiMessage> messages,
            int lineIndex,
            GuiMessage originalLine
    ) {
        int scrolledLines = chatHudAccessor.getScrolledLines();
        Component errorText = Component.translatable(NO_ROUTED_MODEL_ERROR_KEY).withStyle(ChatFormatting.RED);
        GuiMessage errorLine = new GuiMessage(originalLine.addedTime(), errorText, originalLine.signature(), originalLine.source(), originalLine.tag());
        messages.set(lineIndex, errorLine);
        chatHudAccessor.invokeRefresh();
        chatHudAccessor.setScrolledLines(scrolledLines);

        CompletableFuture.delayedExecutor(ROUTE_ERROR_DISPLAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> restoreLineAfterTemporaryError(messageId, errorLine, originalLine));
        });

        MessageUtils.removeTrackedMessage(messageId);
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
            int scrolledLines = chatHudAccessor.getScrolledLines();
            messages.set(lineIndex, originalLine);
            chatHudAccessor.invokeRefresh();
            chatHudAccessor.setScrolledLines(scrolledLines);
        }

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

    private static boolean scheduleLineLocateRetry(UUID messageId, Component originalMessage) {
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
            client.execute(() -> translate(messageId, originalMessage));
        });
        return true;
    }

    private record LineSearchResult(int lineIndex, GuiMessage line) {
    }

    static PreparedChatTranslation prepareTranslationPayload(Component originalMessage) {
        Component sourceMessage = stripTrailingTranslationMarker(originalMessage);
        StylePreserver.ExtractionResult extraction = StylePreserver.extractAndMarkWithTags(sourceMessage);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(extraction.markedText);
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = TemplateProcessor.extractDecorativeGlyphTags(templateResult.template());
        String normalizedTemplate = TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(glyphResult.template());
        IgnorableChatSegmentExtractionResult ignorableSegments = extractIgnorableChatSegments(normalizedTemplate);
        return new PreparedChatTranslation(
                ignorableSegments.template(),
                extraction.styleMap,
                templateResult.values(),
                glyphResult.values(),
                ignorableSegments.values()
        );
    }

    private static String buildSkyblockNpcCacheKey(String targetLanguage, String textToTranslate) {
        if (textToTranslate == null || textToTranslate.isBlank()) {
            return null;
        }
        String normalizedLanguage = targetLanguage == null
                ? ""
                : targetLanguage.trim().toLowerCase(Locale.ROOT);
        return "target=" + normalizedLanguage + "\u001f" + textToTranslate;
    }

    private static void cacheSkyblockNpcTranslation(String cacheKey, String translation) {
        if (cacheKey == null || translation == null || translation.isBlank()) {
            return;
        }
        SkyblockNpcTranslationCache.getInstance().updateTranslations(Map.of(cacheKey, translation));
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
                translatedText == null ? "" : translatedText,
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
            List<String> ignorableSegments
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
