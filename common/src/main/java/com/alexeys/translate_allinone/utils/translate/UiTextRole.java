package com.alexeys.translate_allinone.utils.translate;

public enum UiTextRole {
    TITLE("title"),
    CATEGORY("category"),
    MODULE("module"),
    OPTION("option"),
    DESCRIPTION("description"),
    BUTTON("button"),
    VALUE("value"),
    TOOLTIP("tooltip");

    private final String wireName;

    UiTextRole(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
