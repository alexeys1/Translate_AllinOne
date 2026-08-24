package com.alexeys.translate_allinone.utils.llmapi;

import com.alexeys.translate_allinone.utils.TranslateExceptionUtils;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.llmapi.ollama.OllamaChatRequest;
import com.alexeys.translate_allinone.utils.llmapi.ollama.OllamaChatResponse;
import com.alexeys.translate_allinone.utils.llmapi.ollama.OllamaClient;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIChatCompletion;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIClient;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIResponsesRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class LLM {
    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone");
    private static final ConcurrentMap<String, OutputFormat> COMPONENT_OUTPUT_CAPABILITIES = new ConcurrentHashMap<>();

    public static void configureRequestTextStatsLogging(BooleanSupplier enabledSupplier) {
        LlmRequestDebugLogger.configureRequestTextStatsLogging(enabledSupplier);
    }

    private final OpenAIClient openAIClient;
    private final OllamaClient ollamaClient;
    private final ProviderSettings settings;

    public LLM(ProviderSettings settings) {
        this.settings = settings;
        if (settings.openAISettings() != null) {
            this.openAIClient = new OpenAIClient(settings.openAISettings());
            this.ollamaClient = null;
        } else if (settings.ollamaSettings() != null) {
            this.ollamaClient = new OllamaClient(settings.ollamaSettings());
            this.openAIClient = null;
        } else {
            this.openAIClient = null;
            this.ollamaClient = null;
            throw new IllegalStateException("LLM服务提供商未配置或配置不正确。");
        }
    }

    /**
     * 发送非流式请求，并异步返回完整结果。
     * @param messages 消息列表 (使用OpenAI的Message结构，因为它们是兼容的)
     * @return 包含完整响应字符串的 CompletableFuture
     */
    public CompletableFuture<String> getCompletion(List<OpenAIRequest.Message> messages) {
        return getCompletion(messages, null);
    }

    public CompletableFuture<String> getCompletion(List<OpenAIRequest.Message> messages, String requestContext) {
        return getCompletion(messages, requestContext, null);
    }

    public CompletableFuture<String> getCompletion(
            List<OpenAIRequest.Message> messages,
            String requestContext,
            CompletionObserver observer
    ) {
        long requestEpoch = LlmRequestLifecycle.currentEpoch();
        if (openAIClient != null) {
            if (useResponsesApi()) {
                CompletionSupplier primaryCall = instrumentCompletionSupplier(
                        "openai_responses",
                        requestContext,
                        requestEpoch,
                        messages,
                        false,
                        false,
                        "primary",
                        () -> openAIClient.getResponsesCompletion(
                                buildOpenAIResponsesRequest(messages, false)
                        )
                );
                return invokeCompletionSupplier(primaryCall);
            }

            CompletionSupplier primaryCall = instrumentCompletionSupplier(
                    "openai_chat",
                    requestContext,
                    requestEpoch,
                    messages,
                    false,
                    false,
                    "primary",
                    () -> LlmRequestLifecycle.map(
                            openAIClient.getChatCompletion(buildOpenAIRequest(messages, false)),
                            response -> response.choices.get(0).message.content
                    )
            );
            return invokeCompletionSupplier(primaryCall);
        }

        if (ollamaClient != null) {
            CompletionSupplier primaryCall = instrumentCompletionSupplier(
                    "ollama_chat",
                    requestContext,
                    requestEpoch,
                    messages,
                    false,
                    false,
                    "primary",
                    () -> LlmRequestLifecycle.map(
                            ollamaClient.getChatCompletion(buildOllamaRequest(messages, false)),
                            response -> response.message.content
                    )
            );
            return invokeCompletionSupplier(primaryCall);
        }

        return CompletableFuture.failedFuture(new IllegalStateException("当前供应商不支持聊天消息补全接口。"));
    }

    /**
     * 发送流式请求，并返回一个包含文本块的流。
     * <p>
     * <b>重要:</b> 对返回的流进行操作是一个阻塞操作。
     * 调用者必须负责在单独的线程中消费此流，以避免阻塞主线程。
     *
     * @param messages 消息列表
     * @return 包含响应文本块的 Stream
     */
    public CompletableFuture<LlmCompletion> getCompletion(
            List<OpenAIRequest.Message> messages,
            String requestContext,
            CompletionObserver observer,
            StructuredOutputSpec schema
    ) {
        if (schema == null) {
            return LlmRequestLifecycle.map(
                    getCompletion(messages, requestContext, observer),
                    content -> new LlmCompletion(content, "")
            );
        }
        long requestEpoch = LlmRequestLifecycle.currentEpoch();
        String capabilityKey = componentOutputCapabilityKey();

        if (openAIClient != null) {
            if (useResponsesApi()) {
                DetailedCompletionSupplier schemaCall = instrumentDetailedCompletionSupplier(
                        "openai_responses", requestContext, requestEpoch, messages, true, "component_json_schema",
                        () -> openAIClient.getResponsesCompletionDetail(
                                buildOpenAIResponsesRequest(messages, false, OutputFormat.JSON_SCHEMA, schema)
                        )
                );
                DetailedCompletionSupplier jsonObjectCall = instrumentDetailedCompletionSupplier(
                        "openai_responses", requestContext, requestEpoch, messages, true, "component_json_object_fallback",
                        () -> openAIClient.getResponsesCompletionDetail(
                                buildOpenAIResponsesRequest(messages, false, OutputFormat.JSON_OBJECT, null)
                        )
                );
                DetailedCompletionSupplier promptOnlyCall = instrumentDetailedCompletionSupplier(
                        "openai_responses", requestContext, requestEpoch, messages, false, "component_prompt_fallback",
                        () -> openAIClient.getResponsesCompletionDetail(
                                buildOpenAIResponsesRequest(messages, false, OutputFormat.NONE, null)
                        )
                );
                return withComponentStructuredOutputFallback(
                        schemaCall,
                        jsonObjectCall,
                        promptOnlyCall,
                        "OpenAI Responses",
                        observer,
                        capabilityKey
                );
            }

            DetailedCompletionSupplier schemaCall = instrumentDetailedCompletionSupplier(
                    "openai_chat", requestContext, requestEpoch, messages, true, "component_json_schema",
                    () -> LlmRequestLifecycle.map(
                            openAIClient.getChatCompletion(buildOpenAIRequest(messages, false, OutputFormat.JSON_SCHEMA, schema)),
                            LLM::toDetailedChatCompletion
                    )
            );
            DetailedCompletionSupplier jsonObjectCall = instrumentDetailedCompletionSupplier(
                    "openai_chat", requestContext, requestEpoch, messages, true, "component_json_object_fallback",
                    () -> LlmRequestLifecycle.map(
                            openAIClient.getChatCompletion(buildOpenAIRequest(messages, false, OutputFormat.JSON_OBJECT, null)),
                            LLM::toDetailedChatCompletion
                    )
            );
            DetailedCompletionSupplier promptOnlyCall = instrumentDetailedCompletionSupplier(
                    "openai_chat", requestContext, requestEpoch, messages, false, "component_prompt_fallback",
                    () -> LlmRequestLifecycle.map(
                            openAIClient.getChatCompletion(buildOpenAIRequest(messages, false, OutputFormat.NONE, null)),
                            LLM::toDetailedChatCompletion
                    )
            );
            return withComponentStructuredOutputFallback(
                    schemaCall,
                    jsonObjectCall,
                    promptOnlyCall,
                    "OpenAI",
                    observer,
                    capabilityKey
            );
        }

        if (ollamaClient != null) {
            DetailedCompletionSupplier schemaCall = instrumentDetailedCompletionSupplier(
                    "ollama_chat", requestContext, requestEpoch, messages, true, "component_json_schema",
                    () -> LlmRequestLifecycle.map(
                            ollamaClient.getChatCompletion(buildOllamaRequest(messages, false, OutputFormat.JSON_SCHEMA, schema)),
                            LLM::toDetailedOllamaCompletion
                    )
            );
            DetailedCompletionSupplier jsonObjectCall = instrumentDetailedCompletionSupplier(
                    "ollama_chat", requestContext, requestEpoch, messages, true, "component_json_object_fallback",
                    () -> LlmRequestLifecycle.map(
                            ollamaClient.getChatCompletion(buildOllamaRequest(messages, false, OutputFormat.JSON_OBJECT, null)),
                            LLM::toDetailedOllamaCompletion
                    )
            );
            DetailedCompletionSupplier promptOnlyCall = instrumentDetailedCompletionSupplier(
                    "ollama_chat", requestContext, requestEpoch, messages, false, "component_prompt_fallback",
                    () -> LlmRequestLifecycle.map(
                            ollamaClient.getChatCompletion(buildOllamaRequest(messages, false, OutputFormat.NONE, null)),
                            LLM::toDetailedOllamaCompletion
                    )
            );
            return withComponentStructuredOutputFallback(
                    schemaCall,
                    jsonObjectCall,
                    promptOnlyCall,
                    "Ollama",
                    observer,
                    capabilityKey
            );
        }

        return CompletableFuture.failedFuture(new IllegalStateException("No provider is configured."));
    }

    public Stream<String> getStreamingCompletion(List<OpenAIRequest.Message> messages) {
        return getStreamingCompletion(messages, null);
    }

    public Stream<String> getStreamingCompletion(List<OpenAIRequest.Message> messages, String requestContext) {
        long requestEpoch = LlmRequestLifecycle.currentEpoch();
        if (openAIClient != null) {
            if (useResponsesApi()) {
                return executeStreamingSupplier(
                        "openai_responses",
                        requestContext,
                        requestEpoch,
                        messages,
                        true,
                        false,
                        "primary",
                        () -> openAIClient.getStreamingResponsesCompletion(
                                buildOpenAIResponsesRequest(messages, true)
                        )
                );
            }

            return executeStreamingSupplier(
                    "openai_chat",
                    requestContext,
                    requestEpoch,
                    messages,
                    true,
                    false,
                    "primary",
                    () -> openAIClient.getStreamingChatCompletion(
                            buildOpenAIRequest(messages, true)
                    ).map(chunk -> chunk.choices.get(0).delta.content)
            );
        }

        if (ollamaClient != null) {
            return executeStreamingSupplier(
                    "ollama_chat",
                    requestContext,
                    requestEpoch,
                    messages,
                    true,
                    false,
                    "primary",
                    () -> ollamaClient.getStreamingChatCompletion(
                            buildOllamaRequest(messages, true)
                    ).map(chunk -> chunk.message.content)
            );
        }

        throw new IllegalStateException("当前供应商不支持流式聊天补全接口。");
    }

    public boolean supportsChatCompletion() {
        return openAIClient != null || ollamaClient != null;
    }

    private OpenAIRequest buildOpenAIRequest(List<OpenAIRequest.Message> messages, boolean stream) {
        return buildOpenAIRequest(messages, stream, OutputFormat.NONE, null);
    }

    private OpenAIRequest buildOpenAIRequest(
            List<OpenAIRequest.Message> messages,
            boolean stream,
            OutputFormat outputFormat,
            StructuredOutputSpec schema
    ) {
        OpenAIRequest.ResponseFormat responseFormat = switch (outputFormat) {
            case NONE -> null;
            case JSON_OBJECT -> new OpenAIRequest.ResponseFormat("json_object");
            case JSON_SCHEMA -> new OpenAIRequest.ResponseFormat(schema.name(), schema.schema());
        };
        return new OpenAIRequest(
                settings.openAISettings().modelId(),
                messages,
                settings.openAISettings().temperature(),
                stream,
                responseFormat
        );
    }

    private OpenAIResponsesRequest buildOpenAIResponsesRequest(
            List<OpenAIRequest.Message> messages,
            boolean stream
    ) {
        return buildOpenAIResponsesRequest(messages, stream, OutputFormat.NONE, null);
    }

    private OpenAIResponsesRequest buildOpenAIResponsesRequest(
            List<OpenAIRequest.Message> messages,
            boolean stream,
            OutputFormat outputFormat,
            StructuredOutputSpec schema
    ) {
        OpenAIResponsesRequest.TextConfig textConfig = switch (outputFormat) {
            case NONE -> null;
            case JSON_OBJECT -> new OpenAIResponsesRequest.TextConfig(new OpenAIResponsesRequest.Format("json_object"));
            case JSON_SCHEMA -> new OpenAIResponsesRequest.TextConfig(
                    new OpenAIResponsesRequest.Format(schema.name(), schema.schema())
            );
        };
        return OpenAIResponsesRequest.fromChatMessages(
                settings.openAISettings().modelId(),
                messages,
                settings.openAISettings().temperature(),
                stream,
                textConfig
        );
    }

    private OllamaChatRequest buildOllamaRequest(List<OpenAIRequest.Message> messages, boolean stream) {
        return buildOllamaRequest(messages, stream, OutputFormat.NONE, null);
    }

    private OllamaChatRequest buildOllamaRequest(
            List<OpenAIRequest.Message> messages,
            boolean stream,
            OutputFormat outputFormat,
            StructuredOutputSpec schema
    ) {
        Object format = switch (outputFormat) {
            case NONE -> null;
            case JSON_OBJECT -> "json";
            case JSON_SCHEMA -> schema.schema();
        };
        return new OllamaChatRequest(
                settings.ollamaSettings().modelId(),
                messages,
                stream,
                settings.ollamaSettings().keepAlive(),
                settings.ollamaSettings().options(),
                format
        );
    }

    private CompletableFuture<LlmCompletion> withComponentStructuredOutputFallback(
            DetailedCompletionSupplier schemaSupplier,
            DetailedCompletionSupplier jsonObjectSupplier,
            DetailedCompletionSupplier promptOnlySupplier,
            String providerName,
            CompletionObserver observer,
            String capabilityKey
    ) {
        OutputFormat knownCapability = COMPONENT_OUTPUT_CAPABILITIES.get(capabilityKey);
        if (knownCapability == OutputFormat.NONE) {
            return attemptComponentOutputFormat(
                    promptOnlySupplier,
                    "prompt-only JSON",
                    false,
                    true,
                    providerName,
                    observer,
                    null,
                    null
            );
        }
        if (knownCapability == OutputFormat.JSON_OBJECT) {
            return attemptComponentOutputFormat(
                    jsonObjectSupplier,
                    "JSON object",
                    true,
                    true,
                    providerName,
                    observer,
                    () -> COMPONENT_OUTPUT_CAPABILITIES.put(capabilityKey, OutputFormat.NONE),
                    () -> attemptComponentOutputFormat(
                            promptOnlySupplier,
                            "prompt-only JSON",
                            false,
                            true,
                            providerName,
                            observer,
                            null,
                            null
                    )
            );
        }
        return attemptComponentOutputFormat(
                schemaSupplier,
                "JSON Schema",
                true,
                false,
                providerName,
                observer,
                () -> COMPONENT_OUTPUT_CAPABILITIES.put(capabilityKey, OutputFormat.JSON_OBJECT),
                () -> attemptComponentOutputFormat(
                        jsonObjectSupplier,
                        "JSON object",
                        true,
                        true,
                        providerName,
                        observer,
                        () -> COMPONENT_OUTPUT_CAPABILITIES.put(capabilityKey, OutputFormat.NONE),
                        () -> attemptComponentOutputFormat(
                                promptOnlySupplier,
                                "prompt-only JSON",
                                false,
                                true,
                                providerName,
                                observer,
                                null,
                                null
                        )
                )
        );
    }

    private CompletableFuture<LlmCompletion> attemptComponentOutputFormat(
            DetailedCompletionSupplier supplier,
            String formatName,
            boolean structuredOutput,
            boolean fallbackDispatch,
            String providerName,
            CompletionObserver observer,
            Runnable onUnsupported,
            Supplier<CompletableFuture<LlmCompletion>> fallback
    ) {
        notifyDispatch(observer, structuredOutput, fallbackDispatch);
        CompletableFuture<LlmCompletion> current = invokeDetailedSupplier(supplier);
        if (fallback == null) {
            return current;
        }

        CompletableFuture<LlmCompletion> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<LlmCompletion>> activeRequest = new AtomicReference<>(current);
        propagateCancellation(result, activeRequest);
        current.whenComplete((completion, throwable) -> {
            if (result.isDone()) {
                return;
            }
            if (throwable == null) {
                result.complete(completion);
                return;
            }

            Throwable rootCause = TranslateExceptionUtils.unwrapThrowable(throwable);
            if (!isStructuredOutputUnsupported(rootCause)) {
                result.completeExceptionally(rootCause);
                return;
            }

            LOGGER.warn(
                    "{} {} output unsupported, trying the next component response format: {}",
                    providerName,
                    formatName,
                    rootCause.getMessage()
            );
            if (onUnsupported != null) {
                onUnsupported.run();
            }
            try {
                CompletableFuture<LlmCompletion> fallbackRequest = fallback.get();
                activeRequest.set(fallbackRequest);
                if (result.isCancelled()) {
                    fallbackRequest.cancel(true);
                    return;
                }
                fallbackRequest.whenComplete((fallbackCompletion, fallbackError) -> {
                    if (fallbackError == null) {
                        result.complete(fallbackCompletion);
                    } else {
                        result.completeExceptionally(TranslateExceptionUtils.unwrapThrowable(fallbackError));
                    }
                });
            } catch (Throwable fallbackStartError) {
                result.completeExceptionally(TranslateExceptionUtils.unwrapThrowable(fallbackStartError));
            }
        });
        return result;
    }

    private CompletableFuture<LlmCompletion> invokeDetailedSupplier(DetailedCompletionSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(TranslateExceptionUtils.unwrapThrowable(error));
        }
    }

    private String componentOutputCapabilityKey() {
        if (openAIClient != null) {
            ProviderSettings.OpenAISettings openAi = settings.openAISettings();
            return "openai:"
                    + openAi.providerType()
                    + ':' + String.valueOf(openAi.baseUrl())
                    + ':' + openAi.modelId();
        }
        ProviderSettings.OllamaSettings ollama = settings.ollamaSettings();
        return "ollama:"
                + String.valueOf(ollama.baseUrl())
                + ':' + ollama.modelId();
    }

    private static LlmCompletion toDetailedChatCompletion(OpenAIChatCompletion response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new LLMApiException("Chat Completions API returned no choices");
        }
        OpenAIChatCompletion.Choice choice = response.choices.get(0);
        String content = choice == null || choice.message == null ? "" : choice.message.content;
        String finishReason = choice == null ? "" : choice.finish_reason;
        return new LlmCompletion(content, finishReason);
    }

    private static LlmCompletion toDetailedOllamaCompletion(OllamaChatResponse response) {
        if (response == null || response.message == null) {
            throw new LLMApiException("Ollama API returned no completion message");
        }
        return new LlmCompletion(response.message.content, response.done_reason);
    }

    private static void notifyDispatch(
            CompletionObserver observer,
            boolean structuredOutput,
            boolean fallback
    ) {
        if (observer != null) {
            observer.onDispatch(structuredOutput, fallback);
        }
    }

    private boolean isStructuredOutputUnsupported(Throwable throwable) {
        if (!(throwable instanceof LLMApiException) || throwable.getMessage() == null) {
            return false;
        }

        String message = throwable.getMessage().toLowerCase(Locale.ROOT);
        if (!message.startsWith("api returned error:")) {
            return false;
        }
        if (message.contains("response_format") || message.contains("json_schema") || message.contains("json_object")) {
            return true;
        }

        if (message.contains("text.format") || message.contains("text format")) {
            return true;
        }

        if (message.contains("unknown field") && message.contains("format")) {
            return true;
        }

        return (message.contains("format") || message.contains("structured"))
                && (message.contains("unsupported") || message.contains("not support") || message.contains("invalid"));
    }

    private boolean useResponsesApi() {
        return settings.openAISettings().providerType() == ApiProviderType.OPENAI_RESPONSE;
    }

    private CompletableFuture<String> invokeCompletionSupplier(CompletionSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(TranslateExceptionUtils.unwrapThrowable(e));
        }
    }

    private CompletionSupplier instrumentCompletionSupplier(
            String api,
            String requestContext,
            long requestEpoch,
            List<OpenAIRequest.Message> messages,
            boolean streaming,
            boolean structuredOutputEnabled,
            String dispatchReason,
            CompletionSupplier delegate
    ) {
        AtomicInteger sendAttemptCounter = new AtomicInteger(0);
        return () -> LlmRequestLifecycle.invoke(requestEpoch, () -> {
            int sendAttempt = sendAttemptCounter.incrementAndGet();
            LlmRequestDebugLogger.logIfEnabled(
                    api,
                    settings,
                    messages,
                    streaming,
                    structuredOutputEnabled,
                    dispatchReason,
                    sendAttempt,
                    requestContext
            );
            return delegate.get();
        });
    }

    private DetailedCompletionSupplier instrumentDetailedCompletionSupplier(
            String api,
            String requestContext,
            long requestEpoch,
            List<OpenAIRequest.Message> messages,
            boolean structuredOutputEnabled,
            String dispatchReason,
            DetailedCompletionSupplier delegate
    ) {
        AtomicInteger sendAttemptCounter = new AtomicInteger(0);
        return () -> LlmRequestLifecycle.invoke(requestEpoch, () -> {
            int sendAttempt = sendAttemptCounter.incrementAndGet();
            LlmRequestDebugLogger.logIfEnabled(
                    api,
                    settings,
                    messages,
                    false,
                    structuredOutputEnabled,
                    dispatchReason,
                    sendAttempt,
                    requestContext
            );
            return delegate.get();
        });
    }

    private Stream<String> executeStreamingSupplier(
            String api,
            String requestContext,
            long requestEpoch,
            List<OpenAIRequest.Message> messages,
            boolean streaming,
            boolean structuredOutputEnabled,
            String dispatchReason,
            StreamingSupplier delegate
    ) {
        Stream<String> stream = LlmRequestLifecycle.invoke(requestEpoch, () -> {
            LlmRequestDebugLogger.logIfEnabled(
                    api,
                    settings,
                    messages,
                    streaming,
                    structuredOutputEnabled,
                    dispatchReason,
                    1,
                    requestContext
            );
            return delegate.get();
        });
        return LlmRequestLifecycle.guard(stream);
    }

    private static <T> void propagateCancellation(
            CompletableFuture<T> result,
            AtomicReference<CompletableFuture<T>> activeRequest
    ) {
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                CompletableFuture<T> request = activeRequest.get();
                if (request != null) {
                    request.cancel(true);
                }
            }
        });
    }

    @FunctionalInterface
    private interface CompletionSupplier {
        CompletableFuture<String> get();
    }

    @FunctionalInterface
    private interface DetailedCompletionSupplier {
        CompletableFuture<LlmCompletion> get();
    }

    @FunctionalInterface
    public interface CompletionObserver {
        void onDispatch(boolean structuredOutput, boolean fallback);
    }

    @FunctionalInterface
    private interface StreamingSupplier {
        Stream<String> get();
    }

    private enum OutputFormat {
        NONE,
        JSON_OBJECT,
        JSON_SCHEMA
    }
}
