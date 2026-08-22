package com.alexeys.translate_allinone.gui.configui.controls;

import net.minecraft.network.chat.Component;

public record StaticTextRow(
        int x,
        int y,
        int width,
        int labelWidth,
        Component label,
        Component value,
        int labelColor,
        int valueColor
) {
}
