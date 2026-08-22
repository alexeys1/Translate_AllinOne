package com.alexeys.translate_allinone.utils.componentjson;
public record ComponentTranslationPreparedRequest(
        ComponentTranslationDocument document,
        String targetLanguage,
        ComponentTranslationCacheIdentity identity
) {
    public ComponentTranslationPreparedRequest {
        if (document == null || targetLanguage == null || targetLanguage.isBlank() || identity == null) {
            throw new IllegalArgumentException("Prepared component translation request is incomplete.");
        }
        targetLanguage = targetLanguage.trim();
    }

    public static ComponentTranslationPreparedRequest create(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        String normalizedLanguage = targetLanguage == null ? null : targetLanguage.trim();
        return new ComponentTranslationPreparedRequest(
                document,
                normalizedLanguage,
                ComponentTranslationCacheIdentity.create(document, normalizedLanguage)
        );
    }
}
