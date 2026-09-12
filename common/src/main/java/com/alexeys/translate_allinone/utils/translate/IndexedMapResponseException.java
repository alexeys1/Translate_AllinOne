package com.alexeys.translate_allinone.utils.translate;

public class IndexedMapResponseException extends RuntimeException {

    private final TranslationRejectionCode code;

    public IndexedMapResponseException(TranslationRejectionCode code, String message) {
        super(message);
        this.code = code == null ? TranslationRejectionCode.MALFORMED_PROTOCOL : code;
    }

    public TranslationRejectionCode code() {
        return code;
    }
}