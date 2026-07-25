package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.cedarxuesong.translate_allinone.utils.translate.PromptMessageBuilder;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ComponentTranslationClient {
    private static final String PROTOCOL_CONTRACT = "Component translation protocol contract:\n"
            + "1) Output exactly one JSON object with only protocol and translations fields.\n"
            + "2) protocol must be taio-component-v1.\n"
            + "3) translations must contain exactly the requested ids, with string values only.\n"
            + "4) Do not add, remove, rename, or duplicate ids.\n"
            + "5) Do not output Component JSON, JSON Pointer operations, Markdown, or explanations.\n"
            + "6) Read all requested items before translating. If item context contains batch_item, previous_item, or next_item, use those neighboring source texts only to resolve terminology and sentence meaning; never merge items and never return fewer or extra ids.\n"
            + "7) Keep each item's semantic content together. Do not translate separate requested items as unrelated isolated fragments merely because their source text is short.";
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
            return CompletableFuture.failedFuture(new IllegalArgumentException("Component V1 provider request is incomplete."));
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
        long startedAt = System.nanoTime();

        return llm.getCompletion(messages, requestContext).thenApply(rawResponse -> {
            ComponentTranslationResponse response = parser.parse(rawResponse);
            validator.validate(document, response);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            int responseBytes = rawResponse.getBytes(StandardCharsets.UTF_8).length;
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "provider route={} result=completed model={} units={} responseBytes={} elapsedMs={}",
                    document.route().wireName(),
                    providerProfile.model_id,
                    document.units().size(),
                    responseBytes,
                    elapsedMillis
            );
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.SUCCESS);
            return response;
        });
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
        String systemPrompt = withSuffix + "\n\n" + PROTOCOL_CONTRACT;
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
}
