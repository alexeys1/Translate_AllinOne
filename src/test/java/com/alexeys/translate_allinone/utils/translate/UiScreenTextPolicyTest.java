package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScreenTextPolicyTest {
    @Test
    void acceptsVisibleTextWithDecorativeGlyph() {
        String glyph = "\uE000";

        UiTextFilter.Decision decision = UiScreenTextPolicy.evaluate(
                glyph + " Settings",
                UiTextRole.OPTION,
                false
        );

        assertTrue(decision.eligible());
        assertEquals(glyph + " Settings", decision.text());
        assertNull(decision.reason());
        assertEquals(Set.of(glyph), UiScreenTextPolicy.decorativeGlyphs(decision.text()));
    }

    @Test
    void extractsSupplementaryDecorativeGlyphsWithoutDuplicates() {
        String glyph = new String(Character.toChars(0xF0000));

        assertTrue(UiTextFilter.containsPrivateUseCodePoint(glyph + " Option " + glyph));
        assertEquals(Set.of(glyph), UiScreenTextPolicy.decorativeGlyphs(glyph + " Option " + glyph));
    }

    @Test
    void rejectsDecorativeGlyphWithoutVisibleLetters() {
        UiTextFilter.Decision decision = UiScreenTextPolicy.evaluate("\uE000", UiTextRole.OPTION, false);

        assertFalse(decision.eligible());
        assertEquals(UiTextFilter.Reason.NO_LETTERS, decision.reason());
    }

    @Test
    void keepsDecorativeGlyphUserInputPrivate() {
        UiTextFilter.Decision decision = UiScreenTextPolicy.evaluate("\uE000", UiTextRole.OPTION, true);

        assertFalse(decision.eligible());
        assertEquals(UiTextFilter.Reason.USER_INPUT, decision.reason());
    }
}
