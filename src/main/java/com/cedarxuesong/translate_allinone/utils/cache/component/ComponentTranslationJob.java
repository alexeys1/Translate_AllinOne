package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;

public record ComponentTranslationJob(
        ComponentTranslationDocument document,
        ComponentTranslationRoute route,
        String legacyKey,
        long sessionEpoch
) {
    public ComponentTranslationJob {
        if (document == null || route == null || document.route() != route) {
            throw new IllegalArgumentException("Component translation job route does not match its document.");
        }
    }
}
