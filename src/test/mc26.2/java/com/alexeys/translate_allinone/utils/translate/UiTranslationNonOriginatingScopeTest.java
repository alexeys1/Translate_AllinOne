package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTranslationNonOriginatingScopeTest {
    @Test
    void requestsMayOriginateOutsideTheScope() {
        assertTrue(UiTranslationRuntime.mayOriginateRequest());
    }

    @Test
    void requestsMayNotOriginateInsideTheScope() {
        boolean inside = UiTranslationRuntime.withoutOriginatingRequests(
                UiTranslationRuntime::mayOriginateRequest
        );

        assertFalse(inside);
        assertTrue(UiTranslationRuntime.mayOriginateRequest());
    }

    @Test
    void nestedScopesRestoreTheOuterState() {
        UiTranslationRuntime.withoutOriginatingRequests(() -> {
            assertFalse(UiTranslationRuntime.mayOriginateRequest());
            boolean inner = UiTranslationRuntime.withoutOriginatingRequests(
                    UiTranslationRuntime::mayOriginateRequest
            );
            assertFalse(inner);
            assertFalse(UiTranslationRuntime.mayOriginateRequest());
            return null;
        });

        assertTrue(UiTranslationRuntime.mayOriginateRequest());
    }

    @Test
    void scopeIsReleasedWhenTheWrappedCallThrows() {
        assertThrows(IllegalStateException.class, () -> UiTranslationRuntime.withoutOriginatingRequests(() -> {
            throw new IllegalStateException("boom");
        }));

        assertTrue(UiTranslationRuntime.mayOriginateRequest());
    }

    @Test
    void wrappedCallStillReturnsItsValue() {
        assertEquals("value", UiTranslationRuntime.withoutOriginatingRequests(() -> "value"));
    }
}
