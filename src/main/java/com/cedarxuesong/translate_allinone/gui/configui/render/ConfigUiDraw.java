package com.cedarxuesong.translate_allinone.gui.configui.render;

import com.cedarxuesong.translate_allinone.gui.configui.model.UiRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class ConfigUiDraw {
    private ConfigUiDraw() {
    }

    public static void drawOutline(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static final int TOOLTIP_BG = 0xE61A1A1A;
    private static final int TOOLTIP_BORDER = 0xCC3A3A3A;
    private static final int TOOLTIP_TEXT = 0xFFE0E0E0;
    private static final int TOOLTIP_MAX_WIDTH = 280;
    private static final int TOOLTIP_PADDING_X = 8;
    private static final int TOOLTIP_PADDING_Y = 4;
    private static final int TOOLTIP_MOUSE_OFFSET = 12;

    public static void drawTooltip(
            DrawContext context,
            TextRenderer textRenderer,
            Text tooltip,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight
    ) {
        if (tooltip == null || tooltip.getString().isEmpty()) {
            return;
        }

        List<OrderedText> lines = textRenderer.wrapLines(tooltip, TOOLTIP_MAX_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        int lineHeight = textRenderer.fontHeight + 1;
        int textWidth = 0;
        for (OrderedText line : lines) {
            int w = textRenderer.getWidth(line);
            if (w > textWidth) {
                textWidth = w;
            }
        }

        int boxWidth = textWidth + TOOLTIP_PADDING_X * 2;
        int boxHeight = lines.size() * lineHeight + TOOLTIP_PADDING_Y * 2;

        int boxX = mouseX + TOOLTIP_MOUSE_OFFSET;
        int boxY = mouseY + TOOLTIP_MOUSE_OFFSET;

        if (boxX + boxWidth > screenWidth - 4) {
            boxX = screenWidth - 4 - boxWidth;
        }
        if (boxY + boxHeight > screenHeight - 4) {
            boxY = mouseY - boxHeight - TOOLTIP_MOUSE_OFFSET;
        }
        if (boxX < 4) {
            boxX = 4;
        }
        if (boxY < 4) {
            boxY = 4;
        }

        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, TOOLTIP_BG);
        drawOutline(context, boxX, boxY, boxWidth, boxHeight, TOOLTIP_BORDER);

        int textY = boxY + TOOLTIP_PADDING_Y;
        for (OrderedText line : lines) {
            context.drawText(textRenderer, line, boxX + TOOLTIP_PADDING_X, textY, TOOLTIP_TEXT, false);
            textY += lineHeight;
        }
    }

    public static void withScissor(DrawContext context, UiRect rect, Runnable drawer) {
        if (drawer == null) {
            return;
        }

        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            drawer.run();
            return;
        }

        context.enableScissor(rect.x, rect.y, rect.right(), rect.bottom());
        try {
            drawer.run();
        } finally {
            context.disableScissor();
        }
    }
}
