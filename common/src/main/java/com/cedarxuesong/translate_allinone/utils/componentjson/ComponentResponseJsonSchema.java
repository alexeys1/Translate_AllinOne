package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.utils.llmapi.StructuredOutputSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ComponentResponseJsonSchema {
    private static final String SCHEMA_NAME = "taio_component_translation";

    private ComponentResponseJsonSchema() {
    }

    static StructuredOutputSpec forDocument(ComponentTranslationDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Component translation document is required.");
        }

        Map<String, Object> translationProperties = new LinkedHashMap<>();
        List<String> translationIds = new ArrayList<>();
        for (ComponentTextUnit unit : document.units()) {
            translationProperties.put(unit.id(), Map.of("type", "string"));
            translationIds.add(unit.id());
        }

        Map<String, Object> translations = new LinkedHashMap<>();
        translations.put("type", "object");
        translations.put("additionalProperties", false);
        translations.put("properties", translationProperties);
        translations.put("required", translationIds);

        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("type", "string");
        protocol.put("enum", List.of(document.protocol()));

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("protocol", protocol);
        rootProperties.put("translations", translations);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.put("properties", rootProperties);
        root.put("required", List.of("protocol", "translations"));
        return new StructuredOutputSpec(SCHEMA_NAME, root);
    }
}
