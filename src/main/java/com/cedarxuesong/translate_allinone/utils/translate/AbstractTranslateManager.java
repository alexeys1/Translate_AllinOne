package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.TranslateExceptionUtils;
import com.cedarxuesong.translate_allinone.utils.TranslateStringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractTranslateManager {

    protected final AtomicLong sessionEpoch = new AtomicLong(0);
    protected ExecutorService workerExecutor;
    protected ScheduledExecutorService collectorExecutor;
    protected ScheduledExecutorService retryExecutor;

    protected abstract int workerCount();

    protected abstract long collectIntervalMs();

    protected abstract long retryIntervalSec();

    protected abstract int maxBatchSize();

    protected abstract String managerLabel();

    protected abstract boolean isEnabled();

    protected abstract List<String> takeBatch() throws InterruptedException;

    protected abstract void markBatchInProgress(List<String> batch);

    protected abstract void releaseBatchInProgress(Set<String> keys);

    protected abstract void submitBatch(List<String> batch);

    protected abstract Set<String> getErroredKeys();

    protected abstract void requeueErroredKey(String key);

    protected abstract void requeueFailedBatch(List<String> batch, String errorMessage);

    protected abstract void translateBatch(List<String> batch, long batchSessionEpoch);

    protected void beforeStart() {}

    protected void afterStop() {}

    protected void onDisabledSleep() throws InterruptedException {
        TimeUnit.SECONDS.sleep(5);
    }

    protected boolean isSessionActive(long expectedEpoch) {
        return expectedEpoch == sessionEpoch.get();
    }

    protected boolean hasKeyMismatch(Map<String, String> translatedMapFromAI, int expectedSize) {
        Set<String> expectedKeys = new LinkedHashSet<>();
        for (int i = 1; i <= expectedSize; i++) {
            expectedKeys.add(String.valueOf(i));
        }

        Set<String> actualKeys = new LinkedHashSet<>(translatedMapFromAI.keySet());
        Set<String> missingKeys = new LinkedHashSet<>(expectedKeys);
        missingKeys.removeAll(actualKeys);

        Set<String> extraKeys = new LinkedHashSet<>(actualKeys);
        extraKeys.removeAll(expectedKeys);

        if (missingKeys.isEmpty() && extraKeys.isEmpty()) {
            return false;
        }

        Translate_AllinOne.LOGGER.warn(
                "{} translation key mismatch. expectedCount={}, actualCount={}, missing={}, extra={}",
                managerLabel(),
                expectedKeys.size(),
                actualKeys.size(),
                TranslateStringUtils.summarizeKeys(missingKeys),
                TranslateStringUtils.summarizeKeys(extraKeys)
        );
        return true;
    }

    protected boolean isInternalPostprocessError(Throwable throwable) {
        return TranslateExceptionUtils.isInternalPostprocessError(throwable);
    }

    public synchronized void start() {
        long newSessionEpoch = sessionEpoch.incrementAndGet();
        Translate_AllinOne.LOGGER.info("{} translation session started. epoch={}", managerLabel(), newSessionEpoch);

        beforeStart();

        if (workerExecutor == null || workerExecutor.isShutdown()) {
            int count = workerCount();
            workerExecutor = Executors.newFixedThreadPool(count);
            for (int i = 0; i < count; i++) {
                workerExecutor.submit(this::processingLoop);
            }
            Translate_AllinOne.LOGGER.info("{}TranslateManager started with {} worker threads.", managerLabel(), count);
        }

        if (collectorExecutor == null || collectorExecutor.isShutdown()) {
            collectorExecutor = Executors.newSingleThreadScheduledExecutor();
            collectorExecutor.scheduleAtFixedRate(
                    this::collectAndBatchItems, 0, collectIntervalMs(), TimeUnit.MILLISECONDS);
            Translate_AllinOne.LOGGER.info("{} translation collector started.", managerLabel());
        }

        if (retryExecutor == null || retryExecutor.isShutdown()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor();
            long interval = retryIntervalSec();
            retryExecutor.scheduleAtFixedRate(
                    this::requeueErroredItems, interval, interval, TimeUnit.SECONDS);
            Translate_AllinOne.LOGGER.info("{} translation retry scheduler started.", managerLabel());
        }
    }

    public synchronized void stop() {
        long invalidatedSessionEpoch = sessionEpoch.incrementAndGet();
        Translate_AllinOne.LOGGER.info("{} translation session invalidated. epoch={}", managerLabel(), invalidatedSessionEpoch);

        shutdownExecutor(workerExecutor, managerLabel() + "TranslateManager's processing threads");
        shutdownExecutor(collectorExecutor, managerLabel() + " translation collector");
        shutdownExecutor(retryExecutor, managerLabel() + " translation retry scheduler");

        afterStop();
    }

    private void shutdownExecutor(ExecutorService executor, String label) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Translate_AllinOne.LOGGER.error("{} did not terminate in time.", label);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Translate_AllinOne.LOGGER.info("{} stopped.", label);
        }
    }

    protected void processingLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<String> batch = null;
            try {
                long batchSessionEpoch = sessionEpoch.get();

                if (!isEnabled()) {
                    onDisabledSleep();
                    continue;
                }

                batch = takeBatch();
                markBatchInProgress(batch);

                if (!isSessionActive(batchSessionEpoch)) {
                    releaseBatchInProgress(new java.util.HashSet<>(batch));
                    continue;
                }

                translateBatch(batch, batchSessionEpoch);

            } catch (InterruptedException e) {
                if (batch != null && !batch.isEmpty()) {
                    requeueFailedBatch(batch, "Processing thread interrupted");
                }
                Thread.currentThread().interrupt();
                Translate_AllinOne.LOGGER.info("{} processing thread interrupted, shutting down.", managerLabel());
                break;
            } catch (Exception e) {
                if (batch != null && !batch.isEmpty()) {
                    requeueFailedBatch(batch, "Processing loop failure: " + e.getMessage());
                }
                Translate_AllinOne.LOGGER.error("An unexpected error occurred in the {} processing loop, continuing.", managerLabel(), e);
            }
        }
    }

    protected void collectAndBatchItems() {
        try {
            if (!isEnabled()) {
                return;
            }

            List<String> drained = drainAllPendingItems();
            if (drained == null || drained.isEmpty()) {
                return;
            }

            int batchSize = Math.max(1, maxBatchSize());
            for (int start = 0; start < drained.size(); start += batchSize) {
                int end = Math.min(drained.size(), start + batchSize);
                submitBatch(drained.subList(start, end));
            }

        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error collecting {} translation items.", managerLabel(), e);
        }
    }

    protected List<String> drainAllPendingItems() {
        return drainAllPendingItemsFromCache();
    }

    protected abstract List<String> drainAllPendingItemsFromCache();

    protected void requeueErroredItems() {
        try {
            Set<String> erroredKeys = getErroredKeys();
            if (erroredKeys == null || erroredKeys.isEmpty()) {
                return;
            }
            int count = 0;
            for (String key : erroredKeys) {
                requeueErroredKey(key);
                count++;
            }
            if (count > 0) {
                Translate_AllinOne.LOGGER.info("{} retry scheduler requeued {} errored items.", managerLabel(), count);
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Error requeueing {} errored items.", managerLabel(), e);
        }
    }
}
