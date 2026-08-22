package com.alexeys.translate_allinone.utils.componentjson;

public record ComponentTranslationTemplateIdentity(String key, String binding) {
    public ComponentTranslationTemplateIdentity {
        if (!isSha256(key) || !isSha256(binding)) {
            throw new IllegalArgumentException("Component translation template identity is invalid.");
        }
    }

    public static ComponentTranslationTemplateIdentity createEntityName(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        ComponentTranslationCacheKey.TemplateMetadata metadata =
                ComponentTranslationCacheKey.entityTemplateMetadata(document, targetLanguage);
        return new ComponentTranslationTemplateIdentity(metadata.key(), metadata.binding());
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
