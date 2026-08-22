package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextFilterTest {
    @Test
    void acceptsNaturalUiText() {
        UiTextFilter.Decision decision = UiTextFilter.evaluate(
                "Enable module",
                UiTextRole.OPTION,
                false
        );

        assertTrue(decision.eligible());
        assertTrue(decision.text().equals("Enable module"));
    }

    @Test
    void skipsInputCommandsUrlsPathsNumbersAndKeys() {
        assertFalse(UiTextFilter.evaluate("/config reload", UiTextRole.VALUE, false).eligible());
        assertFalse(UiTextFilter.evaluate("https://example.com", UiTextRole.VALUE, false).eligible());
        assertFalse(UiTextFilter.evaluate("C:\\config\\mod.json", UiTextRole.VALUE, false).eligible());
        assertFalse(UiTextFilter.evaluate("120 ms", UiTextRole.VALUE, false).eligible());
        assertFalse(UiTextFilter.evaluate("module.option", UiTextRole.VALUE, false).eligible());
        assertFalse(UiTextFilter.evaluate("typed search", UiTextRole.VALUE, true).eligible());
    }

    @Test
    void skipsDecorativeGlyphs() {
        assertFalse(UiTextFilter.evaluate("Name \uE000", UiTextRole.MODULE, false).eligible());
    }
}
