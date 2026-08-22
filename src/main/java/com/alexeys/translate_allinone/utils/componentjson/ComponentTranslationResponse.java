package com.alexeys.translate_allinone.utils.componentjson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ComponentTranslationResponse(
        String protocol,
        Map<String, String> translations
) {
    public ComponentTranslationResponse {
        if (protocol == null || translations == null) {
            throw new IllegalArgumentException("Component translation response is incomplete.");
        }
        translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }
}
