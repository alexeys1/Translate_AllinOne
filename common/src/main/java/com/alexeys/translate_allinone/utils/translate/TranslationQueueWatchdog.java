package com.alexeys.translate_allinone.utils.translate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TranslationQueueWatchdog {
    static final int STALLED_TASK_THRESHOLD = 6;
    static final long STALL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
    static final int FAILURE_ATTEMPT_THRESHOLD = 3;
    static final int EXHAUSTED_TASK_THRESHOLD = 3;
    static final long FAILURE_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static volatile Consumer<Trip> tripHandler = trip -> {};
    private static final State STATE = new State(
            STALLED_TASK_THRESHOLD,
            STALL_TIMEOUT_MILLIS,
            FAILURE_ATTEMPT_THRESHOLD,
            EXHAUSTED_TASK_THRESHOLD,
            FAILURE_WINDOW_MILLIS
    );

    private TranslationQueueWatchdog() {
    }

    public static void configureTripHandler(Consumer<Trip> handler) {
        tripHandler = Objects.requireNonNull(handler);
    }

    public static long requestStarted(String source, Collection<String> taskKeys) {
        long requestId = REQUEST_IDS.incrementAndGet();
        STATE.start(requestId, source, taskKeys, nowMillis());
        CompletableFuture.delayedExecutor(STALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> tripIfNeeded(STATE.checkStalled(nowMillis())));
        return requestId;
    }

    public static void requestSucceeded(long requestId) {
        tripIfNeeded(STATE.finish(requestId, false, List.of(), false, nowMillis()));
    }

    public static void requestFailed(long requestId, boolean retriesExhausted) {
        tripIfNeeded(STATE.finish(requestId, true, List.of(), retriesExhausted, nowMillis()));
    }

    public static void requestCompleted(
            long requestId,
            Collection<String> failedTaskKeys,
            boolean retriesExhausted
    ) {
        tripIfNeeded(STATE.finish(requestId, false, failedTaskKeys, retriesExhausted, nowMillis()));
    }

    public static void requestSuperseded(long requestId) {
        tripIfNeeded(STATE.cancel(requestId, nowMillis()));
    }

    public static void reset() {
        STATE.reset();
    }

    private static long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static void tripIfNeeded(Trip trip) {
        if (trip != null) {
            tripHandler.accept(trip);
        }
    }

    public enum Reason {
        STALLED_TASKS,
        RETRIES_EXHAUSTED
    }

    public record Trip(Reason reason, int affectedTaskCount) {
    }

    static final class State {
        private final int stalledTaskThreshold;
        private final long stallTimeoutMillis;
        private final int failureAttemptThreshold;
        private final int exhaustedTaskThreshold;
        private final long failureWindowMillis;
        private final Map<Long, ActiveRequest> activeRequests = new LinkedHashMap<>();
        private final Map<String, FailureProgress> failures = new LinkedHashMap<>();
        private final Map<String, Long> exhaustedTasks = new LinkedHashMap<>();

        State(
                int stalledTaskThreshold,
                long stallTimeoutMillis,
                int failureAttemptThreshold,
                int exhaustedTaskThreshold,
                long failureWindowMillis
        ) {
            this.stalledTaskThreshold = Math.max(1, stalledTaskThreshold);
            this.stallTimeoutMillis = Math.max(1L, stallTimeoutMillis);
            this.failureAttemptThreshold = Math.max(1, failureAttemptThreshold);
            this.exhaustedTaskThreshold = Math.max(1, exhaustedTaskThreshold);
            this.failureWindowMillis = Math.max(1L, failureWindowMillis);
        }

        synchronized void start(long requestId, String source, Collection<String> taskKeys, long nowMillis) {
            String normalizedSource = source == null || source.isBlank() ? "translation" : source.trim();
            Map<String, String> identitiesByKey = new LinkedHashMap<>();
            if (taskKeys != null) {
                for (String taskKey : taskKeys) {
                    if (taskKey != null && !taskKey.isBlank()) {
                        identitiesByKey.put(taskKey, normalizedSource + '\u0000' + taskKey);
                    }
                }
            }
            if (identitiesByKey.isEmpty()) {
                String taskKey = "request-" + requestId;
                identitiesByKey.put(taskKey, normalizedSource + '\u0000' + taskKey);
            }
            activeRequests.put(requestId, new ActiveRequest(nowMillis, identitiesByKey));
        }

        synchronized Trip finish(
                long requestId,
                boolean failAll,
                Collection<String> failedTaskKeys,
                boolean retriesExhausted,
                long nowMillis
        ) {
            ActiveRequest request = activeRequests.remove(requestId);
            if (request == null) {
                return null;
            }

            Set<String> failedIdentities = new LinkedHashSet<>();
            if (failAll) {
                failedIdentities.addAll(request.identitiesByKey().values());
            } else if (failedTaskKeys != null) {
                for (String failedTaskKey : failedTaskKeys) {
                    String identity = request.identitiesByKey().get(failedTaskKey);
                    if (identity != null) {
                        failedIdentities.add(identity);
                    }
                }
            }

            for (String identity : request.identitiesByKey().values()) {
                if (failedIdentities.contains(identity)) {
                    recordFailure(identity, retriesExhausted, nowMillis);
                } else {
                    failures.remove(identity);
                    exhaustedTasks.remove(identity);
                }
            }

            purgeExpiredFailures(nowMillis);
            if (exhaustedTasks.size() < exhaustedTaskThreshold) {
                return checkStalled(nowMillis);
            }
            Trip trip = new Trip(Reason.RETRIES_EXHAUSTED, exhaustedTasks.size());
            reset();
            return trip;
        }

        synchronized Trip cancel(long requestId, long nowMillis) {
            activeRequests.remove(requestId);
            return checkStalled(nowMillis);
        }

        synchronized Trip checkStalled(long nowMillis) {
            int activeTaskCount = activeRequests.values().stream()
                    .mapToInt(request -> request.identitiesByKey().size())
                    .sum();
            if (activeTaskCount < stalledTaskThreshold) {
                return null;
            }
            long cutoff = nowMillis - stallTimeoutMillis;
            for (ActiveRequest request : activeRequests.values()) {
                if (request.startedAtMillis() > cutoff) {
                    return null;
                }
            }
            Trip trip = new Trip(Reason.STALLED_TASKS, activeTaskCount);
            reset();
            return trip;
        }

        synchronized void reset() {
            activeRequests.clear();
            failures.clear();
            exhaustedTasks.clear();
        }

        private void recordFailure(String identity, boolean retriesExhausted, long nowMillis) {
            FailureProgress previous = failures.get(identity);
            int attempts = previous == null || nowMillis - previous.lastFailureMillis() > failureWindowMillis
                    ? 1
                    : previous.attempts() + 1;
            failures.put(identity, new FailureProgress(attempts, nowMillis));
            if (retriesExhausted || attempts >= failureAttemptThreshold) {
                exhaustedTasks.put(identity, nowMillis);
            }
        }

        private void purgeExpiredFailures(long nowMillis) {
            failures.entrySet().removeIf(entry -> nowMillis - entry.getValue().lastFailureMillis() > failureWindowMillis);
            exhaustedTasks.entrySet().removeIf(entry -> nowMillis - entry.getValue() > failureWindowMillis);
        }
    }

    private record ActiveRequest(long startedAtMillis, Map<String, String> identitiesByKey) {
    }

    private record FailureProgress(int attempts, long lastFailureMillis) {
    }
}
