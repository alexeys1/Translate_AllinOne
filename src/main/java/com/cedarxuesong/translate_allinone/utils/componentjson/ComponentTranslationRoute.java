package com.cedarxuesong.translate_allinone.utils.componentjson;

public enum ComponentTranslationRoute {
    CHAT_OUTPUT("chat_output", "chat_output"),
    ADVANCEMENT("advancement", "other_translations"),
    TOOLTIP_LINE("tooltip_line", "item"),
    TOOLTIP_STRUCTURED("tooltip_structured", "item"),
    TOOLTIP_PARAGRAPH("tooltip_paragraph", "item"),
    SCOREBOARD("scoreboard", "scoreboard");

    private final String wireName;
    private final String promptRouteKey;

    ComponentTranslationRoute(String wireName, String promptRouteKey) {
        this.wireName = wireName;
        this.promptRouteKey = promptRouteKey;
    }

    public String wireName() {
        return wireName;
    }

    public String promptRouteKey() {
        return promptRouteKey;
    }

    public static ComponentTranslationRoute fromWireName(String value) {
        for (ComponentTranslationRoute route : values()) {
            if (route.wireName.equals(value)) {
                return route;
            }
        }
        throw new IllegalArgumentException("Unknown component translation route: " + value);
    }
}
