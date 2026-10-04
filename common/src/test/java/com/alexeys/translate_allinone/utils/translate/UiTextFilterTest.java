package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextFilterTest {
    @Test
    void skipsHexColorLiterals() {
        for (String literal : List.of("#fff", "#FFFF", "#FFAA00", "#0000007D", "#5DC6FFFF")) {
            UiTextFilter.Decision decision = UiTextFilter.evaluate(literal, UiTextRole.OPTION, false);

            assertFalse(decision.eligible(), literal);
            assertEquals(UiTextFilter.Reason.HEX_COLOR, decision.reason(), literal);
        }
    }

    @Test
    void keepsWordsThatOnlyLookLikeHexBecauseTheyLackTheHash() {
        for (String word : List.of("cafe", "beef", "dead", "added", "decade", "facade")) {
            UiTextFilter.Decision decision = UiTextFilter.evaluate(word, UiTextRole.OPTION, false);

            assertTrue(decision.eligible(), word);
            assertEquals(word, decision.text());
        }
    }

    @Test
    void keepsTextThatStartsLikeAHexColorButCarriesMore() {
        for (String text : List.of("#FFAA00 highlight", "background #FFAA00", "#GGGGGG", "#ABCDEFG")) {
            UiTextFilter.Decision decision = UiTextFilter.evaluate(text, UiTextRole.OPTION, false);

            assertTrue(decision.eligible(), text);
        }
    }

    @Test
    void letterlessTextIsStillRejectedBeforeTheHexColorRule() {
        UiTextFilter.Decision decision = UiTextFilter.evaluate("#12", UiTextRole.OPTION, false);

        assertFalse(decision.eligible());
        assertEquals(UiTextFilter.Reason.NO_LETTERS, decision.reason());
    }
}
