package com.alexeys.translate_allinone.utils.cache.component;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;

import java.util.EnumSet;
import java.util.Set;

public enum ComponentCacheModule {
    ITEM("item", "component_item_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.TOOLTIP_LINE,
            ComponentTranslationRoute.TOOLTIP_STRUCTURED,
            ComponentTranslationRoute.TOOLTIP_PARAGRAPH
    )),
    SCOREBOARD("scoreboard", "component_scoreboard_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.SCOREBOARD
    )),
    SIGN("sign", "component_sign_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.SIGN_FACE,
            ComponentTranslationRoute.SIGN_CONTINUOUS
    )),
    ENTITY("entity", "component_entity_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.ENTITY_NAME,
            ComponentTranslationRoute.TEXT_DISPLAY
    )),
    BOOK("book", "component_book_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.BOOK_PAGE
    )),
    ADVANCEMENT("advancement", "component_advancement_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.ADVANCEMENT
    )),
    SCREEN_UI("screen_ui", "component_screen_ui_translate_cache.json", EnumSet.of(
            ComponentTranslationRoute.SCREEN_UI
    ));

    private final String wireName;
    private final String fileName;
    private final Set<ComponentTranslationRoute> routes;

    ComponentCacheModule(String wireName, String fileName, Set<ComponentTranslationRoute> routes) {
        this.wireName = wireName;
        this.fileName = fileName;
        this.routes = Set.copyOf(routes);
    }

    public String wireName() {
        return wireName;
    }

    public String fileName() {
        return fileName;
    }

    public boolean owns(ComponentTranslationRoute route) {
        return routes.contains(route);
    }

    public static ComponentCacheModule forRoute(ComponentTranslationRoute route) {
        for (ComponentCacheModule module : values()) {
            if (module.owns(route)) {
                return module;
            }
        }
        throw new IllegalArgumentException("No Component cache module for route: " + route);
    }
}
