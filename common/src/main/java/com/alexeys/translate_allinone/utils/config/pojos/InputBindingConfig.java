package com.alexeys.translate_allinone.utils.config.pojos;

public class InputBindingConfig {
    public InputType type = InputType.KEYSYM;
    public int code = -1;
    public String keyName;

    public enum InputType {
        KEYSYM,
        MOUSE
    }

    public boolean isBound() {
        return (keyName != null && !keyName.isBlank()) || code >= 0;
    }
}
