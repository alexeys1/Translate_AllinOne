package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.cache.ChatOutputTranslationCache;
import com.alexeys.translate_allinone.utils.cache.ItemTemplateCache;
import com.alexeys.translate_allinone.utils.cache.SkyblockNpcTranslationCache;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;

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
            Translate_AllinOne.LOGGER.warn(
                    "Clearing all translation queues after watchdog trip. reason={} affectedTasks={}",
                    trip.reason(),
                    trip.affectedTaskCount()
            );
            ComponentTranslationRuntime.cancelPendingTranslations();
            ChatOutputTranslateManager.clearTranslationQueue();
            WynnDialogueTranslateManager.getInstance().clearTranslationQueue();
            WynntilsTaskTrackerTranslateManager.getInstance().clearTranslationQueue();
            ItemTemplateCache.getInstance().clearTranslationQueue();
            ChatOutputTranslationCache.getInstance().clearTranslationQueue();
            SkyblockNpcTranslationCache.getInstance().clearTranslationQueue();
        } finally {
            CLEARING.set(false);
        }
    }
}
