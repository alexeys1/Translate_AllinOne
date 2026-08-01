package com.cedarxuesong.translate_allinone.utils.text;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class LegacyComponentTextCodec {
    private LegacyComponentTextCodec() {
    }

    public static Component decode(String legacyText) {
        return StylePreserver.fromLegacyText(legacyText);
    }

    public static String encode(Component component) {
        if (component == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        Style[] previousStyle = {null};
        component.visit((style, string) -> {
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
                builder.append("§r");
            }
            return;
        }

        if (resetBeforeStyle) {
            builder.append("§r");
        }

        if (style.getColor() != null) {
            boolean appendedFormattingColor = false;
            for (ChatFormatting formatting : ChatFormatting.values()) {
                TextColor legacyColor = TextColor.fromLegacyFormat(formatting);
                if (legacyColor != null && legacyColor.getValue() == style.getColor().getValue()) {
                    builder.append(formatting);
                    appendedFormattingColor = true;
                    break;
                }
            }
            if (!appendedFormattingColor) {
                builder.append("§").append(toExtendedHexColor(style.getColor()));
            }
        }
        if (style.isBold()) builder.append("§l");
        if (style.isItalic()) builder.append("§o");
        if (style.isUnderlined()) builder.append("§n");
        if (style.isStrikethrough()) builder.append("§m");
        if (style.isObfuscated()) builder.append("§k");
    }

    private static String toExtendedHexColor(TextColor color) {
        int rgb = color.getValue();
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return String.format("#%02x%02x%02xff", red, green, blue);
    }
}
