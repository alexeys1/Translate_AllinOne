package com.alexeys.translate_allinone.utils.translate;

public class IndexedMapResponseException extends RuntimeException {

    private final IndexedMapRejectionCode code;

    public IndexedMapResponseException(IndexedMapRejectionCode code, String message) {
        super(message);
        this.code = code == null ? IndexedMapRejectionCode.MALFORMED_PROTOCOL : code;
    }

    public IndexedMapRejectionCode code() {
        return code;
    }
}
