package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.llmapi.LLM;
import com.cedarxuesong.translate_allinone.utils.llmapi.LlmCompletion;
import com.cedarxuesong.translate_allinone.utils.llmapi.ProviderSettings;
import com.cedarxuesong.translate_allinone.utils.llmapi.StructuredOutputSpec;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.cedarxuesong.translate_allinone.versionapi.MinecraftComponentCodec;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ComponentTranslationClient {
    private final ComponentTranslationResponseClient responseClient;
    private final ComponentTranslationApplier applier;

    public ComponentTranslationClient() {
        this(
                new ComponentTranslationResponseClient(
                        ComponentTranslationDebugLogger::flow,
                        ComponentTranslationDebugLogger::error
                ),
                new ComponentTranslationApplier()
        );
    }

    ComponentTranslationClient(
            ComponentResponseParser parser,
            ComponentTranslationValidator validator,
            ComponentTranslationApplier applier
    ) {
        this(
                new ComponentTranslationResponseClient(
                        parser,
                        validator,
                        ComponentTranslationDebugLogger::flow,
                        ComponentTranslationDebugLogger::error
                ),
                applier
        );
    }

    ComponentTranslationClient(
            ComponentResponseParser parser,
            ComponentTranslationValidator validator,
            ComponentTranslationApplier applier,
            Function<ProviderSettings, CompletionRequester> completionRequesterFactory,
            long retryDelayMillis
    ) {
        this(
                new ComponentTranslationResponseClient(
                        parser,
                        validator,
                        settings -> {
                            CompletionRequester requester = completionRequesterFactory.apply(settings);
                            return requester::request;
                        },
                        retryDelayMillis,
                        ComponentTranslationDebugLogger::flow,
                        ComponentTranslationDebugLogger::error
                ),
                applier
        );
    }

    private ComponentTranslationClient(
            ComponentTranslationResponseClient responseClient,
            ComponentTranslationApplier applier
    ) {
        this.responseClient = responseClient;
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
        return translateResponse(document, targetLanguage, providerProfile, requestContext)
                .thenApply(response -> new TranslationResult(
                        document.units().isEmpty()
                                ? MinecraftComponentCodec.INSTANCE.decode(document.sourceJson())
                                : applier.apply(document, response),
                        response
                ));
    }

    public CompletableFuture<ComponentTranslationResponse> translateResponse(
            ComponentTranslationDocument document,
            String targetLanguage,
            ApiProviderProfile providerProfile,
            String requestContext
    ) {
        return responseClient.translate(document, targetLanguage, providerProfile, requestContext);
    }

    static boolean retriesExhausted(Throwable error) {
        return ComponentTranslationResponseClient.retriesExhausted(error);
    }

    static List<OpenAIRequest.Message> buildCorrectionMessages(
            List<OpenAIRequest.Message> previousMessages,
            Throwable validationError
    ) {
        return ComponentTranslationResponseClient.buildCorrectionMessages(previousMessages, validationError);
    }

    static List<OpenAIRequest.Message> buildCorrectionMessages(
            List<OpenAIRequest.Message> previousMessages,
            Throwable validationError,
            ComponentTranslationRoute route
    ) {
        return ComponentTranslationResponseClient.buildCorrectionMessages(
                previousMessages,
                validationError,
                route
        );
    }

    public static List<OpenAIRequest.Message> buildMessages(
            ComponentTranslationRoute route,
            ComponentTranslationRequest request,
            ApiProviderProfile providerProfile
    ) {
        return ComponentTranslationResponseClient.buildMessages(route, request, providerProfile);
    }

    public record TranslationResult(
            Component component,
            ComponentTranslationResponse response
    ) {
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
}
