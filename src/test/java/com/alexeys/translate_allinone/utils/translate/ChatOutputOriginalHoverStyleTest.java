package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
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
        Component original = Component.literal("Hello");
        HoverEvent hoverEvent = new HoverEvent.ShowText(
                ChatOutputOriginalHoverStyle.markComponent(original)
        );

        assertTrue(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(hoverEvent));
    }

    @Test
    void ignoresUnmarkedHoverEvent() {
        HoverEvent hoverEvent = new HoverEvent.ShowText(Component.literal("Hello"));

        assertFalse(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(hoverEvent));
    }

    @Test
    void isMarkedHoverEventHandlesNull() {
        assertFalse(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(null));
    }

    @Test
    void markSurvivesComponentCopy() {
        Component original = Component.literal("Hello");
        Component translated = Component.literal("Translated").withStyle(
                Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                        ChatOutputOriginalHoverStyle.markComponent(original)
                ))
        );

        Component copied = translated.copy();

        assertTrue(ChatOutputOriginalHoverStyle.isMarkedHoverEvent(copied.getStyle().getHoverEvent()));
    }
}
