package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputOriginalHoverStyleTest {
    @Test
    void marksStyleAndDetectsMarker() {
        Style source = Style.EMPTY.withColor(0xFF0000);

        Style marked = ChatOutputOriginalHoverStyle.mark(source);

        assertTrue(ChatOutputOriginalHoverStyle.isMarked(marked));
        assertFalse(ChatOutputOriginalHoverStyle.isMarked(source));
    }

    @Test
    void isMarkedHandlesNullStyle() {
        assertFalse(ChatOutputOriginalHoverStyle.isMarked(null));
    }

    @Test
    void marksOriginalHoverEvent() {
        Text original = Text.literal("Hello");
        HoverEvent hoverEvent = new HoverEvent.ShowText(
                ChatOutputOriginalHoverStyle.markComponent(original)
        );

        assertTrue(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(hoverEvent));
    }

    @Test
    void ignoresUnmarkedHoverEvent() {
        HoverEvent hoverEvent = new HoverEvent.ShowText(Text.literal("Hello"));

        assertFalse(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(hoverEvent));
    }

    @Test
    void isMarkedHoverEventHandlesNull() {
        assertFalse(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(null));
    }

    @Test
    void markSurvivesComponentCopy() {
        Text original = Text.literal("Hello");
        MutableText translated = Text.literal("Translated");
        translated.setStyle(
                Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                        ChatOutputOriginalHoverStyle.markComponent(original)
                ))
        );

        Text copied = translated.copy();

        assertTrue(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(copied.getStyle().getHoverEvent()));
    }
}
