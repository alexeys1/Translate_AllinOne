package com.alexeys.translate_allinone.utils.translate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ChatOutputRetryBudget {
    static final int PROVIDER_CALLS_PER_ROUND = 2;
    static final int AUTO_RETRY_ROUNDS = 2;
    static final int MAX_PROVIDER_CALLS = PROVIDER_CALLS_PER_ROUND * AUTO_RETRY_ROUNDS;
    static final long IN_ROUND_BACKOFF_MILLIS = 2_000L;
    static final long ROUND_COOLDOWN_MILLIS = 5_000L;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    boolean claimAutomaticRetry(String key, long nowMillis) {
        if (key == null) {
            return false;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return true;
        }
        synchronized (entry) {
            if (entry.roundsCompleted >= AUTO_RETRY_ROUNDS) {
                return false;
            }
            return nowMillis >= entry.cooldownUntilMillis;
        }
    }

    boolean canRetryInRound(String key, int callsInRound) {
        if (callsInRound >= PROVIDER_CALLS_PER_ROUND) {
            return false;
        }
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null) {
            return true;
        }
        synchronized (entry) {
            return entry.providerCalls < MAX_PROVIDER_CALLS;
        }
    }

    void recordProviderCall(String key) {
        if (key == null) {
            return;
        }
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        synchronized (entry) {
            entry.providerCalls++;
        }
    }

    void recordFailedRound(String key, long nowMillis) {
        if (key == null) {
            return;
        }
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        synchronized (entry) {
            entry.roundsCompleted++;
            entry.cooldownUntilMillis = nowMillis + ROUND_COOLDOWN_MILLIS;
        }
    }

    void reset(String key) {
        if (key == null) {
            return;
        }
        entries.remove(key);
    }

    void clear() {
        entries.clear();
    }

    private static final class Entry {
        private int roundsCompleted;
        private int providerCalls;
        private long cooldownUntilMillis;
    }
}
