package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.llmapi.LlmRequestLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TranslationQueueResetCoordinator {
    private static final AtomicBoolean CLEARING = new AtomicBoolean();

    private TranslationQueueResetCoordinator() {
    }

    public static void register() {
        TranslationQueueWatchdog.configureTripHandler(TranslationQueueResetCoordinator::clearAll);
    }

    private static void clearAll(TranslationQueueWatchdog.Trip trip) {
        if (trip == null || !CLEARING.compareAndSet(false, true)) {
            return;
        }
        try {
            LlmRequestLifecycle.cancelActiveRequests();
            Translate_AllinOne.LOGGER.warn(
                    "Canceling active translation requests after watchdog trip. reason={} affectedTasks={}",
                    trip.reason(),
                    trip.affectedTaskCount()
            );
        } finally {
            CLEARING.set(false);
        }
    }
}
