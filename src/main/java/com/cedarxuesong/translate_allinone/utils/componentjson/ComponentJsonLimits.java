package com.cedarxuesong.translate_allinone.utils.componentjson;

public record ComponentJsonLimits(
        int maxDocumentUtf8Bytes,
        int maxDocumentDepth,
        int maxDocumentNodes,
        int maxResponseUtf8Bytes,
        int maxResponseDepth,
        int maxTextUnits,
        int maxTranslationChars,
        int maxTotalTranslationChars
) {
    public static final ComponentJsonLimits DEFAULT = new ComponentJsonLimits(
            512 * 1024,
            64,
            4096,
            256 * 1024,
            16,
            128,
            16 * 1024,
            128 * 1024
    );

    public ComponentJsonLimits {
        if (maxDocumentUtf8Bytes < 1
                || maxDocumentDepth < 1
                || maxDocumentNodes < 1
                || maxResponseUtf8Bytes < 1
                || maxResponseDepth < 2
                || maxTextUnits < 1
                || maxTranslationChars < 1
                || maxTotalTranslationChars < 1) {
            throw new IllegalArgumentException("Component JSON limits must be positive.");
        }
    }
}
