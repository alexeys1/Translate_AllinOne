package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynnDialogueDisplayModeSupportTest {
    @Test
    void resolvesPresentationWhenTranslationIsVisible() {
        assertTrue(WynnDialogueDisplayModeSupport.shouldResolvePresentation(true, false));
    }

    @Test
    void resolvesPresentationWhileForcedRefreshIsPending() {
        assertTrue(WynnDialogueDisplayModeSupport.shouldResolvePresentation(false, true));
    }

    @Test
    void skipsPresentationWhenNeitherConditionApplies() {
        assertFalse(WynnDialogueDisplayModeSupport.shouldResolvePresentation(false, false));
    }
}
