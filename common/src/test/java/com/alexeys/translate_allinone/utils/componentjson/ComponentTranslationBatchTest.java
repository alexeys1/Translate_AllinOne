package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTranslationBatchTest {
    @Test
    void refusesToAppendIsolatedBatchDocuments() {
        ComponentTranslationDocument normal = document("normal");
        ComponentTranslationDocument isolated = document("isolated", "isolated");

        assertTrue(ComponentTranslationBatch.canAppend(List.of(normal), normal));
        assertFalse(ComponentTranslationBatch.canAppend(List.of(normal), isolated));
        assertFalse(ComponentTranslationBatch.canAppend(List.of(isolated), normal));
    }

    private static ComponentTranslationDocument document(String text) {
        return document(text, "shared");
    }

    private static ComponentTranslationDocument document(String text, String batchContext) {
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.TOOLTIP_LINE)
                .withContext("tooltip:line")
                .withSemanticSetting("route_policy", "line-v2")
                .withSemanticSetting("batch_context", batchContext);
        return new ComponentJsonDocumentBuilder().build(
                JsonParser.parseString("{\"text\":\"" + text + "\"}"),
                policy
        );
    }
}
