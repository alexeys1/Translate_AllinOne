package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentJsonDocumentBuilderTest {
    @Test
    void extractsLiteralTextFromRootAndExtraComponents() {
        JsonObject source = JsonParser.parseString(
                "{\"text\":\"Score {value}\",\"color\":\"gold\",\"extra\":[{\"text\":\" 42\"},{\"translate\":\"ignored\"}]}"
        ).getAsJsonObject();

        ComponentTranslationDocument document = new ComponentJsonDocumentBuilder().build(
                source,
                ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.SCOREBOARD)
        );

        assertEquals(List.of("/text", "/extra/0/text"), document.units().stream()
                .map(ComponentTextUnit::jsonPointer)
                .toList());
        assertEquals(List.of("Score {value}", " 42"), document.units().stream()
                .map(ComponentTextUnit::sourceText)
                .toList());
        assertEquals(1, document.units().get(0).protectedTokens().get("{value}"));
        assertEquals(source, document.sourceJson());
    }

    @Test
    void enforcesConfiguredTextUnitLimit() {
        ComponentJsonLimits limits = new ComponentJsonLimits(1024, 10, 20, 1024, 4, 1, 100, 100);
        JsonObject source = JsonParser.parseString(
                "{\"text\":\"one\",\"extra\":[{\"text\":\"two\"}]}"
        ).getAsJsonObject();

        ComponentJsonException exception = assertThrows(
                ComponentJsonException.class,
                () -> new ComponentJsonDocumentBuilder(limits).build(
                        source,
                        ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.CHAT_OUTPUT)
                )
        );

        assertEquals(ComponentJsonException.Kind.LIMIT, exception.kind());
    }
}
