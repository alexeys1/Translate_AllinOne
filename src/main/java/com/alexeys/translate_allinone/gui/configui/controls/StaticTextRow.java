package com.alexeys.translate_allinone.gui.configui.controls;

import net.minecraft.text.Text;

public record StaticTextRow(
        int x,
        int y,
        int width,
        int labelWidth,
        Text label,
        Text value,
        int labelColor,
        int valueColor
) {
}
