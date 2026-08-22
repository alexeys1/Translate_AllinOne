package com.alexeys.translate_allinone.gui.configui.controls;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ActionBlockRegistry {
    private final List<ActionBlock> blocks;
    private final int defaultColor;
    private final int defaultHoverColor;
    private final int defaultTextColor;

    public ActionBlockRegistry(List<ActionBlock> blocks, int defaultColor, int defaultHoverColor, int defaultTextColor) {
        this.blocks = blocks;
        this.defaultColor = defaultColor;
        this.defaultHoverColor = defaultHoverColor;
        this.defaultTextColor = defaultTextColor;
    }

    public void add(int x, int y, int width, int height, Component label, Runnable action) {
        add(x, y, width, height, () -> label, action, defaultColor, defaultHoverColor, defaultTextColor, false, null);
    }

    public void add(int x, int y, int width, int height, Component label, Runnable action, Component tooltip) {
        add(x, y, width, height, () -> label, action, defaultColor, defaultHoverColor, defaultTextColor, false, tooltip);
    }

    public void add(
            int x,
            int y,
            int width,
            int height,
            Component label,
            Runnable action,
            Component tooltip,
            BooleanSupplier enabled
    ) {
        blocks.add(new ActionBlock(
                x,
                y,
                width,
                height,
                () -> label,
                action,
                defaultColor,
                defaultHoverColor,
                defaultTextColor,
                false,
                tooltip,
                enabled
        ));
    }

    public void add(int x, int y, int width, int height, Supplier<Component> labelSupplier, Runnable action) {
        add(x, y, width, height, labelSupplier, action, defaultColor, defaultHoverColor, defaultTextColor, false, null);
    }

    public void add(
            int x,
            int y,
            int width,
            int height,
            Component label,
            Runnable action,
            int color,
            int hoverColor,
            int textColor,
            boolean centered
    ) {
        add(x, y, width, height, () -> label, action, color, hoverColor, textColor, centered, null);
    }

    public void add(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Runnable action,
            int color,
            int hoverColor,
            int textColor,
            boolean centered
    ) {
        add(x, y, width, height, labelSupplier, action, color, hoverColor, textColor, centered, null);
    }

    public void add(
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
        blocks.add(new ActionBlock(x, y, width, height, labelSupplier, action, color, hoverColor, textColor, centered, tooltip));
    }
}
