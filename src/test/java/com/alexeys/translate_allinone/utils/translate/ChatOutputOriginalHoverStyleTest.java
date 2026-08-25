package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Style;
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
}
