package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.cedarxuesong.translate_allinone.utils.translate.PromptMessageBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ComponentTranslationClient {
    private static final String PROTOCOL_CONTRACT = "Component translation protocol contract:\n"
            + "1) Output exactly one JSON object with only protocol and translations fields.\n"
            + "2) protocol must be taio-component-v1.\n"
            + "3) translations must contain exactly the requested ids, with string values only.\n"
            + "4) Do not add, remove, rename, or duplicate ids.\n"
            + "5) Do not output Component JSON, JSON Pointer operations, Markdown, or explanations.";

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
        if (document == null || targetLanguage == null || targetLanguage.isBlank() || providerProfile == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Component V1 provider request is incomplete."));
        }
        if (document.units().isEmpty()) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.NO_TEXT);
            return CompletableFuture.completedFuture(ComponentJsonCodec.decode(document.sourceJson()));
        }

        ComponentTranslationRequest request = ComponentTranslationRequest.fromDocument(document, targetLanguage);
        List<OpenAIRequest.Message> messages = buildMessages(document.route(), request, providerProfile);
        ProviderSettings settings = ProviderSettings.fromProviderProfile(providerProfile).withStructuredOutputEnabled();
        LLM llm = new LLM(settings);

        return llm.getCompletion(messages, requestContext).thenApply(rawResponse -> {
            ComponentTranslationResponse response = parser.parse(rawResponse);
            validator.validate(document, response);
            Component translated = applier.apply(document, response);
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.SUCCESS);
            return translated;
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
}
