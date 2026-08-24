package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationRequestSingleFlightTest {
    @Test
    void sharesOneFutureForTheSameKey() {
        TranslationRequestSingleFlight<String, String> requests = new TranslationRequestSingleFlight<>();

        TranslationRequestSingleFlight.Claim<String> owner = requests.acquire("key");
        TranslationRequestSingleFlight.Claim<String> follower = requests.acquire("key");

        assertTrue(owner.owner());
        assertFalse(follower.owner());
        assertTrue(requests.complete("key", owner, "translated"));
        assertEquals("translated", follower.future().join());
        assertTrue(requests.acquire("key").owner());
    }

    @Test
    void cancelsEveryFollowerWhenTheSessionEnds() {
        TranslationRequestSingleFlight<String, String> requests = new TranslationRequestSingleFlight<>();
        TranslationRequestSingleFlight.Claim<String> owner = requests.acquire("key");
        TranslationRequestSingleFlight.Claim<String> follower = requests.acquire("key");

        requests.cancelAll();

        assertThrows(CancellationException.class, owner.future()::join);
        assertThrows(CancellationException.class, follower.future()::join);
    }
}
