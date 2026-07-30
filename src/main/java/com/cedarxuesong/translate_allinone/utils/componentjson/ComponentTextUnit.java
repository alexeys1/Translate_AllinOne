package com.cedarxuesong.translate_allinone.utils.componentjson;

import java.util.Map;
import java.util.TreeMap;

public record ComponentTextUnit(
        String id,
        String jsonPointer,
        String sourceText,
        Map<String, Integer> protectedTokens,
        String context
) {
    public ComponentTextUnit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Component text unit id is required.");
        }
        if (jsonPointer == null || sourceText == null || context == null) {
            throw new IllegalArgumentException("Component text unit fields cannot be null.");
        }
        protectedTokens = protectedTokens == null ? Map.of() : Map.copyOf(new TreeMap<>(protectedTokens));
    }
}
