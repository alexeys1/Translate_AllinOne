package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentDynamicJsonTemplateTest {
    @Test
    void restoresNumericValuesAndEmbeddedDecorativeCharacters() {
        String decorativeGlyph = Character.toString(0xE001);
        JsonObject source = component("Level 42");
        JsonArray extra = new JsonArray();
        extra.add(component("Rank " + decorativeGlyph + " 7"));
        source.add("extra", extra);

        ComponentDynamicJsonTemplate template = ComponentDynamicJsonTemplate.prepare(
                source,
                Set.of(decorativeGlyph)
        );

        JsonElement templateJson = template.templateJson();
        assertEquals("Level {d1}", textAt(templateJson, "text"));
        assertEquals("Rank {taio.private.a} {d1}", textAt(templateJson, "extra", 0, "text"));
        assertTrue(template.privatePlaceholders().contains("{taio.private.a}"));
        assertEquals("Level 42", source.get("text").getAsString());

        JsonObject translated = templateJson.getAsJsonObject();
        translated.addProperty("text", "等级 {d1}");
        translated.getAsJsonArray("extra").get(0).getAsJsonObject()
                .addProperty("text", "级别 {taio.private.a} {d1}");

        JsonElement restored = template.restore(translated);

        assertEquals("等级 42", textAt(restored, "text"));
        assertEquals("级别 " + decorativeGlyph + " 7", textAt(restored, "extra", 0, "text"));
    }

    @Test
    void matchesLongestEmbeddedDecorativeCharacterFirst() {
        String shortToken = Character.toString(0xE002);
        String longToken = shortToken + "x";
        ComponentDynamicJsonTemplate template = ComponentDynamicJsonTemplate.prepare(
                component(longToken),
                Set.of(shortToken, longToken)
        );

        assertEquals("{taio.private.a}", textAt(template.templateJson(), "text"));
        assertEquals(longToken, textAt(template.restore(template.templateJson()), "text"));
    }

    private static JsonObject component(String text) {
        JsonObject component = new JsonObject();
        component.addProperty("text", text);
        return component;
    }

    private static String textAt(JsonElement root, Object... path) {
        JsonElement current = root;
        for (Object segment : path) {
            current = segment instanceof Integer index
                    ? current.getAsJsonArray().get(index)
                    : current.getAsJsonObject().get(segment.toString());
        }
        return current.getAsString();
    }
}
