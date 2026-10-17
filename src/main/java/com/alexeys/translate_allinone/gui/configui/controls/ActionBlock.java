package com.alexeys.translate_allinone.gui.configui.controls;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ActionBlock {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Supplier<Component> labelSupplier;
    private final Runnable action;
    private final IntSupplier colorSupplier;
    private final IntSupplier hoverColorSupplier;
    private final int textColor;
    private final boolean centered;
    private final Supplier<Component> tooltipSupplier;
    private final BooleanSupplier enabled;
    private final boolean resetIcon;

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
        this(x, y, width, height, labelSupplier, action, color, hoverColor, textColor, centered, tooltip, () -> true, false);
    }

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
            Supplier<Component> tooltipSupplier
    ) {
        this(x, y, width, height, labelSupplier, action, () -> color, () -> hoverColor, textColor, centered, tooltipSupplier, () -> true, false);
    }

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
            Component tooltip,
            BooleanSupplier enabled
    ) {
        this(x, y, width, height, labelSupplier, action, color, hoverColor, textColor, centered, tooltip, enabled, false);
    }

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
            Component tooltip,
            BooleanSupplier enabled,
            boolean resetIcon
    ) {
        this(
                x,
                y,
                width,
                height,
                labelSupplier,
                action,
                () -> color,
                () -> hoverColor,
                textColor,
                centered,
                tooltip == null ? null : () -> tooltip,
                enabled,
                resetIcon
        );
    }

    public ActionBlock(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Runnable action,
            IntSupplier colorSupplier,
            IntSupplier hoverColorSupplier,
            int textColor,
            boolean centered,
            Supplier<Component> tooltipSupplier,
            BooleanSupplier enabled
    ) {
        this(
                x,
                y,
                width,
                height,
                labelSupplier,
                action,
                colorSupplier,
                hoverColorSupplier,
                textColor,
                centered,
                tooltipSupplier,
                enabled,
                false
        );
    }

    private ActionBlock(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            Runnable action,
            IntSupplier colorSupplier,
            IntSupplier hoverColorSupplier,
            int textColor,
            boolean centered,
            Supplier<Component> tooltipSupplier,
            BooleanSupplier enabled,
            boolean resetIcon
    ) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.labelSupplier = labelSupplier;
        this.action = action;
        this.colorSupplier = colorSupplier;
        this.hoverColorSupplier = hoverColorSupplier;
        this.textColor = textColor;
        this.centered = centered;
        this.tooltipSupplier = tooltipSupplier;
        this.enabled = enabled == null ? () -> true : enabled;
        this.resetIcon = resetIcon;
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
        if (enabled()) {
            action.run();
        }
    }

    public int color() {
        return colorSupplier.getAsInt();
    }

    public int hoverColor() {
        return hoverColorSupplier.getAsInt();
    }

    public int textColor() {
        return textColor;
    }

    public boolean centered() {
        return centered;
    }

    public Component tooltip() {
        return tooltipSupplier == null ? null : tooltipSupplier.get();
    }

    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    public boolean resetIcon() {
        return resetIcon;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
