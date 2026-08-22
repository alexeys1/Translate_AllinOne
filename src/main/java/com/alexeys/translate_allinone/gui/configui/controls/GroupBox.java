package com.alexeys.translate_allinone.gui.configui.controls;

import net.minecraft.network.chat.Component;

public record GroupBox(
        int x,
        int y,
        int width,
        int height,
        Component title,
        Style style
) {
    public record Style(
            int backgroundColor,
            int borderColor,
            int titleColor,
            int titleBackgroundColor
    ) {
    }
}
