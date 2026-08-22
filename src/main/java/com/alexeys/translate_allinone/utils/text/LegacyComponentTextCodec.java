package com.alexeys.translate_allinone.utils.text;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class LegacyComponentTextCodec {
    private LegacyComponentTextCodec() {
    }

    public static Text decode(String legacyText) {
        return StylePreserver.fromLegacyText(legacyText);
    }

    public static String encode(Text text) {
        if (text == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        Style[] previousStyle = {null};
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            Style currentStyle = style == null ? Style.EMPTY : style;
            if (previousStyle[0] == null) {
                appendStyleCodes(builder, currentStyle, false);
            } else if (!Objects.equals(previousStyle[0], currentStyle)) {
                appendStyleCodes(builder, currentStyle, !previousStyle[0].isEmpty());
            }

            builder.append(string);
            previousStyle[0] = currentStyle;
            return Optional.empty();
        }, Style.EMPTY);
        return builder.toString();
    }

    private static void appendStyleCodes(StringBuilder builder, Style style, boolean resetBeforeStyle) {
        if (style == null || style.isEmpty()) {
            if (resetBeforeStyle) {
                builder.append("\u00A7r");
            }
            return;
        }

        if (resetBeforeStyle) {
            builder.append("\u00A7r");
        }

        if (style.getColor() != null) {
            boolean appendedFormattingColor = false;
            for (Formatting formatting : Formatting.values()) {
                if (formatting.isColor()
                        && formatting.getColorValue() != null
                        && formatting.getColorValue().equals(style.getColor().getRgb())) {
                    builder.append('\u00A7').append(formatting.getCode());
                    appendedFormattingColor = true;
                    break;
                }
            }
            if (!appendedFormattingColor) {
                builder.append("\u00A7").append(toExtendedHexColor(style.getColor()));
            }
        }
        if (style.isBold()) builder.append("\u00A7l");
        if (style.isItalic()) builder.append("\u00A7o");
        if (style.isUnderlined()) builder.append("\u00A7n");
        if (style.isStrikethrough()) builder.append("\u00A7m");
        if (style.isObfuscated()) builder.append("\u00A7k");
    }

    private static String toExtendedHexColor(TextColor color) {
        int rgb = color.getRgb();
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return String.format("#%02x%02x%02xff", red, green, blue);
    }
}
