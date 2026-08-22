package com.alexeys.translate_allinone.utils.componentjson;
public record ComponentTranslationCacheIdentity(String key, String binding) {
    public ComponentTranslationCacheIdentity {
        if (!isSha256(key) || !isSha256(binding)) {
            throw new IllegalArgumentException("Component translation cache identity is invalid.");
        }
    }

    public static ComponentTranslationCacheIdentity create(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        ComponentTranslationCacheKey.Metadata metadata = ComponentTranslationCacheKey.metadata(document, targetLanguage);
        return new ComponentTranslationCacheIdentity(metadata.key(), metadata.binding());
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 71 || !value.startsWith("sha256:")) {
            return false;
        }
        for (int index = 7; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(current >= '0' && current <= '9') && !(current >= 'a' && current <= 'f')) {
                return false;
            }
        }
        return true;
    }
}
