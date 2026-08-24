package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationRequestLimiterTest {
    @Test
    void blocksAdditionalRequestUntilPermitIsReleased() throws InterruptedException {
        TranslationRequestLimiter limiter = new TranslationRequestLimiter(1);
        TranslationRequestLimiter.Permit first = limiter.acquire();

        assertNull(limiter.tryAcquire());

        first.close();
        try (TranslationRequestLimiter.Permit second = limiter.tryAcquire()) {
            assertNotNull(second);
        }
    }

    @Test
    void releasingPermitTwiceDoesNotIncreaseCapacity() throws InterruptedException {
        TranslationRequestLimiter limiter = new TranslationRequestLimiter(1);
        TranslationRequestLimiter.Permit first = limiter.acquire();

        first.close();
        first.close();

        try (TranslationRequestLimiter.Permit second = limiter.tryAcquire()) {
            assertNotNull(second);
            assertNull(limiter.tryAcquire());
        }
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new TranslationRequestLimiter(0));
    }
}
