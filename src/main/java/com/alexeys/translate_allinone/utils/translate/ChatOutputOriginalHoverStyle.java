package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class ChatOutputOriginalHoverStyle {
    private static final String INSERTION = "translate_allinone:chat_output_original_hover";

    private ChatOutputOriginalHoverStyle() {
    }

    public static Style mark(Style style) {
        return (style == null ? Style.EMPTY : style).withInsertion(INSERTION);
    }

    public static boolean isMarked(Style style) {
        return style != null && INSERTION.equals(style.getInsertion());
    }

    public static Text markComponent(Text component) {
        if (component == null) {
            return null;
        }
        MutableText copy = component.copy();
        copy.setStyle(mark(copy.getStyle()));
        return copy;
    }

    public static boolean isMarkedHoverEvent(HoverEvent hoverEvent) {
        if (!(hoverEvent instanceof HoverEvent.ShowText showText)) {
            return false;
        }
        Text value = showText.value();
        return value != null && isMarked(value.getStyle());
    }
}
