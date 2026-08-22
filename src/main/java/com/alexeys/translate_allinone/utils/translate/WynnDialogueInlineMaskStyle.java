package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Style;

public final class WynnDialogueInlineMaskStyle {
    private static final String INSERTION = "translate_allinone:wynn_dialogue_inline_mask";

    private WynnDialogueInlineMaskStyle() {
    }

    public static Style mask(Style style) {
        return (style == null ? Style.EMPTY : style).withInsertion(INSERTION);
    }

    public static boolean isMasked(Style style) {
        return style != null && INSERTION.equals(style.getInsertion());
    }
}
