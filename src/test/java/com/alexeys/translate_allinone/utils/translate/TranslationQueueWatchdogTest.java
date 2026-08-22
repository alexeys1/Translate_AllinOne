package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TranslationQueueWatchdogTest {
    @Test
    void tripsOnlyAfterEveryActiveTaskHasStalled() {
        TranslationQueueWatchdog.State state = state();
        state.start(1L, "item", List.of("1", "2", "3"), 0L);
        state.start(2L, "scoreboard", List.of("4", "5", "6"), 1_000L);

        assertNull(state.checkStalled(120_999L));

        TranslationQueueWatchdog.Trip trip = state.checkStalled(121_000L);
        assertEquals(TranslationQueueWatchdog.Reason.STALLED_TASKS, trip.reason());
        assertEquals(6, trip.affectedTaskCount());
        assertNull(state.checkStalled(300_000L));
    }

    @Test
    void rechecksOldTasksWhenAYoungerRequestFinishes() {
        TranslationQueueWatchdog.State state = state();
        state.start(1L, "item", List.of("1", "2", "3", "4", "5", "6"), 0L);
        state.start(2L, "chat", List.of("7"), 119_000L);

        assertNull(state.checkStalled(120_000L));

        TranslationQueueWatchdog.Trip trip = state.finish(2L, false, List.of(), false, 120_001L);
        assertEquals(TranslationQueueWatchdog.Reason.STALLED_TASKS, trip.reason());
        assertEquals(6, trip.affectedTaskCount());
    }

    @Test
    void tripsAfterThreeDistinctTasksExhaustRetries() {
        TranslationQueueWatchdog.State state = state();

        assertNull(fail(state, 1L, "a", true, 1_000L));
        assertNull(fail(state, 2L, "b", true, 2_000L));
        TranslationQueueWatchdog.Trip trip = fail(state, 3L, "c", true, 3_000L);

        assertEquals(TranslationQueueWatchdog.Reason.RETRIES_EXHAUSTED, trip.reason());
        assertEquals(3, trip.affectedTaskCount());
    }

    @Test
    void ordinaryFailuresRequireThreeAttemptsForEachTask() {
        TranslationQueueWatchdog.State state = state();
        long requestId = 0L;

        for (int attempt = 0; attempt < 2; attempt++) {
            for (String task : List.of("a", "b", "c")) {
                requestId++;
                assertNull(fail(state, requestId, task, false, requestId * 1_000L));
            }
        }

        assertNull(fail(state, ++requestId, "a", false, requestId * 1_000L));
        assertNull(fail(state, ++requestId, "b", false, requestId * 1_000L));
        TranslationQueueWatchdog.Trip trip = fail(state, ++requestId, "c", false, requestId * 1_000L);

        assertEquals(TranslationQueueWatchdog.Reason.RETRIES_EXHAUSTED, trip.reason());
        assertEquals(3, trip.affectedTaskCount());
    }

    @Test
    void expiredFailuresDoNotAccumulate() {
        TranslationQueueWatchdog.State state = state();

        assertNull(fail(state, 1L, "a", false, 0L));
        assertNull(fail(state, 2L, "a", false, 1_000L));
        assertNull(fail(state, 3L, "a", false, 62_000L));
        assertNull(fail(state, 4L, "b", true, 63_000L));
        assertNull(fail(state, 5L, "c", true, 64_000L));
    }

    private static TranslationQueueWatchdog.State state() {
        return new TranslationQueueWatchdog.State(6, 120_000L, 3, 3, 60_000L);
    }

    private static TranslationQueueWatchdog.Trip fail(
            TranslationQueueWatchdog.State state,
            long requestId,
            String task,
            boolean retriesExhausted,
            long nowMillis
    ) {
        state.start(requestId, "test", List.of(task), nowMillis);
        return state.finish(requestId, true, List.of(), retriesExhausted, nowMillis);
    }
}
