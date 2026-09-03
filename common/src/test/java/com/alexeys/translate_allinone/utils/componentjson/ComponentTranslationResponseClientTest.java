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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) ->
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
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
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

    @Test
    void screenUiRouteDisablesResponseCorrectionRetry() {
        AtomicInteger calls = new AtomicInteger();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(completion(
                    "{\"protocol\":\"taio-component-v1\",\"translations\":{}}"
            ));
        });

        assertThrows(CompletionException.class, () -> client.translate(
                        document(ComponentTranslationRoute.SCREEN_UI, textUnits()),
                        "Chinese",
                        profile(),
                        "screen-ui"
                ).join());

        assertEquals(1, calls.get());
    }

    @Test
    void screenUiRouteDisablesStructuredOutputFallback() {
        AtomicReference<Boolean> receivedFallback = new AtomicReference<>();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
            receivedFallback.set(allowFallback);
            return CompletableFuture.completedFuture(completion(validResponse("你好")));
        });

        ComponentTranslationResponse response = client.translate(
                document(ComponentTranslationRoute.SCREEN_UI, textUnits()),
                "Chinese",
                profile(),
                "screen-ui"
        ).join();

        assertEquals(Map.of("u0", "你好"), response.translations());
        assertEquals(Boolean.FALSE, receivedFallback.get());
    }

    @Test
    void otherRoutesKeepStructuredOutputFallbackEnabled() {
        AtomicReference<Boolean> receivedFallback = new AtomicReference<>();
        ComponentTranslationResponseClient client = client((messages, context, observer, schema, allowFallback) -> {
            receivedFallback.set(allowFallback);
            return CompletableFuture.completedFuture(completion(validResponse("你好")));
        });

        client.translate(
                document(ComponentTranslationRoute.CHAT_OUTPUT, textUnits()),
                "Chinese",
                profile(),
                "chat-output"
        ).join();

        assertEquals(Boolean.TRUE, receivedFallback.get());
    }


    @Test
    void tooltipLineBuildMessagesIncludesProtectedData() {
        ComponentTranslationRequest request = new ComponentTranslationRequest(
                ComponentTranslationDocument.PROTOCOL,
                "Chinese",
                List.of(new ComponentTranslationRequest.Item("u0", "Hello", "tooltip:line"))
        );
        List<OpenAIRequest.Message> messages = ComponentTranslationResponseClient.buildMessages(
                ComponentTranslationRoute.TOOLTIP_LINE,
                request,
                profile()
        );
        String system = messages.get(0).content;
        assertTrue(system.contains("Tooltip protected data:"));
        assertTrue(system.contains("Preserve exactly every <sN> and </sN> style tag."));
        assertTrue(system.contains("Preserve exactly every {dN}, {gN}, {valueN}, URL, command, item id, number, unit, %s/%d/%f, Minecraft formatting code, \\n, and \\t."));
    }

    @Test
    void tooltipStructuredBuildMessagesIncludesProtectedData() {
        ComponentTranslationRequest request = new ComponentTranslationRequest(
                ComponentTranslationDocument.PROTOCOL,
                "Chinese",
                List.of(new ComponentTranslationRequest.Item("u0", "Hello", "tooltip:structured"))
        );
        List<OpenAIRequest.Message> messages = ComponentTranslationResponseClient.buildMessages(
                ComponentTranslationRoute.TOOLTIP_STRUCTURED,
                request,
                profile()
        );
        String system = messages.get(0).content;
        assertTrue(system.contains("Tooltip protected data:"));
    }

    @Test
    void tooltipParagraphBuildMessagesKeepsProtectedDataBeforeParagraphContract() {
        ComponentTranslationRequest request = new ComponentTranslationRequest(
                ComponentTranslationDocument.PROTOCOL,
                "Chinese",
                List.of(new ComponentTranslationRequest.Item(
                        "u0",
                        "__TAIO_PROTECTED_TOKEN_0__ <s0>Hello</s0>",
                        "tooltip:paragraph"
                ))
        );
        List<OpenAIRequest.Message> messages = ComponentTranslationResponseClient.buildMessages(
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                request,
                profile()
        );
        String system = messages.get(0).content;
        int protectedData = system.indexOf("Tooltip protected data:");
        int protectedToken = system.indexOf("Each __TAIO_PROTECTED_TOKEN_N__ identifier");
        int paragraph = system.indexOf("tooltip_paragraph contains");
        assertTrue(protectedData >= 0);
        assertTrue(protectedToken > protectedData);
        assertTrue(paragraph > protectedToken);
    }

    @Test
    void tooltipBuildMessagesKeepsProtectedDataAfterPromptOverride() {
        ApiProviderProfile provider = profile();
        provider.system_prompt_overrides = new java.util.LinkedHashMap<>();
        provider.system_prompt_overrides.put(
                "item",
                "Custom tooltip prompt for {target_language}."
        );
        ComponentTranslationRequest request = new ComponentTranslationRequest(
                ComponentTranslationDocument.PROTOCOL,
                "Chinese",
                List.of(new ComponentTranslationRequest.Item("u0", "Hello", "tooltip:line"))
        );
        List<OpenAIRequest.Message> messages = ComponentTranslationResponseClient.buildMessages(
                ComponentTranslationRoute.TOOLTIP_LINE,
                request,
                provider
        );
        String system = messages.get(0).content;
        assertTrue(system.contains("Custom tooltip prompt for Chinese."));
        assertTrue(system.contains("Tooltip protected data:"));
    }

    @Test
    void componentRoutesBuildMessagesIncludeProtectedDataAfterPromptOverride() {
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.CHAT_OUTPUT,
                "chat_output",
                "Chat output protected data:"
        );
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.SIGN_FACE,
                "sign_book",
                "Sign/book protected data:"
        );
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.ENTITY_NAME,
                "entity_text",
                "Entity text protected data:"
        );
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.SCOREBOARD,
                "scoreboard",
                "Scoreboard protected data:"
        );
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.SCREEN_UI,
                "screen_ui",
                "Screen UI protected data:"
        );
        assertComponentRouteProtectedData(
                ComponentTranslationRoute.ADVANCEMENT,
                "other_translations",
                "Protected data:"
        );
    }

    private static void assertComponentRouteProtectedData(
            ComponentTranslationRoute route,
            String promptRouteKey,
            String protectedDataLabel
    ) {
        ApiProviderProfile provider = profile();
        provider.system_prompt_overrides = new java.util.LinkedHashMap<>();
        provider.system_prompt_overrides.put(
                promptRouteKey,
                "Custom prompt for {target_language}."
        );
        ComponentTranslationRequest request = new ComponentTranslationRequest(
                ComponentTranslationDocument.PROTOCOL,
                "Chinese",
                List.of(new ComponentTranslationRequest.Item("u0", "Hello", route.wireName()))
        );
        List<OpenAIRequest.Message> messages = ComponentTranslationResponseClient.buildMessages(
                route,
                request,
                provider
        );
        String system = messages.get(0).content;
        assertTrue(system.contains("Custom prompt for Chinese."));
        assertTrue(system.contains(protectedDataLabel));
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
        return document(ComponentTranslationRoute.CHAT_OUTPUT, units);
    }

    private static ComponentTranslationDocument document(
            ComponentTranslationRoute route,
            List<ComponentTextUnit> units
    ) {
        JsonObject sourceJson = new JsonObject();
        sourceJson.addProperty("text", "Hello");
        return new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                route,
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
