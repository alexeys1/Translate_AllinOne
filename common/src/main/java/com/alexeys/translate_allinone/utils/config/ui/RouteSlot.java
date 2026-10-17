package com.alexeys.translate_allinone.utils.config.ui;

import java.util.ArrayList;
import java.util.List;

public enum RouteSlot {
    ITEM("item", true),
    SCOREBOARD("scoreboard", true),
    OTHER_TRANSLATIONS("other_translations", true),
    WYNNCRAFT("wynncraft", true),
    WYNN_NPC_DIALOGUE("wynn_npc_dialogue", false),
    WYNNTILS_TASK_TRACKER("wynntils_task_tracker", false),
    CHAT_INPUT("chat_input", true),
    CHAT_OUTPUT("chat_output", true);

    private final String key;
    private final boolean selectable;

    RouteSlot(String key, boolean selectable) {
        this.key = key;
        this.selectable = selectable;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "route." + key;
    }

    public static List<RouteSlot> selectableSlots() {
        List<RouteSlot> slots = new ArrayList<>();
        for (RouteSlot slot : values()) {
            if (slot.selectable) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
