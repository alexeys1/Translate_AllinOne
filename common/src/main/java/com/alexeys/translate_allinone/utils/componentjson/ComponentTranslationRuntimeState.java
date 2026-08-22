package com.alexeys.translate_allinone.utils.componentjson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class ComponentTranslationRuntimeState<F> {
    private static final int CACHE_LIMIT = 512;

    private final Map<String, FailureState<F>> failures = new ConcurrentHashMap<>();
    private final Map<String, PendingCandidate> pendingCandidates = new ConcurrentHashMap<>();
    private final Map<String, Long> fallbackGenerations = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > CACHE_LIMIT;
                }
            }
    );
    private final AtomicLong sessionEpoch = new AtomicLong();
    private final Object workLock = new Object();
    private final Map<String, TranslationWork> works = new LinkedHashMap<>();
    private final Map<PreparedRequestMemoKey, ComponentTranslationPreparedRequest> preparedRequests =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<PreparedRequestMemoKey, ComponentTranslationPreparedRequest> eldest
                ) {
                    return size() > CACHE_LIMIT;
                }
            });
    private final Map<String, Boolean> entityTemplateSeeds = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > CACHE_LIMIT;
                }
            }
    );

    public long epoch() {
        return sessionEpoch.get();
    }

    public long advanceSession() {
        return sessionEpoch.incrementAndGet();
    }

    public void clear() {
        failures.clear();
        pendingCandidates.clear();
        fallbackGenerations.clear();
        preparedRequests.clear();
        entityTemplateSeeds.clear();
        synchronized (workLock) {
            works.clear();
        }
    }

    public ComponentTranslationPreparedRequest preparedRequest(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Component translation document and target language are required.");
        }
        String normalizedLanguage = targetLanguage.trim();
        PreparedRequestMemoKey key = new PreparedRequestMemoKey(document, normalizedLanguage);
        ComponentTranslationPreparedRequest memoized = preparedRequests.get(key);
        if (memoized != null) {
            return memoized;
        }
        long startedAt = System.nanoTime();
        try {
            ComponentTranslationPreparedRequest prepared = ComponentTranslationPreparedRequest.create(
                    document,
                    normalizedLanguage
            );
            preparedRequests.put(key, prepared);
            return prepared;
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    document.route(),
                    ComponentTranslationMetrics.Timing.HASH,
                    System.nanoTime() - startedAt
            );
        }
    }

    public FailureState<F> activeFailure(String key, long nowMillis) {
        FailureState<F> failure = failures.get(key);
        if (failure == null) {
            return null;
        }
        if (nowMillis <= failure.expiresAtMillis()) {
            return failure;
        }
        failures.remove(key, failure);
        return null;
    }

    public void putFailure(String key, FailureState<F> failure) {
        failures.put(key, failure);
    }

    public boolean removeFailure(String key) {
        return failures.remove(key) != null;
    }

    public boolean hasPendingCandidate(String key) {
        return pendingCandidates.containsKey(key);
    }

    public PendingCandidate pendingCandidate(String key) {
        return pendingCandidates.get(key);
    }

    public void putPendingCandidate(String key, PendingCandidate candidate) {
        pendingCandidates.put(key, candidate);
    }

    public boolean removePendingCandidate(String key) {
        return pendingCandidates.remove(key) != null;
    }

    public boolean removePendingCandidate(String key, PendingCandidate candidate) {
        return pendingCandidates.remove(key, candidate);
    }

    public boolean claimFallbackGeneration(String key) {
        long epoch = sessionEpoch.get();
        synchronized (fallbackGenerations) {
            Long previousEpoch = fallbackGenerations.put(key, epoch);
            return previousEpoch == null || previousEpoch != epoch;
        }
    }

    public boolean clearFallbackGeneration(String key) {
        return fallbackGenerations.remove(key) != null;
    }

    public boolean registerQueued(
            ComponentTranslationPreparedRequest request,
            String requestContext,
            long epoch
    ) {
        synchronized (workLock) {
            if (epoch != sessionEpoch.get() || works.containsKey(request.identity().key())) {
                return false;
            }
            works.put(
                    request.identity().key(),
                    new TranslationWork(request, epoch, requestContext, WorkState.QUEUED, false)
            );
            return true;
        }
    }

    public boolean hasActiveWork(String cacheKey) {
        synchronized (workLock) {
            TranslationWork work = works.get(cacheKey);
            return work != null && work.epoch() == sessionEpoch.get();
        }
    }

    public boolean markWorkInFlight(String cacheKey, long epoch) {
        synchronized (workLock) {
            TranslationWork work = works.get(cacheKey);
            if (work == null || work.epoch() != epoch || work.state() != WorkState.QUEUED) {
                return false;
            }
            works.put(cacheKey, work.withState(WorkState.IN_FLIGHT));
            return true;
        }
    }

    public WorkCompletion complete(
            ComponentTranslationPreparedRequest request,
            ComponentTranslationResponse response,
            long epoch,
            boolean featureEnabled,
            boolean stageCandidate,
            String candidateHash,
            String requestContext,
            BooleanSupplier store
    ) {
        synchronized (workLock) {
            String cacheKey = request.identity().key();
            TranslationWork work = works.get(cacheKey);
            if (!featureEnabled
                    || work == null
                    || work.epoch() != epoch
                    || epoch != sessionEpoch.get()) {
                return WorkCompletion.STALE;
            }
            if (work.refreshAfterCompletion()) {
                works.remove(cacheKey);
                return new WorkCompletion(true, true, false, false);
            }
            if (stageCandidate) {
                pendingCandidates.put(
                        cacheKey,
                        new PendingCandidate(request, response, epoch, candidateHash, requestContext)
                );
                works.remove(cacheKey);
                return new WorkCompletion(true, false, false, true);
            }
            boolean stored = store.getAsBoolean();
            works.remove(cacheKey);
            return new WorkCompletion(true, false, stored, false);
        }
    }

    public void failWork(String cacheKey, long epoch) {
        synchronized (workLock) {
            TranslationWork work = works.get(cacheKey);
            if (work != null && work.epoch() == epoch) {
                works.remove(cacheKey);
            }
        }
    }

    public boolean requestRefresh(String cacheKey) {
        synchronized (workLock) {
            TranslationWork work = works.get(cacheKey);
            if (work == null) {
                return false;
            }
            if (work.state() == WorkState.QUEUED) {
                works.remove(cacheKey);
                return true;
            }
            works.put(cacheKey, work.withRefreshAfterCompletion());
            return true;
        }
    }

    public boolean markEntityTemplateSeeded(String fullCacheKey) {
        synchronized (entityTemplateSeeds) {
            return entityTemplateSeeds.put(fullCacheKey, Boolean.TRUE) == null;
        }
    }

    public void clearEntityTemplateSeed(String key) {
        entityTemplateSeeds.remove(key);
    }

    public record FailureState<F>(String message, long expiresAtMillis, F disposition) {
    }

    public record PendingCandidate(
            ComponentTranslationPreparedRequest request,
            ComponentTranslationResponse response,
            long epoch,
            String hash,
            String requestContext
    ) {
    }

    public record WorkCompletion(boolean current, boolean refreshAfterCompletion, boolean stored, boolean staged) {
        private static final WorkCompletion STALE = new WorkCompletion(false, false, false, false);
    }

    private record TranslationWork(
            ComponentTranslationPreparedRequest request,
            long epoch,
            String requestContext,
            WorkState state,
            boolean refreshAfterCompletion
    ) {
        private TranslationWork withState(WorkState updatedState) {
            return new TranslationWork(request, epoch, requestContext, updatedState, refreshAfterCompletion);
        }

        private TranslationWork withRefreshAfterCompletion() {
            return new TranslationWork(request, epoch, requestContext, state, true);
        }
    }

    private enum WorkState {
        QUEUED,
        IN_FLIGHT
    }

    private static final class PreparedRequestMemoKey {
        private final ComponentTranslationDocument document;
        private final String targetLanguage;
        private final int hashCode;

        private PreparedRequestMemoKey(ComponentTranslationDocument document, String targetLanguage) {
            this.document = document;
            this.targetLanguage = targetLanguage;
            this.hashCode = 31 * System.identityHashCode(document) + targetLanguage.hashCode();
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof PreparedRequestMemoKey other
                    && document == other.document
                    && targetLanguage.equals(other.targetLanguage);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
