package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.TranslateExceptionUtils;
import com.cedarxuesong.translate_allinone.utils.TranslateStringUtils;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.LlmCompletion;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.StructuredOutputSpec;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.cedarxuesong.translate_allinone.utils.translate.PromptMessageBuilder;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ComponentTranslationClient {
    private static final int MAX_RESPONSE_ATTEMPTS = 2;
    private static final int MAX_CORRECTION_REASON_CHARS = 480;
    private static final String PROTOCOL_CONTRACT = "Return one JSON object with only protocol and translations fields. "
            + "protocol must be taio-component-v1. Return exactly the requested ids, once each, with string values only. "
            + "No Component JSON, JSON Pointer operations, Markdown, explanations, or extra fields. "
            + "Read all items before translating; context is only for meaning, never for merging items.";
    private static final String COHERENT_PARAGRAPH_CONTRACT = "\n"
            + "tooltip_paragraph contains one wrapped paragraph: translate it coherently, not line-by-line or tag-by-tag. "
            + "Natural target-language order may cross source spans. Preserve every non-style placeholder exactly. "
            + "<sN> is a semantic style: use every source id, invent none, and keep tags flat and balanced.";

    private final ComponentResponseParser parser;
    private final ComponentTranslationValidator validator;
    private final ComponentTranslationApplier applier;

    public ComponentTranslationClient() {
        this(new ComponentResponseParser(), new ComponentTranslationValidator(), new ComponentTranslationApplier());
    }

    ComponentTranslationClient(
            ComponentResponseParser parser,
            ComponentTranslationValidator validator,
            ComponentTranslationApplier applier
    ) {
        this.parser = parser;
        this.validator = validator;
        this.applier = applier;
    }

    public CompletableFuture<Component> translate(
            ComponentTranslationDocument document,
            String targetLanguage,
            ApiProviderProfile providerProfile,
            String requestContext
    ) {
        return translateResult(document, targetLanguage, providerProfile, requestContext)
                .thenApply(TranslationResult::component);
    }

    public CompletableFuture<TranslationResult> translateResult(
            ComponentTranslationDocument document,
            String targetLanguage,
            ApiProviderProfile providerProfile,
            String requestContext
    ) {
        if (document != null && document.units().isEmpty()) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.NO_TEXT);
            return CompletableFuture.completedFuture(new TranslationResult(
                    ComponentJsonCodec.decode(document.sourceJson()),
                    new ComponentTranslationResponse(document.protocol(), java.util.Map.of())
            ));
        }
        return translateResponse(document, targetLanguage, providerProfile, requestContext)
                .thenApply(response -> new TranslationResult(applier.apply(document, response), response));
    }

    public CompletableFuture<ComponentTranslationResponse> translateResponse(
            ComponentTranslationDocument document,
            String targetLanguage,
            ApiProviderProfile providerProfile,
            String requestContext
    ) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank() || providerProfile == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Component provider request is incomplete."));
        }
        if (document.units().isEmpty()) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.NO_TEXT);
            return CompletableFuture.completedFuture(
                    new ComponentTranslationResponse(document.protocol(), java.util.Map.of())
            );
        }

        ComponentTranslationRequest request = ComponentTranslationRequest.fromDocument(document, targetLanguage);
        List<OpenAIRequest.Message> messages = buildMessages(document.route(), request, providerProfile);
        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile).withStructuredOutputEnabled();
        LLM llm = new LLM(settings);
        StructuredOutputSpec responseSchema = ComponentResponseJsonSchema.forDocument(document);
        ComponentTranslationMetrics.recordValue(
                document,
                ComponentTranslationMetrics.Measurement.REQUEST_BYTES,
                request.toJson().getBytes(StandardCharsets.UTF_8).length
        );
        ComponentTranslationMetrics.recordValue(
                document,
                ComponentTranslationMetrics.Measurement.TEXT_UNITS,
                document.units().size()
        );
        long startedAt = System.nanoTime();

        CompletableFuture<ComponentTranslationResponse> future = requestValidResponse(
                document,
                messages,
                llm,
                responseSchema,
                requestContext,
                1
        ).thenApply(response -> {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "provider route={} result=completed model={} units={} elapsedMs={}",
                    document.route().wireName(),
                    providerProfile.model_id,
                    document.units().size(),
                    elapsedMillis
            );
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.SUCCESS);
            return response;
        });
        return future.whenComplete((ignored, error) -> {
            ComponentTranslationMetrics.recordNanos(
                    document,
                    ComponentTranslationMetrics.Timing.PROVIDER,
                    System.nanoTime() - startedAt
            );
            if (error != null) {
                recordResponseFailure(document, error);
            }
        });
    }

    private CompletableFuture<ComponentTranslationResponse> requestValidResponse(
            ComponentTranslationDocument document,
            List<OpenAIRequest.Message> messages,
            LLM llm,
            StructuredOutputSpec responseSchema,
            String requestContext,
            int attempt
    ) {
        return llm.getCompletion(
                messages,
                requestContext,
                (structuredOutput, fallback) -> {
                    if (structuredOutput) {
                        ComponentTranslationMetrics.record(
                                document,
                                ComponentTranslationMetrics.Outcome.PROVIDER_STRUCTURED_OUTPUT
                        );
                    }
                    if (fallback) {
                        ComponentTranslationMetrics.record(
                                document,
                                ComponentTranslationMetrics.Outcome.PROVIDER_FALLBACK_OUTPUT
                        );
                    }
                },
                responseSchema
        ).thenCompose(completion -> {
            String rawResponse = completion.content();
            try {
                if (completion.wasTruncated()) {
                    throw new ComponentJsonException(
                            ComponentJsonException.Kind.RESPONSE,
                            "Component translation response was truncated by the provider: finishReason="
                                    + completion.finishReason()
                                    + ", expected=complete JSON object"
                    );
                }
                ComponentTranslationResponse response = parser.parse(rawResponse);
                validator.validate(document, response);
                ComponentTranslationMetrics.recordValue(
                        document,
                        ComponentTranslationMetrics.Measurement.RESPONSE_BYTES,
                        rawResponse.getBytes(StandardCharsets.UTF_8).length
                );
                return CompletableFuture.completedFuture(response);
            } catch (Throwable error) {
                Throwable cause = TranslateExceptionUtils.unwrapThrowable(error);
                if (!isRetryableResponseFailure(cause)) {
                    return CompletableFuture.failedFuture(cause);
                }

                ComponentTranslationMetrics.record(
                        document,
                        ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                );
                ComponentTranslationDebugLogger.error(
                        document.route(),
                        "provider response rejected route={} attempt={}/{} finishReason={} reason={} responseBytes={} response={}",
                        document.route().wireName(),
                        attempt,
                        MAX_RESPONSE_ATTEMPTS,
                        completion.finishReason(),
                        cause.getMessage(),
                        rawResponse.getBytes(StandardCharsets.UTF_8).length,
                        ComponentTranslationDebugLogger.responsePreview(rawResponse)
                );

                if (attempt >= MAX_RESPONSE_ATTEMPTS) {
                    return CompletableFuture.failedFuture(cause);
                }

                return requestValidResponse(
                        document,
                        buildCorrectionMessages(messages, cause),
                        llm,
                        responseSchema,
                        requestContext,
                        attempt + 1
                );
            }
        });
    }

    private static boolean isRetryableResponseFailure(Throwable error) {
        if (!(error instanceof ComponentJsonException componentError)) {
            return false;
        }
        return switch (componentError.kind()) {
            case RESPONSE, VALIDATION, LIMIT -> true;
            case APPLY, CODEC, DOCUMENT -> false;
        };
    }

    static List<OpenAIRequest.Message> buildCorrectionMessages(
            List<OpenAIRequest.Message> previousMessages,
            Throwable validationError
    ) {
        List<OpenAIRequest.Message> messages = new ArrayList<>(previousMessages == null ? List.of() : previousMessages);
        String reason = validationError == null || validationError.getMessage() == null
                ? "the response did not satisfy the required JSON response contract"
                : TranslateStringUtils.truncate(validationError.getMessage(), MAX_CORRECTION_REASON_CHARS);
        messages.add(new OpenAIRequest.Message(
                "user",
                "Your previous component translation response was rejected. Reason: " + reason + "\n"
                        + "Return one complete replacement response now. Output only the required JSON object; "
                        + "do not explain the error and do not include Markdown."
        ));
        return List.copyOf(messages);
    }

    public static List<OpenAIRequest.Message> buildMessages(
            ComponentTranslationRoute route,
            ComponentTranslationRequest request,
            ApiProviderProfile providerProfile
    ) {
        String targetLanguage = request.targetLanguage();
        String defaultTemplate = PromptMessageBuilder.getDefaultPromptTemplate(route.promptRouteKey());
        String defaultPrompt = defaultTemplate.replace("{target_language}", targetLanguage);
        String resolvedPrompt = PromptMessageBuilder.applyPromptOverride(
                route.promptRouteKey(),
                defaultPrompt,
                providerProfile.system_prompt_overrides,
                targetLanguage
        );
        String withSuffix = PromptMessageBuilder.appendSystemPromptSuffix(
                resolvedPrompt,
                providerProfile.activeSystemPromptSuffix()
        );
        String protocolContract = route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH
                ? PROTOCOL_CONTRACT + COHERENT_PARAGRAPH_CONTRACT
                : PROTOCOL_CONTRACT;
        String systemPrompt = withSuffix + "\n\n" + protocolContract;
        return PromptMessageBuilder.buildMessages(
                systemPrompt,
                request.toJson(),
                providerProfile.activeSupportsSystemMessage(),
                providerProfile.model_id,
                true
        );
    }

    public record TranslationResult(
            Component component,
            ComponentTranslationResponse response
    ) {
    }

    private static void recordResponseFailure(
            ComponentTranslationDocument document,
            Throwable error
    ) {
        Throwable cause = TranslateExceptionUtils.unwrapThrowable(error);
        if (!(cause instanceof ComponentJsonException componentError)) {
            return;
        }
        String message = componentError.getMessage() == null
                ? ""
                : componentError.getMessage().toLowerCase(java.util.Locale.ROOT);
        switch (componentError.kind()) {
            case RESPONSE -> ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.RESPONSE_PARSE_FAILURE
            );
            case LIMIT -> ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.VALIDATION_LENGTH_FAILURE
            );
            case VALIDATION -> {
                ComponentTranslationMetrics.Outcome outcome;
                if (message.contains("translation ids") || message.contains("missing translation")) {
                    outcome = ComponentTranslationMetrics.Outcome.VALIDATION_ID_FAILURE;
                } else if (message.contains("protected token")) {
                    outcome = ComponentTranslationMetrics.Outcome.VALIDATION_TOKEN_FAILURE;
                } else if (message.contains("length") || message.contains("chars")) {
                    outcome = ComponentTranslationMetrics.Outcome.VALIDATION_LENGTH_FAILURE;
                } else {
                    outcome = ComponentTranslationMetrics.Outcome.VALIDATION_STRUCTURE_FAILURE;
                }
                ComponentTranslationMetrics.record(document, outcome);
            }
            case APPLY -> ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.VALIDATION_STRUCTURE_FAILURE
            );
            case CODEC -> ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.CODEC_DECODE_FAILURE
            );
            case DOCUMENT -> ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.DOCUMENT_FAILED
            );
        }
    }
}
