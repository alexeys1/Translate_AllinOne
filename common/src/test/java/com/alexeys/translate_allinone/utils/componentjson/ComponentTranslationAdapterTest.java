package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.versionapi.ComponentCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTranslationAdapterTest {
    private static final ComponentCodec<TestComponent> COMPONENT_CODEC = new ComponentCodec<>() {
        @Override
        public JsonElement encode(TestComponent component) {
            JsonObject json = new JsonObject();
            json.addProperty("text", component.text());
            return json;
        }

        @Override
        public TestComponent decode(JsonElement json) {
            return new TestComponent(json.getAsJsonObject().get("text").getAsString());
        }
    };

    @Test
    void delegatesRoundTripToVersionCodec() {
        TestComponent source = new TestComponent("Hello");

        assertEquals(source, COMPONENT_CODEC.decode(COMPONENT_CODEC.encode(source)));
    }

    @Test
    void appliesTranslationsBeforeDecodingVersionComponent() {
        JsonObject sourceJson = new JsonObject();
        sourceJson.addProperty("text", "Hello");
        ComponentTranslationDocument document = new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                ComponentTranslationRoute.CHAT_OUTPUT,
                sourceJson,
                List.of(new ComponentTextUnit("u0", "/text", "Hello", Map.of(), "chat_output")),
                Map.of()
        );
        ComponentTranslationResponse response = new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of("u0", "你好")
        );

        TestComponent translated = new ComponentTranslationAdapter<>(COMPONENT_CODEC).apply(document, response);

        assertEquals(new TestComponent("你好"), translated);
    }

    private record TestComponent(String text) {
    }
}
