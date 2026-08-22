package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.TranslateStringUtils;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.llmapi.LLM;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ChatInputTranslateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatInputTranslateManager.class);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Chat-Input-Translate-Thread");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean isTranslating = new AtomicBoolean(false);
    private static final AtomicReference<String> originalTextRef = new AtomicReference<>("");
    private static final AtomicReference<String> lastSourceTextRef = new AtomicReference<>("");
    private static final long ROUTE_ERROR_DISPLAY_MS = 3_000L;
    private static final String TRANSLATING_KEY = "text.translate_allinone.translation.status.translating";
    private static final String TRANSLATION_ERROR_KEY = "text.translate_allinone.chat.input_translation_error";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";

    public enum PanelAvailability {
        NO_MODEL,
        TRANSLATE_ONLY,
        FULL
    }

    private enum TransformMode {
        TRANSLATE,
        PROFESSIONAL,
        FRIENDLY,
        EXPAND,
        CONCISE,
        INSTRUCTION
    }

    public static void translate(EditBox chatField) {
        submitTransform(chatField, TransformMode.TRANSLATE);
    }

    public static PanelAvailability getPanelAvailability() {
        ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                ProviderRouteResolver.Route.CHAT_INPUT
        );
        if (providerProfile == null || providerProfile.model_id == null || providerProfile.model_id.isBlank()) {
            return PanelAvailability.NO_MODEL;
        }
        if (!providerProfile.activeSupportsSystemMessage()) {
            return PanelAvailability.TRANSLATE_ONLY;
        }
        return PanelAvailability.FULL;
    }

    public static void translateProfessional(EditBox chatField) {
        submitTransform(chatField, TransformMode.PROFESSIONAL);
    }

    public static void translateFriendly(EditBox chatField) {
        submitTransform(chatField, TransformMode.FRIENDLY);
    }

    public static void translateExpand(EditBox chatField) {
        submitTransform(chatField, TransformMode.EXPAND);
    }

    public static void translateDetailed(EditBox chatField) {
        translateExpand(chatField);
    }

    public static void translateConcise(EditBox chatField) {
        submitTransform(chatField, TransformMode.CONCISE);
    }

    public static void rewriteByInstruction(EditBox chatField, String instruction) {
        String normalizedInstruction = instruction == null ? "" : instruction.trim();
        if (normalizedInstruction.isEmpty()) {
            return;
        }
        submitTransform(chatField, TransformMode.INSTRUCTION, normalizedInstruction);
    }

    public static void restoreOriginal(EditBox chatField) {
        if (chatField == null || isTranslating.get()) {
            return;
        }

        String original = lastSourceTextRef.get();
        if (original == null || original.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            chatField.setValue(original);
            chatField.moveCursorTo(original.length(), false);
        });
    }

    private static void submitTransform(EditBox chatField, TransformMode mode) {
        submitTransform(chatField, mode, null);
    }

    private static void submitTransform(EditBox chatField, TransformMode mode, String instruction) {
        if (!isTranslating.compareAndSet(false, true)) {
            return; // Already translating
        }

        long translationGeneration = mode == TransformMode.TRANSLATE
                ? TranslationFeatureGate.generation()
                : -1L;
        if (!isTransformActive(mode, translationGeneration)) {
            isTranslating.set(false);
            return;
        }

        ChatTranslateConfig.ChatInputTranslateConfig inputConfig = Translate_AllinOne.getConfig().chatTranslate.input;
        if (!inputConfig.enabled) {
            isTranslating.set(false);
            return;
        }

        String currentText = chatField.getValue();
        if (currentText.trim().isEmpty()) {
            isTranslating.set(false);
            return;
        }
        originalTextRef.set(currentText);
        lastSourceTextRef.set(currentText);

        executor.submit(() -> {
            String requestContext = "route=chat_input, mode=" + mode.name().toLowerCase();
            try {
                if (!isTransformActive(mode, translationGeneration)) {
                    return;
                }
                ApiProviderProfile providerProfile = ProviderRouteResolver.resolve(
                        Translate_AllinOne.getConfig(),
                        ProviderRouteResolver.Route.CHAT_INPUT
                );
                if (providerProfile == null) {
                    LOGGER.warn("No routed model selected for chat input translation; showing temporary error.");
                    showTemporaryRouteError(chatField, originalTextRef.get());
                    return;
                }

                if (!isTransformModeSupported(providerProfile, mode)) {
                    LOGGER.info(
                            "Skip unsupported chat input transform mode={} for model={} (supportsSystemMessage={})",
                            mode.name().toLowerCase(),
                            providerProfile.model_id,
                            providerProfile.activeSupportsSystemMessage()
                    );
                    return;
                }

                ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
                LLM llm = new LLM(settings);

                List<OpenAIRequest.Message> apiMessages = getMessages(providerProfile, inputConfig.target_language, originalTextRef.get(), mode, instruction);
                requestContext = buildRequestContext(
                        providerProfile,
                        inputConfig.target_language,
                        originalTextRef.get(),
                        apiMessages,
                        inputConfig.streaming_response,
                        mode,
                        instruction
                );

                if (inputConfig.streaming_response) {
                    final StringBuilder rawResponseBuffer = new StringBuilder();
                    final StringBuilder visibleContentBuffer = new StringBuilder();
                    final AtomicBoolean inThinkTag = new AtomicBoolean(false);

                    Minecraft.getInstance().execute(() -> {
                        if (isTransformActive(mode, translationGeneration)) {
                            chatField.setValue("Connecting...");
                        }
                    });

                    llm.getStreamingCompletion(apiMessages, requestContext).forEach(chunk -> {
                        if (!isTransformActive(mode, translationGeneration)) {
                            return;
                        }
                        rawResponseBuffer.append(chunk);

                        while (true) {
                            if (inThinkTag.get()) {
                                int endTagIndex = rawResponseBuffer.indexOf("</think>");
                                if (endTagIndex != -1) {
                                    inThinkTag.set(false);
                                    rawResponseBuffer.delete(0, endTagIndex + "</think>".length());

                                    // Restore the visible content so far
                                    String currentTranslation = visibleContentBuffer.toString().stripLeading();
                                    Minecraft.getInstance().execute(() -> {
                                        if (!isTransformActive(mode, translationGeneration)) {
                                            return;
                                        }
                                        chatField.setValue(currentTranslation);
                                        chatField.moveCursorTo(currentTranslation.length(), false);
                                    });
                                    continue; // Check for more tags in the same chunk
                                }
                                break; // Incomplete tag, wait for more chunks
                            } else {
                                int startTagIndex = rawResponseBuffer.indexOf("<think>");
                                if (startTagIndex != -1) {
                                    // Found a think tag. Append content before it to visible buffer.
                                    String translationPart = rawResponseBuffer.substring(0, startTagIndex);
                                    visibleContentBuffer.append(translationPart);
                                    rawResponseBuffer.delete(0, startTagIndex + "<think>".length());
                                    inThinkTag.set(true);

                                    // Now display "Thinking..."
                                    Minecraft.getInstance().execute(() -> {
                                        if (isTransformActive(mode, translationGeneration)) {
                                            chatField.setValue("Thinking...");
                                        }
                                    });

                                    continue; // Check for more tags
                                } else {
                                    // No think tag, just regular content
                                    visibleContentBuffer.append(rawResponseBuffer.toString());
                                    rawResponseBuffer.setLength(0);
                                    String currentTranslation = visibleContentBuffer.toString().stripLeading();
                                    Minecraft.getInstance().execute(() -> {
                                        if (!isTransformActive(mode, translationGeneration)) {
                                            return;
                                        }
                                        chatField.setValue(currentTranslation);
                                        chatField.moveCursorTo(currentTranslation.length(), false);
                                    });
                                    break; // Wait for more chunks
                                }
                            }
                        }
                    });

                    // Final update after stream is complete, using the accumulated visible content
                    Minecraft.getInstance().execute(() -> {
                        if (!isTransformActive(mode, translationGeneration)) {
                            return;
                        }
                        String finalTranslation = visibleContentBuffer.toString().stripLeading();
                        chatField.setValue(finalTranslation);
                        chatField.moveCursorTo(finalTranslation.length(), false);
                    });
                } else {
                    if (!isTransformActive(mode, translationGeneration)) {
                        return;
                    }
                    Minecraft.getInstance().execute(() -> {
                        if (isTransformActive(mode, translationGeneration)) {
                            chatField.setValue(Component.translatable(TRANSLATING_KEY).getString());
                        }
                    });
                    String result = llm.getCompletion(apiMessages, requestContext).join();
                    if (!isTransformActive(mode, translationGeneration)) {
                        return;
                    }
                    final String finalTranslation = result.stripLeading();
                    Minecraft.getInstance().execute(() -> {
                        if (!isTransformActive(mode, translationGeneration)) {
                            return;
                        }
                        chatField.setValue(finalTranslation);
                        chatField.moveCursorTo(finalTranslation.length(), false);
                    });
                }
            } catch (Exception e) {
                if (!isTransformActive(mode, translationGeneration)) {
                    return;
                }
                LOGGER.error("[Chat-Input-Translate] Exception during translation. context={}", requestContext, e);
                Minecraft.getInstance().execute(() -> {
                    if (!isTransformActive(mode, translationGeneration)) {
                        return;
                    }
                    Component errorMessage = Component.translatable(TRANSLATION_ERROR_KEY, TranslationErrorTextSupport.localizeReason(e.getMessage())).withStyle(ChatFormatting.RED);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(errorMessage);
                    }
                    chatField.setValue(originalTextRef.get()); // Restore original on error
                    chatField.moveCursorTo(originalTextRef.get().length(), false);
                });
            } finally {
                isTranslating.set(false);
                originalTextRef.set("");
            }
        });
    }

    private static boolean isTransformActive(TransformMode mode, long translationGeneration) {
        return mode != TransformMode.TRANSLATE || TranslationFeatureGate.isActive(translationGeneration);
    }

    @NotNull
    private static List<OpenAIRequest.Message> getMessages(
            ApiProviderProfile providerProfile,
            String targetLanguage,
            String textToTranslate,
            TransformMode mode,
            String instruction
    ) {
        String basePrompt = buildSystemPrompt(targetLanguage, mode, instruction);
        String resolved = PromptMessageBuilder.applyPromptOverride("chat_input_translate", basePrompt, providerProfile.system_prompt_overrides, targetLanguage);
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

    private static String buildSystemPrompt(String targetLanguage, TransformMode mode, String instruction) {
        return switch (mode) {
            case TRANSLATE -> PromptMessageBuilder.getDefaultPrompt("chat_input_translate", targetLanguage);
            case PROFESSIONAL -> buildRewritePrompt(targetLanguage, "professional, precise, concise");
            case FRIENDLY -> buildRewritePrompt(targetLanguage, "friendly, warm, natural");
            case EXPAND -> buildRewritePrompt(targetLanguage, "rich, vivid, and more detailed while keeping the original intent");
            case CONCISE -> buildRewritePrompt(targetLanguage, "concise, direct, and compact while preserving key meaning");
            case INSTRUCTION -> buildInstructionPrompt(targetLanguage, instruction);
        };
    }

    private static String buildRewritePrompt(String targetLanguage, String styleHint) {
        return "Rewrite player-composed Minecraft chat input in " + targetLanguage + ".\n"
                + "Style: " + styleHint + ".\n"
                + "Output only the final message, with no Markdown, quotes, or explanation.\n"
                + "Preserve meaning, gameplay terms, commands beginning with /, player names, item ids, URLs, numbers, Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, \\n, and \\t exactly.\n"
                + "Keep an uncertain term unchanged.";
    }

    private static String buildInstructionPrompt(String targetLanguage, String instruction) {
        String normalizedInstruction = instruction == null ? "" : instruction.trim();
        return "Rewrite player-composed Minecraft chat input in " + targetLanguage + ".\n"
                + "Requested transformation: " + normalizedInstruction + "\n"
                + "Output only the final message, with no Markdown, quotes, or explanation.\n"
                + "Follow the requested transformation only when it preserves the original intent and these exact tokens: commands beginning with /, player names, item ids, URLs, numbers, Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, \\n, and \\t.\n"
                + "Keep an uncertain term unchanged.";
    }

    private static String buildRequestContext(
            ApiProviderProfile profile,
            String targetLanguage,
            String originalText,
            List<OpenAIRequest.Message> messages,
            boolean streaming,
            TransformMode mode,
            String instruction
    ) {
        String providerId = profile == null ? "" : profile.id;
        String modelId = profile == null ? "" : profile.model_id;
        int messageCount = messages == null ? 0 : messages.size();
        String roles = messages == null
                ? "[]"
                : messages.stream().map(message -> message == null ? "null" : String.valueOf(message.role)).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String sample = TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(originalText), 160);
        String instructionSample = TranslateStringUtils.truncate(TranslateStringUtils.normalizeWhitespace(instruction), 120);
        return "route=chat_input"
                + ", provider=" + providerId
                + ", model=" + modelId
                + ", target=" + (targetLanguage == null ? "" : targetLanguage)
                + ", mode=" + mode.name().toLowerCase()
                + ", streaming=" + streaming
                + ", messages=" + messageCount
                + ", roles=" + roles
                + ", instruction=\"" + instructionSample + "\""
                + ", sample=\"" + sample + "\"";
    }

    private static boolean isTransformModeSupported(ApiProviderProfile providerProfile, TransformMode mode) {
        if (providerProfile == null || providerProfile.model_id == null || providerProfile.model_id.isBlank()) {
            return false;
        }
        if (mode == TransformMode.TRANSLATE) {
            return true;
        }
        return providerProfile.activeSupportsSystemMessage();
    }

    private static void showTemporaryRouteError(EditBox chatField, String originalText) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        final String errorText = Component.translatable(NO_ROUTED_MODEL_ERROR_KEY).getString();
        final String fallbackText = originalText == null ? "" : originalText;

        client.execute(() -> {
            chatField.setValue(errorText);
            chatField.moveCursorTo(errorText.length(), false);
        });

        CompletableFuture.delayedExecutor(ROUTE_ERROR_DISPLAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft delayedClient = Minecraft.getInstance();
            if (delayedClient == null) {
                return;
            }
            delayedClient.execute(() -> {
                if (!errorText.equals(chatField.getValue())) {
                    return;
                }
                chatField.setValue(fallbackText);
                chatField.moveCursorTo(fallbackText.length(), false);
            });
        });
    }
}
