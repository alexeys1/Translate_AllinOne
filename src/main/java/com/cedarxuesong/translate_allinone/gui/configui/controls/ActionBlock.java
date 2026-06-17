package com.cedarxuesong.translate_allinone.gui.configui.controls;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ActionBlock {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Supplier<Component> labelSupplier;
    private final Runnable action;
    private final int color;
    private final int hoverColor;
    private final int textColor;
    private final boolean centered;
    private final Component tooltip;

    public ActionBlock(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Runnable action,
            int color,
            int hoverColor,
            int textColor,
            boolean centered,
            Component tooltip
    ) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.labelSupplier = labelSupplier;
        this.action = action;
        this.color = color;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
        this.centered = centered;
        this.tooltip = tooltip;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Component label() {
        return labelSupplier.get();
    }

    public void runAction() {
        action.run();
    }

    public int color() {
        return color;
    }

    public int hoverColor() {
        return hoverColor;
    }

    public int textColor() {
        return textColor;
    }

    public boolean centered() {
        return centered;
    }

    public Component tooltip() {
        return tooltip;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
