package com.alexeys.translate_allinone.utils.translate;

public record TranslationContentVerdict(boolean accepted, String reason) {

    public static final String EMPTY = "EMPTY";
    public static final String TRUNCATED = "TRUNCATED";
    public static final String MALFORMED_PROTOCOL = "MALFORMED_PROTOCOL";
    public static final String STRUCTURED_ARTIFACT = "STRUCTURED_ARTIFACT";
    public static final String SOURCE_COPY = "SOURCE_COPY";
    public static final String MISSING_TARGET_LANGUAGE_SIGNAL = "MISSING_TARGET_LANGUAGE_SIGNAL";
    public static final String PROTECTED_TOKEN_MISMATCH = "PROTECTED_TOKEN_MISMATCH";
    public static final String ABNORMAL_REPETITION = "ABNORMAL_REPETITION";

    public static TranslationContentVerdict accept() {
        return new TranslationContentVerdict(true, null);
    }

    public static TranslationContentVerdict reject(String reason) {
        return new TranslationContentVerdict(false, reason == null ? "" : reason);
    }
}
