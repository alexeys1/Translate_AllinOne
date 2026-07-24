package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationCacheKey;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ComponentTranslationCacheEntry(
        String protocol,
        int policyVersion,
        String route,
        String targetLanguage,
        String structureFingerprint,
        String sourceFingerprint,
        String tokenFingerprint,
        Map<String, String> translations
) {
    public ComponentTranslationCacheEntry {
        if (protocol == null
                || policyVersion < 1
                || route == null
                || targetLanguage == null
                || structureFingerprint == null
                || sourceFingerprint == null
                || tokenFingerprint == null
                || translations == null) {
            throw new IllegalArgumentException("Component translation cache entry is incomplete.");
        }
        translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }

    public static ComponentTranslationCacheEntry create(
            ComponentTranslationDocument document,
            String targetLanguage,
            ComponentTranslationResponse response
    ) {
        ComponentTranslationCacheKey.Metadata metadata = ComponentTranslationCacheKey.metadata(document, targetLanguage);
        return create(document, targetLanguage, response, metadata);
    }

    public static ComponentTranslationCacheEntry create(
            ComponentTranslationDocument document,
            String targetLanguage,
            ComponentTranslationResponse response,
            ComponentTranslationCacheKey.Metadata metadata
    ) {
        return new ComponentTranslationCacheEntry(
                document.protocol(),
                document.policyVersion(),
                document.route().wireName(),
                targetLanguage.trim(),
                metadata.structureFingerprint(),
                metadata.sourceFingerprint(),
                metadata.tokenFingerprint(),
                response.translations()
        );
    }

    public boolean matches(
            ComponentTranslationDocument document,
            String requestedTargetLanguage,
            ComponentTranslationCacheKey.Metadata metadata
    ) {
        return document.protocol().equals(protocol)
                && document.policyVersion() == policyVersion
                && document.route().wireName().equals(route)
                && requestedTargetLanguage.trim().equals(targetLanguage)
                && metadata.structureFingerprint().equals(structureFingerprint)
                && metadata.sourceFingerprint().equals(sourceFingerprint)
                && metadata.tokenFingerprint().equals(tokenFingerprint);
    }

    public ComponentTranslationResponse toResponse() {
        return new ComponentTranslationResponse(protocol, translations);
    }
}
