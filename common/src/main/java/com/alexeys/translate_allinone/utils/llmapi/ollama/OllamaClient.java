package com.alexeys.translate_allinone.utils.llmapi.ollama;

import com.alexeys.translate_allinone.utils.llmapi.LLMApiException;
import com.alexeys.translate_allinone.utils.llmapi.LlmPayloadJsonSupport;
import com.alexeys.translate_allinone.utils.llmapi.LlmRequestLifecycle;
import com.alexeys.translate_allinone.utils.llmapi.ProviderSettings;
import com.alexeys.translate_allinone.utils.llmapi.TokenUsageLedger;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIError;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OllamaClient {

    private static final Gson GSON = LlmPayloadJsonSupport.gson();
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ProviderSettings.OllamaSettings settings;

    public OllamaClient(ProviderSettings.OllamaSettings settings) {
        this.settings = settings;
    }

    public CompletableFuture<OllamaChatResponse> getChatCompletion(OllamaChatRequest request) {
        request.stream = false;
        String requestBody = GSON.toJson(request);

        HttpRequest httpRequest = buildRequest(requestBody);

        return LlmRequestLifecycle.track(
                SHARED_HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()),
                response -> {
                    if (response.statusCode() != 200) {
                        // Ollama的错误格式可能不同，但我们尝试用OpenAI的格式解析
                        OpenAIError error = GSON.fromJson(response.body(), OpenAIError.class);
                        String message = error != null && error.error != null ? error.error.message : response.body();
                        throw new LLMApiException("API returned error: " + response.statusCode() + " - " + message);
                    }
                    OllamaChatResponse completion = GSON.fromJson(response.body(), OllamaChatResponse.class);
                    TokenUsageLedger.recordCall(settings, request.model);
                    recordTokens(request.model, completion);
                    return completion;
                }
        );
    }

    public Stream<OllamaChatResponse> getStreamingChatCompletion(OllamaChatRequest request) {
        request.stream = true;
        String requestBody = GSON.toJson(request);

        HttpRequest httpRequest = buildRequest(requestBody);

        LlmRequestLifecycle.StreamingRequest streamingRequest = LlmRequestLifecycle.startStreamingRequest();
        try {
            HttpResponse<Stream<String>> response = SHARED_HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            Stream<String> responseBody = streamingRequest.attach(response.body());

            if (response.statusCode() != 200) {
                String errorBody = responseBody.collect(Collectors.joining("\n"));
                OpenAIError error = GSON.fromJson(errorBody, OpenAIError.class);
                String message = error != null && error.error != null ? error.error.message : errorBody;
                throw new LLMApiException("API returned error: " + response.statusCode() + " - " + message);
            }

            TokenUsageLedger.recordCall(settings, request.model);

            return responseBody
                    .map(line -> GSON.fromJson(line, OllamaChatResponse.class))
                    .peek(chunk -> {
                        if (chunk != null && Boolean.TRUE.equals(chunk.done)) {
                            recordTokens(request.model, chunk);
                        }
                    })
                    .filter(chunk -> chunk != null && chunk.message != null && chunk.message.content != null);
        } catch (Exception e) {
            RuntimeException cancellationFailure = streamingRequest.cancellationFailure(e);
            if (cancellationFailure != null) {
                throw cancellationFailure;
            }
            if (e instanceof LLMApiException) {
                throw (LLMApiException) e;
            }
            throw new LLMApiException("请求Ollama流式API时出错", e);
        }
    }

    private void recordTokens(String modelId, OllamaChatResponse response) {
        if (response == null || (response.prompt_eval_count == null && response.eval_count == null)) {
            return;
        }
        TokenUsageLedger.recordTokens(
                settings,
                modelId,
                evalCount(response.prompt_eval_count),
                evalCount(response.eval_count)
        );
    }

    private static long evalCount(Integer value) {
        return value == null ? 0L : Math.max(0, value);
    }

    private HttpRequest buildRequest(String requestBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(settings.baseUrl()))
                .timeout(LlmRequestLifecycle.requestTimeout())
                .header("Content-Type", "application/json");
        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + settings.apiKey());
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
    }
}
