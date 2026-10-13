package com.alexeys.translate_allinone.utils.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class CacheKeyQueueSupport<K, B> {
    private static final long ERROR_RETRY_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final LinkedBlockingDeque<K> pendingQueue;
    private final Set<K> inProgress;
    private final LinkedBlockingQueue<B> batchWorkQueue;
    private final Set<K> allQueuedOrInProgressKeys;
    private final Map<K, String> errorCache;
    private final Map<K, Long> errorTimestamps = new ConcurrentHashMap<>();

    CacheKeyQueueSupport(
            LinkedBlockingDeque<K> pendingQueue,
            Set<K> inProgress,
            LinkedBlockingQueue<B> batchWorkQueue,
            Set<K> allQueuedOrInProgressKeys,
            Map<K, String> errorCache
    ) {
        this.pendingQueue = pendingQueue;
        this.inProgress = inProgress;
        this.batchWorkQueue = batchWorkQueue;
        this.allQueuedOrInProgressKeys = allQueuedOrInProgressKeys;
        this.errorCache = errorCache;
    }

    void resetForLoad() {
        pendingQueue.clear();
        inProgress.clear();
        batchWorkQueue.clear();
        allQueuedOrInProgressKeys.clear();
        clearErrors();
    }

    void clearPendingAndInProgress() {
        pendingQueue.clear();
        inProgress.clear();
        batchWorkQueue.clear();
        allQueuedOrInProgressKeys.clear();
    }

    void clearTranslationQueue() {
        clearPendingAndInProgress();
        clearErrors();
    }

    boolean isPendingQueueEmpty() {
        return pendingQueue.isEmpty();
    }

    boolean isInProgress(K key) {
        return inProgress.contains(key);
    }

    boolean isQueuedOrInProgress(K key) {
        return allQueuedOrInProgressKeys.contains(key);
    }

    String getError(K key) {
        Long expiresAtMillis = errorTimestamps.get(key);
        if (expiresAtMillis == null) {
            return null;
        }
        if (System.currentTimeMillis() < expiresAtMillis) {
            return errorCache.get(key);
        }
        requeueFromError(key);
        return null;
    }

    Set<K> copyErroredKeys() {
        return new HashSet<>(errorCache.keySet());
    }

    List<K> drainAllPendingItems() {
        List<K> items = new ArrayList<>();
        pendingQueue.drainTo(items);
        return items;
    }

    void submitBatch(B batch) {
        batchWorkQueue.offer(batch);
    }

    B takeBatch() throws InterruptedException {
        return batchWorkQueue.take();
    }

    void markAsInProgress(Collection<K> keys) {
        inProgress.addAll(keys);
    }

    void enqueueIfAbsent(K key) {
        if (allQueuedOrInProgressKeys.add(key)) {
            pendingQueue.offerLast(key);
        }
    }

    void requeueToFront(K key) {
        clearRuntimeStateForKey(key);
        allQueuedOrInProgressKeys.add(key);
        pendingQueue.offerFirst(key);
    }

    boolean requeueFromError(K key) {
        if (!clearError(key)) {
            return false;
        }
        inProgress.remove(key);
        while (pendingQueue.remove(key)) {
            // Drop stale pending copies before retrying this key from the front.
        }
        allQueuedOrInProgressKeys.add(key);
        pendingQueue.offerFirst(key);
        return true;
    }

    void releaseInProgress(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        inProgress.removeAll(keys);
        allQueuedOrInProgressKeys.removeAll(keys);
    }

    void finishKeys(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (K key : keys) {
            clearRuntimeStateForKey(key);
        }
    }

    void markErrored(Collection<K> failedKeys, String errorMessage, String fallbackErrorMessage) {
        if (failedKeys == null || failedKeys.isEmpty()) {
            return;
        }
        String resolvedErrorMessage = errorMessage != null
                ? errorMessage
                : (fallbackErrorMessage != null ? fallbackErrorMessage : "Unknown translation error");
        inProgress.removeAll(failedKeys);
        long expiresAtMillis = System.currentTimeMillis() + ERROR_RETRY_COOLDOWN_MILLIS;
        for (K key : failedKeys) {
            errorCache.put(key, resolvedErrorMessage);
            errorTimestamps.put(key, expiresAtMillis);
        }
    }

    void appendTrackedKeysTo(Set<K> keys) {
        keys.addAll(pendingQueue);
        keys.addAll(inProgress);
    }

    int pendingSize() {
        return pendingQueue.size();
    }

    int batchQueueSize() {
        return batchWorkQueue.size();
    }

    int inProgressSize() {
        return inProgress.size();
    }

    int erroredSize() {
        return errorCache.size();
    }

    int queuedOrInProgressSize() {
        return allQueuedOrInProgressKeys.size();
    }

    private void clearRuntimeStateForKey(K key) {
        clearError(key);
        inProgress.remove(key);
        allQueuedOrInProgressKeys.remove(key);
        while (pendingQueue.remove(key)) {
            // Remove stale queued copies so the next state transition starts cleanly.
        }
    }

    private boolean clearError(K key) {
        errorTimestamps.remove(key);
        return errorCache.remove(key) != null;
    }

    private void clearErrors() {
        errorCache.clear();
        errorTimestamps.clear();
    }
}
