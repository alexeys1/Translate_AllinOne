package com.alexeys.translate_allinone.utils.translate;

public enum TranslationRejectionCode {
    EMPTY,
    TRUNCATED,
    MALFORMED_PROTOCOL,
    ID_MISMATCH,
    STRUCTURED_ARTIFACT,
    SOURCE_COPY,
    MISSING_TARGET_LANGUAGE_SIGNAL,
    PROTECTED_TOKEN_MISMATCH,
    ABNORMAL_REPETITION
}