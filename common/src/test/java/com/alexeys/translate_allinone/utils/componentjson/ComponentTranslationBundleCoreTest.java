package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTranslationBundleCoreTest {
    @Test
    void appliesOrderedTranslationsAndRestoresDynamicValues() {
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createOrdered(
                List.of(template("Level 42"), template("Health 7")),
                List.of("Level {d1}", "Health {d1}"),
                ComponentTranslationRoute.CHAT_OUTPUT,
                "status",
                "2",
                true
        );
        ComponentTranslationResponse response = new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of(
                        "l0:u0", "等级 {d1}",
                        "l1:u0", "生命 {d1}"
                )
        );

        List<JsonElement> translated = core.applyToJson(response);

        assertEquals("等级 42", text(translated.get(0)));
        assertEquals("生命 7", text(translated.get(1)));
        assertEquals("2", core.cacheDocument().semanticSettings().get("line_count"));
        assertEquals("0:1,1:1", core.cacheDocument().semanticSettings().get("line_boundaries"));
        assertEquals("isolated", core.cacheDocument().semanticSettings().get("batch_context"));
    }

    @Test
    void resolvesSemanticSlotParagraphAndSafeBodyFallback() {
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createCoherentParagraph(
                List.of(template("Alice defeated")),
                "tooltip:paragraph",
                "paragraph-v4",
                "<s0>{slot0}</s0> defeated",
                List.of(new ComponentTranslationBundleCore.SemanticSlot("slot0", 0, "Alice"))
        );
        ComponentTranslationResponse response = new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of(
                        "paragraph", "<s0>{slot0}</s0> 已获胜",
                        "slot0", "琪露诺"
                )
        );

        assertEquals("<s0>琪露诺</s0> 已获胜", core.coherentParagraphTranslation(response));
        assertEquals("<s3>琪露诺 已获胜</s3>", core.coherentSafeBodyParagraphTranslation(response, 3));
    }

    @Test
    void preservesInlineAnchorTopology() {
        ComponentTranslationBundleCore core = ComponentTranslationBundleCore.createInlineAnchoredParagraph(
                "tooltip:paragraph",
                "paragraph-v6",
                "{accent0.begin}Power{accent0.end} {value0}",
                List.of("{value0}"),
                List.of("accent0")
        );
        ComponentTranslationResponse response = new ComponentTranslationResponse(
                ComponentTranslationDocument.PROTOCOL,
                Map.of("paragraph", "{accent0.begin}力量{accent0.end} {value0}")
        );

        assertEquals(
                "{accent0.begin}力量{accent0.end} {value0}",
                core.coherentParagraphTranslation(response)
        );
        assertEquals(List.of(new ComponentTranslationBundleCore.AccentAnchor("accent0")), core.accentAnchors());
    }

    private static ComponentDynamicJsonTemplate template(String value) {
        JsonObject json = new JsonObject();
        json.addProperty("text", value);
        return ComponentDynamicJsonTemplate.prepare(json);
    }

    private static String text(JsonElement json) {
        return json.getAsJsonObject().get("text").getAsString();
    }
}
