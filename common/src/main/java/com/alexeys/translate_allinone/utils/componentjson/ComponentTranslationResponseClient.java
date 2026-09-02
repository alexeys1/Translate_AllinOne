package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.TranslateExceptionUtils;
import com.alexeys.translate_allinone.utils.TranslateStringUtils;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.llmapi.LLM;
import com.alexeys.translate_allinone.utils.llmapi.LlmCompletion;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.llmapi.StructuredOutputSpec;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.alexeys.translate_allinone.utils.translate.PromptMessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComponentTranslationResponseClient {
    private static final int MAX_PROVIDER_CALLS_PER_WORK = 2;
    private static final int MAX_CORRECTION_REASON_CHARS = 480;
    private static final double CORRECTION_TEMPERATURE = 0.2;
    private static final Pattern LEGACY_FORMATTING_CODE_PATTERN = Pattern.compile("\\x{00A7}[0-9A-FK-ORa-fk-or]");
    private static final Pattern LEGACY_FORMATTING_RUN_PATTERN = Pattern.compile("(?:\\x{00A7}[0-9A-FK-ORa-fk-or])+");
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("<s(\\d+)>");
    private static final Pattern INLINE_ANCHOR_PATTERN = Pattern.compile("\\{accent\\d+\\.(?:begin|end)}");
    private static final String PROTOCOL_CONTRACT = "Return exactly one JSON object in this shape: "
            + "{\"protocol\":\"taio-component-v1\",\"translations\":{\"requested-id\":\"translated text\"}}. "
            + "translations must be an object, not an array. Return exactly the requested ids, once each, with string values only. "
            + "No Component JSON, JSON Pointer operations, Markdown, explanations, or extra fields. "
            + "Read all items before translating; context is only for meaning, never for merging items.";
    private static final String COHERENT_PARAGRAPH_CONTRACT = "\n"
            + "tooltip_paragraph contains one complete paragraph with source UI wrapping removed: translate it coherently as one paragraph. "
            + "Natural target-language order may move protected tokens and semantic anchors. Return exactly one paragraph translation for its requested id. ";
    private static final String INLINE_ANCHOR_CONTRACT = "Preserve every {valueN} and {glyphN} token exactly once with its original spelling. "
            + "Keep each {accentN.begin} and {accentN.end} around the translation of the source words they enclose; the pair may move with target-language order. "
            + "Do not emit sN tags, independent slot translations, raw private-use characters, Minecraft formatting codes, unknown markers, or extra structure.";
    private static final String LEGACY_PARAGRAPH_STYLE_CONTRACT = "Natural target-language order may cross source spans. Preserve every non-style placeholder exactly. "
            + "<sN> is a semantic style: use every source id, invent none, and keep tags flat and balanced. "
            + "Do not omit, merge, or replace a source style id even when its words move in the target language. "
            + "When slotN translation ids are requested, translate each slot value and keep its {slotN} placeholder inside the matching source <sN> span in paragraph. "
            + "When a {gN} placeholder directly touches a {slotN} placeholder in the source paragraph, keep it immediately adjacent on the same side and move the pair together.";
    private static final String PROTECTED_TOKEN_CONTRACT = "\n"
            + "Each __TAIO_PROTECTED_TOKEN_N__ identifier represents one complete legacy formatting run and must appear exactly once; never rename, remove, duplicate, or merge it. "
            + "Use only these identifiers for protected formatting. Never output literal Minecraft formatting codes.";
    private static final String TOOLTIP_PROTECTED_DATA_CONTRACT = "\n"
            + "Tooltip protected data: "
            + "Preserve exactly every <sN> and </sN> style tag. "
            + "Preserve exactly every {dN}, {gN}, {valueN}, URL, command, item id, number, unit, %s/%d/%f, Minecraft formatting code, \\n, and \\t. "
            + "Style tags may move with target-language word order only when the route allows it. "
            + "Keep explicit line breaks; normal words may be reordered naturally around protected tokens.";
    private static final LogSink NO_OP_LOG_SINK = (route, message, arguments) -> {};

    private final ComponentResponseParser parser;
    private final ComponentTranslationValidator validator;
    private final Function<ProviderSettings, CompletionRequester> completionRequesterFactory;
    private final LogSink flowLogSink;
    private final LogSink errorLogSink;

    public ComponentTranslationResponseClient() {
        this(NO_OP_LOG_SINK, NO_OP_LOG_SINK);
    }

    ComponentTranslationResponseClient(LogSink flowLogSink, LogSink errorLogSink) {
        this(
                new ComponentResponseParser(),
                new ComponentTranslationValidator(),
                ComponentTranslationResponseClient::createCompletionRequester,
                flowLogSink,
                errorLogSink
        );
    }

    ComponentTranslationResponseClient(
            ComponentResponseParser parser,
            ComponentTranslationValidator validator,
            LogSink flowLogSink,
            LogSink errorLogSink
    ) {
        this(
                parser,
                validator,
                ComponentTranslationResponseClient::createCompletionRequester,
                flowLogSink,
                errorLogSink
        );
    }

    ComponentTranslationResponseClient(
            ComponentResponseParser parser,
            ComponentTranslationValidator validator,
            Function<ProviderSettings, CompletionRequester> completionRequesterFactory,
            LogSink flowLogSink,
            LogSink errorLogSink
    ) {
        this.parser = parser;
        this.validator = validator;
        this.completionRequesterFactory = completionRequesterFactory;
        this.flowLogSink = flowLogSink == null ? NO_OP_LOG_SINK : flowLogSink;
        this.errorLogSink = errorLogSink == null ? NO_OP_LOG_SINK : errorLogSink;
    }

    public CompletableFuture<ComponentTranslationResponse> translate(
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
                    new ComponentTranslationResponse(document.protocol(), Map.of())
            );
        }

        ComponentTranslationRequest request = ComponentTranslationRequest.fromDocument(document, targetLanguage);
        ProtectedTokenMask protectedTokenMask = ProtectedTokenMask.forRequest(document.route(), request);
        request = protectedTokenMask.mask(request);
        List<OpenAIRequest.Message> messages = buildMessages(document.route(), request, providerProfile);
        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile);
        CompletionRequester completionRequester = completionRequesterFactory.apply(settings);
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
                completionRequester,
                settings,
                responseSchema,
                requestContext,
                protectedTokenMask,
                MAX_PROVIDER_CALLS_PER_WORK
        ).thenApply(response -> {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            flowLogSink.log(
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
            CompletionRequester completionRequester,
            ProviderSettings settings,
            StructuredOutputSpec responseSchema,
            String requestContext,
            ProtectedTokenMask protectedTokenMask,
            int attemptsRemaining
    ) {
        return completionRequester.request(
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
            String providerResponse = completion.content();
            String rawResponse = providerResponse;
            try {
                protectedTokenMask.rejectUnexpectedRawFormattingCodes(providerResponse);
                rawResponse = protectedTokenMask.restore(providerResponse);
                if (completion.wasTruncated()) {
                    throw new ComponentJsonException(
                            ComponentJsonException.Kind.RESPONSE,
                            "Component translation response was truncated by the provider: finishReason="
                                    + completion.finishReason()
                                    + ", expected=complete JSON object"
                    );
                }
                ComponentTranslationResponse response = parser.parse(rawResponse);
                if (!isRuntimeBatchDocument(document)) {
                    validator.validate(document, response);
                }
                ComponentTranslationMetrics.recordValue(
                        document,
                        ComponentTranslationMetrics.Measurement.RESPONSE_BYTES,
                        rawResponse.getBytes(StandardCharsets.UTF_8).length
                );
                return CompletableFuture.completedFuture(response);
            } catch (Throwable error) {
                Throwable cause = TranslateExceptionUtils.unwrapThrowable(error);
                ComponentTranslationMetrics.record(
                        document,
                        ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                );
                errorLogSink.log(
                        document.route(),
                        "provider response rejected route={} attempt={}/{} finishReason={} reason={} responseBytes={} response={}",
                        document.route().wireName(),
                        MAX_PROVIDER_CALLS_PER_WORK - attemptsRemaining + 1,
                        MAX_PROVIDER_CALLS_PER_WORK,
                        completion.finishReason(),
                        cause.getMessage(),
                        rawResponse.getBytes(StandardCharsets.UTF_8).length,
                        responsePreview(rawResponse)
                );
                if (!isRetryableResponseFailure(cause) || attemptsRemaining <= 1) {
                    return CompletableFuture.failedFuture(cause);
                }
                flowLogSink.log(
                        document.route(),
                        "provider route={} result=correction_retry attempt={}/{} reason={}",
                        document.route().wireName(),
                        MAX_PROVIDER_CALLS_PER_WORK - attemptsRemaining + 1,
                        MAX_PROVIDER_CALLS_PER_WORK,
                        cause.getMessage()
                );
                ProviderSettings correctionSettings = withCorrectionTemperature(settings);
                CompletionRequester correctionRequester = completionRequesterFactory.apply(correctionSettings);
                return requestValidResponse(
                        document,
                        buildCorrectionMessages(messages, cause, document.route()),
                        correctionRequester,
                        correctionSettings,
                        responseSchema,
                        requestContext,
                        protectedTokenMask,
                        attemptsRemaining - 1
                );
            }
        });
    }

    static List<OpenAIRequest.Message> buildCorrectionMessages(
            List<OpenAIRequest.Message> previousMessages,
            Throwable validationError
    ) {
        return buildCorrectionMessages(previousMessages, validationError, null);
    }

    static List<OpenAIRequest.Message> buildCorrectionMessages(
            List<OpenAIRequest.Message> previousMessages,
            Throwable validationError,
            ComponentTranslationRoute route
    ) {
        List<OpenAIRequest.Message> messages = new ArrayList<>(previousMessages == null ? List.of() : previousMessages);
        String reason = validationError == null || validationError.getMessage() == null
                ? "the response did not satisfy the required JSON response contract"
                : TranslateStringUtils.truncate(validationError.getMessage(), MAX_CORRECTION_REASON_CHARS);
        reason = LEGACY_FORMATTING_CODE_PATTERN.matcher(reason).replaceAll("[formatting-code]");
        String paragraphCorrection = route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH
                ? "For tooltip_paragraph, restore every required hard token exactly and return one complete coherent paragraph."
                : "";
        messages.add(new OpenAIRequest.Message(
                "user",
                "Your previous component translation response was rejected. Reason: " + reason + "\n"
                        + paragraphCorrection
                        + (paragraphCorrection.isEmpty() ? "" : "\n")
                        + "Return one complete replacement response now. Output only the required JSON object; "
                        + "do not explain the error and do not include Markdown."
        ));
        return List.copyOf(messages);
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

    private static ProviderSettings withCorrectionTemperature(ProviderSettings settings) {
        if (settings == null) {
            return settings;
        }
        double temperature = Math.min(currentTemperature(settings), CORRECTION_TEMPERATURE);
        if (settings.openAISettings() != null) {
            ProviderSettings.OpenAISettings openAi = settings.openAISettings();
            return ProviderSettings.fromOpenAI(new ProviderSettings.OpenAISettings(
                    openAi.baseUrl(),
                    openAi.apiKey(),
                    openAi.modelId(),
                    temperature,
                    openAi.customParameters(),
                    openAi.providerType()
            ));
        }
        if (settings.ollamaSettings() != null) {
            ProviderSettings.OllamaSettings ollama = settings.ollamaSettings();
            Map<String, Object> options = new LinkedHashMap<>(ollama.options() == null ? Map.of() : ollama.options());
            options.put("temperature", temperature);
            return ProviderSettings.fromOllama(new ProviderSettings.OllamaSettings(
                    ollama.baseUrl(),
                    ollama.apiKey(),
                    ollama.modelId(),
                    ollama.keepAlive(),
                    options
            ));
        }
        return settings;
    }

    private static double currentTemperature(ProviderSettings settings) {
        if (settings != null && settings.openAISettings() != null) {
            return settings.openAISettings().temperature();
        }
        if (settings != null && settings.ollamaSettings() != null) {
            Object value = settings.ollamaSettings().options() == null
                    ? null
                    : settings.ollamaSettings().options().get("temperature");
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return CORRECTION_TEMPERATURE;
    }

    private static CompletionRequester createCompletionRequester(ProviderSettings settings) {
        LLM llm = new LLM(settings);
        return llm::getCompletion;
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
        String protocolContract = PROTOCOL_CONTRACT;
        if (isTooltipRoute(route)) {
            protocolContract += TOOLTIP_PROTECTED_DATA_CONTRACT;
        }
        if (isTooltipRoute(route) && containsProtectedTokenIdentifier(request)) {
            protocolContract += PROTECTED_TOKEN_CONTRACT;
        }
        if (route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            protocolContract += buildCoherentParagraphContract(request);
        }
        String systemPrompt = withSuffix + "\n\n" + protocolContract;
        return PromptMessageBuilder.buildMessages(
                systemPrompt,
                request.toJson(),
                providerProfile.activeSupportsSystemMessage(),
                providerProfile.model_id,
                true
        );
    }

    private static String buildCoherentParagraphContract(ComponentTranslationRequest request) {
        if (containsInlineAnchor(request)) {
            return COHERENT_PARAGRAPH_CONTRACT + INLINE_ANCHOR_CONTRACT;
        }
        return COHERENT_PARAGRAPH_CONTRACT
                + LEGACY_PARAGRAPH_STYLE_CONTRACT
                + " Required tooltip paragraph style ids for this request are "
                + paragraphStyleIds(request)
                + ". Include at least one balanced opening and closing tag pair for every listed id.";
    }

    private static boolean containsInlineAnchor(ComponentTranslationRequest request) {
        if (request == null || request.items() == null) {
            return false;
        }
        return request.items().stream()
                .filter(java.util.Objects::nonNull)
                .map(ComponentTranslationRequest.Item::text)
                .filter(java.util.Objects::nonNull)
                .anyMatch(text -> INLINE_ANCHOR_PATTERN.matcher(text).find());
    }

    private static String paragraphStyleIds(ComponentTranslationRequest request) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (request == null || request.items() == null) {
            return ids.toString();
        }
        for (ComponentTranslationRequest.Item item : request.items()) {
            if (item == null || item.text() == null) {
                continue;
            }
            Matcher matcher = STYLE_TAG_PATTERN.matcher(item.text());
            while (matcher.find()) {
                ids.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return ids.toString();
    }

    private static boolean containsProtectedTokenIdentifier(ComponentTranslationRequest request) {
        return request.items().stream()
                .anyMatch(item -> item.text().contains("__TAIO_PROTECTED_TOKEN_"));
    }

    private static boolean isRuntimeBatchDocument(ComponentTranslationDocument document) {
        return document != null
                && document.semanticSettings().containsKey("batch_size");
    }

    private static boolean isTooltipRoute(ComponentTranslationRoute route) {
        return route == ComponentTranslationRoute.TOOLTIP_LINE
                || route == ComponentTranslationRoute.TOOLTIP_STRUCTURED
                || route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH;
    }

    @FunctionalInterface
    interface CompletionRequester {
        CompletableFuture<LlmCompletion> request(
                List<OpenAIRequest.Message> messages,
                String requestContext,
                LLM.CompletionObserver observer,
                StructuredOutputSpec responseSchema
        );
    }

    @FunctionalInterface
    interface LogSink {
        void log(ComponentTranslationRoute route, String message, Object... arguments);
    }

    static final class ProtectedTokenMask {
        private static final ProtectedTokenMask NONE = new ProtectedTokenMask(Map.of());

        private final Map<String, String> replacements;

        private ProtectedTokenMask(Map<String, String> replacements) {
            this.replacements = Map.copyOf(replacements);
        }

        static ProtectedTokenMask forRequest(
                ComponentTranslationRoute route,
                ComponentTranslationRequest request
        ) {
            if (!ComponentTranslationResponseClient.isTooltipRoute(route)) {
                return NONE;
            }

            LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
            int[] index = {0};
            for (ComponentTranslationRequest.Item item : request.items()) {
                Matcher matcher = LEGACY_FORMATTING_RUN_PATTERN.matcher(item.text());
                while (matcher.find()) {
                    replacements.put(
                            protectedTokenIdentifier(index[0]++),
                            matcher.group()
                    );
                }
            }
            return replacements.isEmpty() ? NONE : new ProtectedTokenMask(replacements);
        }

        ComponentTranslationRequest mask(ComponentTranslationRequest request) {
            if (replacements.isEmpty()) {
                return request;
            }

            List<ComponentTranslationRequest.Item> items = new ArrayList<>(request.items().size());
            int[] index = {0};
            for (ComponentTranslationRequest.Item item : request.items()) {
                StringBuilder maskedText = new StringBuilder(item.text().length());
                int previousEnd = 0;
                Matcher matcher = LEGACY_FORMATTING_RUN_PATTERN.matcher(item.text());
                while (matcher.find()) {
                    maskedText.append(item.text(), previousEnd, matcher.start());
                    maskedText.append(protectedTokenIdentifier(index[0]++));
                    previousEnd = matcher.end();
                }
                maskedText.append(item.text(), previousEnd, item.text().length());
                items.add(new ComponentTranslationRequest.Item(item.id(), maskedText.toString(), item.context()));
            }
            return new ComponentTranslationRequest(request.protocol(), request.targetLanguage(), items);
        }

        String restore(String response) {
            String restored = response;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                restored = restored.replace(replacement.getKey(), replacement.getValue());
            }
            return restored;
        }

        void rejectUnexpectedRawFormattingCodes(String response) {
            if (replacements.isEmpty() || response == null) {
                return;
            }
            Matcher matcher = LEGACY_FORMATTING_CODE_PATTERN.matcher(response);
            if (matcher.find()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.VALIDATION,
                        "Provider response contains an unmasked Minecraft formatting code: " + matcher.group()
                );
            }
        }

        private static String protectedTokenIdentifier(int index) {
            return "__TAIO_PROTECTED_TOKEN_" + index + "__";
        }
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

    private static String responsePreview(String response) {
        if (response == null) {
            return "<null>";
        }
        String escaped = response
                .replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
        int maxChars = 1_024;
        if (escaped.length() <= maxChars) {
            return escaped;
        }
        return escaped.substring(0, maxChars) + "...(+" + (escaped.length() - maxChars) + " chars)";
    }
}
