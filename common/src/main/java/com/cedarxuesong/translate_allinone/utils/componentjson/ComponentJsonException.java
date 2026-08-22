package com.cedarxuesong.translate_allinone.utils.componentjson;

public final class ComponentJsonException extends RuntimeException {
    private final Kind kind;
    private final boolean retriesExhausted;

    public ComponentJsonException(Kind kind, String message) {
        this(kind, message, null, false);
    }

    public ComponentJsonException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, false);
    }

    private ComponentJsonException(Kind kind, String message, Throwable cause, boolean retriesExhausted) {
        super(message, cause);
        this.kind = kind;
        this.retriesExhausted = retriesExhausted;
    }

    public Kind kind() {
        return kind;
    }

    ComponentJsonException withRetriesExhausted() {
        return retriesExhausted
                ? this
                : new ComponentJsonException(kind, getMessage(), this, true);
    }

    boolean retriesExhausted() {
        return retriesExhausted;
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
