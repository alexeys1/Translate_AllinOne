package com.alexeys.translate_allinone.utils.componentjson;

public enum ComponentTranslationRoute {
    CHAT_OUTPUT("chat_output", "chat_output"),
    ADVANCEMENT("advancement", "other_translations"),
    SIGN_FACE("sign_face", "sign_book"),
    SIGN_CONTINUOUS("sign_continuous", "sign_book"),
    ENTITY_NAME("entity_name", "entity_text"),
    TEXT_DISPLAY("text_display", "entity_text"),
    BOOK_PAGE("book_page", "sign_book"),
    TOOLTIP_LINE("tooltip_line", "item"),
    TOOLTIP_STRUCTURED("tooltip_structured", "item"),
    TOOLTIP_PARAGRAPH("tooltip_paragraph", "item"),
    SCOREBOARD("scoreboard", "scoreboard"),
    SCREEN_UI("screen_ui", "screen_ui");

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
