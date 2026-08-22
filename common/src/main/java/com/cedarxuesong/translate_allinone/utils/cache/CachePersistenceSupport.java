package com.cedarxuesong.translate_allinone.utils.cache;

final class CachePersistenceSupport {
    enum SaveScheduleAction {
        SAVE_NOW,
        SCHEDULE_ASYNC,
        SKIP_ALREADY_SCHEDULED
    }

    record SaveSchedulePlan(SaveScheduleAction action, long elapsedMillis, long delayMillis) {
    }

    private final long saveDebounceMillis;

    private volatile boolean dirty = false;
    private volatile long lastSaveAtMillis = 0L;
    private volatile boolean saveScheduled = false;

    CachePersistenceSupport(long saveDebounceMillis) {
        this.saveDebounceMillis = saveDebounceMillis;
    }

    void resetForLoad() {
        dirty = false;
        saveScheduled = false;
    }

    void finishLoad(boolean shouldRewriteCacheFile, Runnable scheduleSave) {
        if (!shouldRewriteCacheFile) {
            dirty = false;
            return;
        }
        dirty = true;
        scheduleSave.run();
    }

    void markDirty() {
        dirty = true;
    }

    boolean beginSave() {
        saveScheduled = false;
        return dirty;
    }

    void finishSave() {
        dirty = false;
        lastSaveAtMillis = System.currentTimeMillis();
    }

    SaveSchedulePlan planSave(boolean preferAsyncImmediateSave) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - lastSaveAtMillis);
        if (elapsedMillis >= saveDebounceMillis) {
            if (!preferAsyncImmediateSave) {
                return new SaveSchedulePlan(SaveScheduleAction.SAVE_NOW, elapsedMillis, 0L);
            }
            if (saveScheduled) {
                return new SaveSchedulePlan(SaveScheduleAction.SKIP_ALREADY_SCHEDULED, elapsedMillis, 0L);
            }
            saveScheduled = true;
            return new SaveSchedulePlan(SaveScheduleAction.SCHEDULE_ASYNC, elapsedMillis, 0L);
        }

        if (saveScheduled) {
            return new SaveSchedulePlan(SaveScheduleAction.SKIP_ALREADY_SCHEDULED, elapsedMillis, 0L);
        }

        long delayMillis = Math.max(0L, saveDebounceMillis - elapsedMillis);
        saveScheduled = true;
        return new SaveSchedulePlan(SaveScheduleAction.SCHEDULE_ASYNC, elapsedMillis, delayMillis);
    }
}
