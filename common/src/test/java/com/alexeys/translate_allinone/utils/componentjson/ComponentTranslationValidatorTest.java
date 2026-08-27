package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationValidatorTest {
    @Test
    void allowsSplittingSameStyleIdOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s3>Hello</s3>"
        );
        ComponentTranslationResponse response = response("<s3>你好</s3><s3>世界</s3>");

        assertDoesNotThrow(() -> new ComponentTranslationValidator().validate(document, response));
    }

    @Test
    void rejectsNewStyleIdOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s3>Hello</s3>"
        );
        ComponentTranslationResponse response = response("<s4>你好</s4>");

        ComponentJsonException error = assertThrows(
                ComponentJsonException.class,
                () -> new ComponentTranslationValidator().validate(document, response)
        );
        assertTrue(error.getMessage().contains("Line style ids changed"));
    }

    @Test
    void allowsMissingStyleIdOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s0>A</s0><s1>B</s1>"
        );
        ComponentTranslationResponse response = response("<s0>啊</s0>");

        assertDoesNotThrow(() -> new ComponentTranslationValidator().validate(document, response));
    }

    @Test
    void rejectsUnbalancedStyleTagsOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s3>Hello</s3>"
        );
        ComponentTranslationResponse response = response("<s3>你好</s3><s3>世界");

        ComponentJsonException error = assertThrows(
                ComponentJsonException.class,
                () -> new ComponentTranslationValidator().validate(document, response)
        );
        assertTrue(error.getMessage().contains("Style tags are invalid"));
    }

    @Test
    void stillRequiresNonStyleProtectedTokensOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s3>{d1} Hello</s3>"
        );
        ComponentTranslationResponse response = response("<s3>你好</s3>");

        ComponentJsonException error = assertThrows(
                ComponentJsonException.class,
                () -> new ComponentTranslationValidator().validate(document, response)
        );
        assertTrue(error.getMessage().contains("Protected tokens changed"));
    }

    @Test
    void allowsExtraNonStyleProtectedTokensOnTooltipLine() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_LINE,
                "<s0>Hello</s0>"
        );
        ComponentTranslationResponse response = response("<s0>你好 {d1}</s0>");

        assertDoesNotThrow(() -> new ComponentTranslationValidator().validate(document, response));
    }

    @Test
    void allowsSplittingSameStyleIdOnTooltipStructured() {
        ComponentTranslationDocument document = document(
                ComponentTranslationRoute.TOOLTIP_STRUCTURED,
                "<s3>Hello</s3>"
        );
        ComponentTranslationResponse response = response("<s3>你好</s3><s3>世界</s3>");

        assertDoesNotThrow(() -> new ComponentTranslationValidator().validate(document, response));
    }

    private static ComponentTranslationDocument document(
            ComponentTranslationRoute route,
            String sourceText
    ) {
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route);
        ComponentTextUnit unit = new ComponentTextUnit(
                "u0",
                "/text",
                sourceText,
                policy.protectedTokenMultiset(sourceText),
                route.wireName()
        );
        JsonObject sourceJson = new JsonObject();
        sourceJson.addProperty("text", sourceText);
        return new ComponentTranslationDocument(
                ComponentTranslationDocument.PROTOCOL,
                ComponentTranslationPolicy.CURRENT_VERSION,
                route,
                sourceJson,
                List.of(unit),
                Map.of()
        );
    }

    private static ComponentTranslationResponse response(String translation) {
        return new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of("u0", translation)
        );
    }
}
