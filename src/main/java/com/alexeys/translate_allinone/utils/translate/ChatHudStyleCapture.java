package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Style;

public final class ChatHudStyleCapture {
    private static Style captured;

    private ChatHudStyleCapture() {
    }

    public static void set(Style style) {
        if (style != null) {
            captured = style;
        }
    }

    public static void reset() {
        captured = null;
    }

    public static Style get() {
        return captured;
    }
}