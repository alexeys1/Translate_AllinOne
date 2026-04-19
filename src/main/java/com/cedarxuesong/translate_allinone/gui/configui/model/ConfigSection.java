package com.cedarxuesong.translate_allinone.gui.configui.model;

public enum ConfigSection {
    PROVIDERS("providers"),
    CHAT_OUTPUT("chat_output"),
    CHAT_INPUT("chat_input"),
    ITEM("item"),
    SCOREBOARD("scoreboard"),
    WYNNCRAFT("wynncraft"),
    CACHE("cache"),
    DEBUG("debug");

    private final String key;

    ConfigSection(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "section." + key;
    }
}
