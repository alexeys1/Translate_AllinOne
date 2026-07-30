package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record ComponentTranslationDocument(
        String protocol,
        int policyVersion,
        ComponentTranslationRoute route,
        JsonElement sourceJson,
        List<ComponentTextUnit> units,
        Map<String, String> semanticSettings
) {
    public static final String PROTOCOL = "taio-component-v1";

    public ComponentTranslationDocument {
        if (!PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException("Unsupported component translation protocol: " + protocol);
        }
        if (policyVersion < 1 || route == null || sourceJson == null || units == null) {
            throw new IllegalArgumentException("Component translation document is incomplete.");
        }
        sourceJson = sourceJson.deepCopy();
        units = List.copyOf(units);
        semanticSettings = semanticSettings == null ? Map.of() : Map.copyOf(new TreeMap<>(semanticSettings));
    }

    @Override
    public JsonElement sourceJson() {
        return sourceJson.deepCopy();
    }
}
