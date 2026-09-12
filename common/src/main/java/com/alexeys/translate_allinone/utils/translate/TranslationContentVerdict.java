package com.alexeys.translate_allinone.utils.translate;

public record TranslationContentVerdict(boolean accepted, TranslationRejectionCode code) {

    public static TranslationContentVerdict accept() {
        return new TranslationContentVerdict(true, null);
    }

    public static TranslationContentVerdict reject(TranslationRejectionCode code) {
        return new TranslationContentVerdict(false, code == null ? TranslationRejectionCode.MALFORMED_PROTOCOL : code);
    }
}