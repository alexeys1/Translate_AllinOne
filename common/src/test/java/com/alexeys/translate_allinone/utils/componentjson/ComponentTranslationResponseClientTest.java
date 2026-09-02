package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.llmapi.LLMApiException;
import com.alexeys.translate_allinone.utils.llmapi.LlmCompletion;
import com.alexeys.translate_allinone.utils.llmapi.StructuredOutputSpec;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationResponseClientTest {
    @Test
    void returnsEmptyResponseWithoutCallingProviderForDocumentWithoutText() {
        AtomicInteger calls = new AtomicInteger();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(completion("unused"));
        });

        ComponentTranslationResponse response = client.translate(
                document(List.of()),
                "Chinese",
                profile(),
                "no-text"
        ).join();

        assertEquals(ComponentTranslationDocument.PROTOCOL, response.protocol());
        assertEquals(Map.of(), response.translations());
        assertEquals(0, calls.get());
    }

    @Test
    void requestsAndValidatesComponentTranslationResponse() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> receivedContext = new AtomicReference<>();
        AtomicReference<StructuredOutputSpec> receivedSchema = new AtomicReference<>();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) -> {
            calls.incrementAndGet();
            receivedContext.set(context);
            receivedSchema.set(schema);
            return CompletableFuture.completedFuture(completion(validResponse("你好")));
        });

        ComponentTranslationResponse response = client.translate(
                document(textUnits()),
                "Chinese",
                profile(),
                "chat-output"
        ).join();

        assertEquals(Map.of("u0", "你好"), response.translations());
        assertEquals(1, calls.get());
        assertEquals("chat-output", receivedContext.get());
        assertNotNull(receivedSchema.get());
        assertEquals("taio_component_translation", receivedSchema.get().name());
    }

    @Test
    void requestsCorrectionAfterRejectedResponse() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<OpenAIRequest.Message>> correctionMessages = new AtomicReference<>();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) -> {
            int attempt = calls.incrementAndGet();
            if (attempt == 1) {
                return CompletableFuture.completedFuture(completion(
                        "{\"protocol\":\"taio-component-v1\",\"translations\":{}}"
                ));
            }
            correctionMessages.set(messages);
            return CompletableFuture.completedFuture(completion(validResponse("你好")));
        });

        ComponentTranslationResponse response = client.translate(
                document(textUnits()),
                "Chinese",
                profile(),
                "chat-output"
        ).join();

        assertEquals(Map.of("u0", "你好"), response.translations());
        assertEquals(2, calls.get());
        List<OpenAIRequest.Message> messages = correctionMessages.get();
        assertNotNull(messages);
        assertTrue(messages.get(messages.size() - 1).content.contains("previous component translation response was rejected"));
    }

    @Test
    void doesNotRetryMoreThanOnceAfterRejectedResponse() {
        AtomicInteger calls = new AtomicInteger();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(completion(
                    "{\"protocol\":\"taio-component-v1\",\"translations\":{}}"
            ));
        });

        assertThrows(CompletionException.class, () -> client.translate(
                        document(textUnits()),
                        "Chinese",
                        profile(),
                        "chat-output"
                ).join());

        assertEquals(2, calls.get());
    }

    @Test
    void allowsBatchResponseToReachPerDocumentValidation() {
        ComponentTranslationBatch batch = ComponentTranslationBatch.create(List.of(
                document(textUnits()),
                document(textUnits())
        ));
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) ->
                CompletableFuture.completedFuture(completion(
                        "{\"protocol\":\"taio-component-v1\",\"translations\":{\"b0:u0\":\"你好\",\"b1:u0\":\"<s0>你好</s0></s0>\"}}"
                ))
        );

        ComponentTranslationResponse response = client.translate(
                batch.requestDocument(),
                "Chinese",
                profile(),
                "batch"
        ).join();

        assertEquals(2, response.translations().size());
        assertEquals("你好", response.translations().get("b0:u0"));
    }

    @Test
    void doesNotRetryTransientProviderFailure() {
        AtomicInteger calls = new AtomicInteger();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema) -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new LLMApiException("503 temporarily unavailable"));
        });

        assertThrows(CompletionException.class, () -> client.translate(
                        document(textUnits()),
                        "Chinese",
                        profile(),
                        "chat-output"
                ).join());

        assertEquals(1, calls.get());
    }

    private static ComponentTranslationResponseClient client(
            ComponentTranslationResponseClient.CompletionRequester requester
    ) {
        return new ComponentTranslationResponseClient(
                new ComponentResponseParser(),
                new ComponentTranslationValidator(),
                settings -> requester,
                null,
                null
        );
    }

    private static ComponentTranslationDocument document(List<ComponentTextUnit> units) {
        JsonObject sourceJson = new JsonObject();
        sourceJson.addProperty("text", "Hello");
        return new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                ComponentTranslationRoute.CHAT_OUTPUT,
                sourceJson,
                units,
                Map.of()
        );
    }

    private static List<ComponentTextUnit> textUnits() {
        return List.of(new ComponentTextUnit("u0", "/text", "Hello", Map.of(), "chat_output"));
    }

    private static ApiProviderProfile profile() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.model_id = "test-model";
        profile.system_prompt_suffix = "";
        return profile;
    }

    private static LlmCompletion completion(String content) {
        return new LlmCompletion(content, "stop");
    }

    private static String validResponse(String translation) {
        return "{\"protocol\":\"taio-component-v1\",\"translations\":{\"u0\":\""
                + translation
                + "\"}}";
    }
}
