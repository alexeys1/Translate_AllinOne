package com.alexeys.translate_allinone.utils.translate;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TranslationRequestLimiter {
    private final Semaphore permits;

    public TranslationRequestLimiter(int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be positive");
        }
        permits = new Semaphore(maxInFlight, true);
    }

    public Permit acquire() throws InterruptedException {
        permits.acquire();
        return new Permit(permits);
    }

    Permit tryAcquire() {
        return permits.tryAcquire() ? new Permit(permits) : null;
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
