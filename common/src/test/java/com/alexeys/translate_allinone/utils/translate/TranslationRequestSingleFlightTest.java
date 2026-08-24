package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void keepsSuccessfulFlightClaimedWhileFollowersAreReleased() {
        TranslationRequestSingleFlight<String, String> requests = new TranslationRequestSingleFlight<>();
        TranslationRequestSingleFlight.Claim<String> owner = requests.acquire("key");
        AtomicReference<TranslationRequestSingleFlight.Claim<String>> reentrantClaim = new AtomicReference<>();
        owner.future().thenRun(() -> reentrantClaim.set(requests.acquire("key")));

        assertTrue(requests.complete("key", owner, "translated"));

        assertFalse(reentrantClaim.get().owner());
        assertEquals("translated", reentrantClaim.get().future().join());
        assertTrue(requests.acquire("key").owner());
    }

    @Test
    void keepsFailedFlightClaimedWhileFollowersAreReleased() {
        TranslationRequestSingleFlight<String, String> requests = new TranslationRequestSingleFlight<>();
        TranslationRequestSingleFlight.Claim<String> owner = requests.acquire("key");
        AtomicReference<TranslationRequestSingleFlight.Claim<String>> reentrantClaim = new AtomicReference<>();
        owner.future().whenComplete((value, error) -> reentrantClaim.set(requests.acquire("key")));

        assertTrue(requests.fail("key", owner, new IllegalStateException("failed")));

        assertFalse(reentrantClaim.get().owner());
        CompletionException failure = assertThrows(CompletionException.class, reentrantClaim.get().future()::join);
        assertEquals("failed", failure.getCause().getMessage());
        assertTrue(requests.acquire("key").owner());
    }
}
