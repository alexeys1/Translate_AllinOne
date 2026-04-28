package com.cedarxuesong.translate_allinone.utils.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class CacheRuntimeStateSupport<K, B> {
    enum LookupStatus {
        TRANSLATED,
        IN_PROGRESS,
        PENDING,
        ERROR,
        NOT_CACHED
    }

    record LookupState(LookupStatus status, String translation, String errorMessage) {
    }

    private final Map<K, String> templateCache;
    private final CacheKeyQueueSupport<K, B> keyQueueSupport;
    private final Set<K> refreshAfterInProgress = ConcurrentHashMap.newKeySet();

    CacheRuntimeStateSupport(Map<K, String> templateCache, CacheKeyQueueSupport<K, B> keyQueueSupport) {
        this.templateCache = templateCache;
        this.keyQueueSupport = keyQueueSupport;
    }

    void resetForLoad() {
        templateCache.clear();
        refreshAfterInProgress.clear();
        keyQueueSupport.resetForLoad();
    }

    void putLoadedEntries(Map<K, String> entries) {
        templateCache.putAll(entries);
    }

    Map<K, String> templateCache() {
        return templateCache;
    }

    LookupState peek(K key) {
        String translation = templateCache.get(key);
        if (translation != null && !translation.isEmpty()) {
            return new LookupState(LookupStatus.TRANSLATED, translation, null);
        }

        String errorMessage = keyQueueSupport.getError(key);
        if (errorMessage != null) {
            return new LookupState(LookupStatus.ERROR, "", errorMessage);
        }

        if (keyQueueSupport.isInProgress(key)) {
            return new LookupState(LookupStatus.IN_PROGRESS, "", null);
        }

        if (keyQueueSupport.isQueuedOrInProgress(key)) {
            return new LookupState(LookupStatus.PENDING, "", null);
        }

        return new LookupState(LookupStatus.NOT_CACHED, "", null);
    }

    LookupState lookupOrQueue(K key) {
        LookupState lookupState = peek(key);
        if (lookupState.status() != LookupStatus.NOT_CACHED) {
            return lookupState;
        }

        keyQueueSupport.enqueueIfAbsent(key);
        return new LookupState(LookupStatus.PENDING, "", null);
    }

    void promoteTranslation(K key, String translation) {
        if (refreshAfterInProgress.remove(key)) {
            keyQueueSupport.finishKeys(Set.of(key));
            keyQueueSupport.requeueToFront(key);
            return;
        }
        templateCache.put(key, translation);
        keyQueueSupport.finishKeys(Set.of(key));
    }

    void updateTranslations(Map<K, String> translations) {
        Map<K, String> acceptedTranslations = new HashMap<>();
        List<K> deferredRefreshKeys = new ArrayList<>();
        for (Map.Entry<K, String> entry : translations.entrySet()) {
            K key = entry.getKey();
            if (refreshAfterInProgress.remove(key)) {
                deferredRefreshKeys.add(key);
                continue;
            }
            acceptedTranslations.put(key, entry.getValue());
        }

        if (!acceptedTranslations.isEmpty()) {
            templateCache.putAll(acceptedTranslations);
            keyQueueSupport.finishKeys(acceptedTranslations.keySet());
        }
        requeueDeferredRefreshKeys(deferredRefreshKeys);
    }

    int forceRefresh(Iterable<K> keys) {
        if (keys == null) {
            return 0;
        }

        int refreshedCount = 0;
        List<K> keysToRequeueNow = new ArrayList<>();
        for (K key : keys) {
            if (key == null) {
                continue;
            }
            templateCache.remove(key);
            if (keyQueueSupport.isInProgress(key)) {
                refreshAfterInProgress.add(key);
                refreshedCount++;
                continue;
            }
            refreshAfterInProgress.remove(key);
            keysToRequeueNow.add(key);
            refreshedCount++;
        }
        for (int i = keysToRequeueNow.size() - 1; i >= 0; i--) {
            keyQueueSupport.requeueToFront(keysToRequeueNow.get(i));
        }
        return refreshedCount;
    }

    long translatedCount() {
        return templateCache.values().stream()
                .filter(value -> value != null && !value.isEmpty())
                .count();
    }

    int totalCount() {
        Set<K> allKeys = new HashSet<>(templateCache.keySet());
        keyQueueSupport.appendTrackedKeysTo(allKeys);
        return allKeys.size();
    }

    Set<K> copyErroredKeys() {
        return keyQueueSupport.copyErroredKeys();
    }

    ListAccess<K, B> queues() {
        return new ListAccess<>(keyQueueSupport, refreshAfterInProgress);
    }

    private void requeueDeferredRefreshKeys(List<K> deferredRefreshKeys) {
        if (deferredRefreshKeys == null || deferredRefreshKeys.isEmpty()) {
            return;
        }

        keyQueueSupport.finishKeys(deferredRefreshKeys);
        for (int i = deferredRefreshKeys.size() - 1; i >= 0; i--) {
            keyQueueSupport.requeueToFront(deferredRefreshKeys.get(i));
        }
    }

    record ListAccess<K, B>(
            CacheKeyQueueSupport<K, B> keyQueueSupport,
            Set<K> refreshAfterInProgress
    ) {
        ListAccess {
        }

        void clearPendingAndInProgress() {
            keyQueueSupport.clearPendingAndInProgress();
            refreshAfterInProgress.clear();
        }

        boolean isPendingQueueEmpty() {
            return keyQueueSupport.isPendingQueueEmpty();
        }

        java.util.List<K> drainAllPendingItems() {
            return keyQueueSupport.drainAllPendingItems();
        }

        void submitBatch(B batch) {
            keyQueueSupport.submitBatch(batch);
        }

        B takeBatch() throws InterruptedException {
            return keyQueueSupport.takeBatch();
        }

        void markAsInProgress(Collection<K> keys) {
            keyQueueSupport.markAsInProgress(keys);
        }

        boolean requeueFromError(K key) {
            return keyQueueSupport.requeueFromError(key);
        }

        void releaseInProgress(Set<K> keys) {
            if (keys == null || keys.isEmpty()) {
                return;
            }
            List<K> deferredRefreshKeys = new ArrayList<>();
            for (K key : keys) {
                if (refreshAfterInProgress.remove(key)) {
                    deferredRefreshKeys.add(key);
                }
            }
            keyQueueSupport.releaseInProgress(keys);
            requeueDeferredRefreshKeys(deferredRefreshKeys);
        }

        void markErrored(Collection<K> failedKeys, String errorMessage, String fallbackErrorMessage) {
            if (failedKeys == null || failedKeys.isEmpty()) {
                return;
            }

            List<K> deferredRefreshKeys = new ArrayList<>();
            Set<K> erroredKeys = new HashSet<>();
            for (K key : failedKeys) {
                if (refreshAfterInProgress.remove(key)) {
                    deferredRefreshKeys.add(key);
                } else {
                    erroredKeys.add(key);
                }
            }
            if (!erroredKeys.isEmpty()) {
                keyQueueSupport.markErrored(erroredKeys, errorMessage, fallbackErrorMessage);
            }
            requeueDeferredRefreshKeys(deferredRefreshKeys);
        }

        private void requeueDeferredRefreshKeys(List<K> deferredRefreshKeys) {
            if (deferredRefreshKeys == null || deferredRefreshKeys.isEmpty()) {
                return;
            }

            keyQueueSupport.finishKeys(deferredRefreshKeys);
            for (int i = deferredRefreshKeys.size() - 1; i >= 0; i--) {
                keyQueueSupport.requeueToFront(deferredRefreshKeys.get(i));
            }
        }

        int pendingSize() {
            return keyQueueSupport.pendingSize();
        }

        int batchQueueSize() {
            return keyQueueSupport.batchQueueSize();
        }

        int inProgressSize() {
            return keyQueueSupport.inProgressSize();
        }

        int erroredSize() {
            return keyQueueSupport.erroredSize();
        }

        int queuedOrInProgressSize() {
            return keyQueueSupport.queuedOrInProgressSize();
        }
    }
}
