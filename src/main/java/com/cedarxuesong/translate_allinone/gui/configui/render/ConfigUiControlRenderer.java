package com.cedarxuesong.translate_allinone.gui.configui.render;

import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.CheckboxBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.GroupBox;
import com.cedarxuesong.translate_allinone.gui.configui.controls.IntSliderBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.StaticTextRow;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiDraw.drawOutline;

public final class ConfigUiControlRenderer {
    private ConfigUiControlRenderer() {
    }

    public static void drawActionBlocks(
            GuiGraphicsExtractor context,
            Font textRenderer,
            List<ActionBlock> blocks,
            int mouseX,
            int mouseY,
            int borderColor
    ) {
        for (ActionBlock block : blocks) {
            boolean hovered = block.contains(mouseX, mouseY);
            context.fill(block.x(), block.y(), block.x() + block.width(), block.y() + block.height(), hovered ? block.hoverColor() : block.color());
            drawOutline(context, block.x(), block.y(), block.width(), block.height(), borderColor);

            Component label = trimText(textRenderer, block.label(), Math.max(0, block.width() - 12));
            int textY = block.y() + 6;
            int textX = block.centered()
                    ? block.x() + (block.width() - textRenderer.width(label)) / 2
                    : block.x() + 6;
            context.text(textRenderer, label, textX, textY, block.textColor(), false);
        }
    }

    public static void drawSliderBlocks(GuiGraphicsExtractor context, List<IntSliderBlock> sliders, int mouseX, int mouseY) {
        for (IntSliderBlock slider : sliders) {
            slider.render(context, mouseX, mouseY);
        }
    }

    public static void drawGroupBoxes(GuiGraphicsExtractor context, Font textRenderer, List<GroupBox> boxes) {
        for (GroupBox box : boxes) {
            if (box.width() <= 0 || box.height() <= 0) {
                continue;
            }

            GroupBox.Style style = box.style();
            context.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), style.backgroundColor());
            drawOutline(context, box.x(), box.y(), box.width(), box.height(), style.borderColor());

            Component title = box.title();
            if (title == null || title.getString().isBlank()) {
                continue;
            }

            int titleX = box.x() + 10;
            int titleY = box.y() + 5;
            int titleWidth = textRenderer.width(title);
            context.fill(titleX - 3, titleY - 1, titleX + titleWidth + 3, titleY + 9, style.titleBackgroundColor());
            context.text(textRenderer, title, titleX, titleY, style.titleColor(), false);
        }
    }

    public static void drawCheckboxBlocks(
            GuiGraphicsExtractor context,
            Font textRenderer,
            List<CheckboxBlock> blocks,
            int mouseX,
            int mouseY
    ) {
        for (CheckboxBlock block : blocks) {
            block.render(context, textRenderer, mouseX, mouseY);
        }
    }

    public static void drawStaticTextRows(GuiGraphicsExtractor context, Font textRenderer, List<StaticTextRow> rows) {
        for (StaticTextRow row : rows) {
            int labelX = row.x() + 6;
            int valueX = row.x() + row.labelWidth() + 6;
            int textY = row.y() + 6;

            int labelMaxWidth = Math.max(0, row.labelWidth() - 12);
            int valueMaxWidth = Math.max(0, row.width() - row.labelWidth() - 12);

            Component label = trimText(textRenderer, row.label(), labelMaxWidth);
            Component value = trimText(textRenderer, row.value(), valueMaxWidth);

            context.text(textRenderer, label, labelX, textY, row.labelColor(), false);
            context.text(textRenderer, value, valueX, textY, row.valueColor(), false);
        }
    }

    private static Component trimText(Font textRenderer, Component text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return Component.empty();
        }

        String content = text.getString();
        if (textRenderer.width(content) <= maxWidth) {
            return text;
        }

        int ellipsisWidth = textRenderer.width("...");
        if (ellipsisWidth >= maxWidth) {
            return Component.literal(textRenderer.plainSubstrByWidth(content, maxWidth));
        }

        String trimmed = textRenderer.plainSubstrByWidth(content, maxWidth - ellipsisWidth);
        return Component.literal(trimmed + "...");
    }
}
