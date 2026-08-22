package com.alexeys.translate_allinone.utils.translate;

import java.util.concurrent.atomic.AtomicLong;

public final class TranslationFeatureGate {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile boolean enabled = true;

    private TranslationFeatureGate() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static boolean isActive(long expectedGeneration) {
        return enabled && expectedGeneration == GENERATION.get();
    }

    public static synchronized boolean update(boolean nextEnabled) {
        if (enabled == nextEnabled) {
            return false;
        }
        enabled = nextEnabled;
        GENERATION.incrementAndGet();
        return true;
    }
}
