package com.cedarxuesong.translate_allinone.utils.componentjson;

public final class ComponentJsonException extends RuntimeException {
    private final Kind kind;

    public ComponentJsonException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ComponentJsonException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        CODEC,
        LIMIT,
        DOCUMENT,
        RESPONSE,
        VALIDATION,
        APPLY
    }
}
