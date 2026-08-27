package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.utils.TranslateExceptionUtils;
import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStore;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.translate.TranslationFeatureGate;
import com.alexeys.translate_allinone.utils.translate.TranslationQueueWatchdog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ComponentTranslationRuntimeCore {
    private static final long FAILURE_RETRY_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(5);
    private static final long ITEM_BATCH_COLLECT_DELAY_MILLIS = 10L;
    private static final long REQUEST_RATE_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final int OTHER_TRANSLATIONS_REQUESTS_PER_MINUTE = 60;
    private static volatile Access access;
    private static final Map<DispatchRoute, DispatchState> DISPATCH = createDispatchStates();
    private static final ComponentTranslationRuntimeState<FailureDisposition> STATE =
            new ComponentTranslationRuntimeState<>();

    private ComponentTranslationRuntimeCore() {
    }

    public static void configure(Access runtimeAccess) {
        access = Objects.requireNonNull(runtimeAccess, "runtimeAccess");
    }

    private static Access access() {
        Access current = access;
        if (current == null) {
            throw new IllegalStateException("Component translation runtime is not configured.");
        }
        return current;
    }

    public static <T> Resolution<T> resolve(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            Supplier<T> legacyLookup,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext
    ) {
        return resolve(
                document,
                targetLanguage,
                legacyKey,
                legacyLookup,
                renderer,
                requestContext,
                true
        );
    }

    public static <T> Resolution<T> resolve(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            Supplier<T> legacyLookup,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext,
            boolean queueIfMissing
    ) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank() || renderer == null) {
            ComponentTranslationDebugLogger.error(
                    document == null ? null : document.route(),
                    "incomplete request: route={} targetLanguagePresent={} rendererPresent={} context={}",
                    document == null || document.route() == null ? "" : document.route().wireName(),
                    targetLanguage != null && !targetLanguage.isBlank(),
                    renderer != null,
                    requestContext == null ? "" : requestContext
            );
            return new Resolution<>(State.INELIGIBLE, null, "", "Incomplete Component request");
        }
        if (!TranslationFeatureGate.isEnabled()) {
            return new Resolution<>(State.INELIGIBLE, null, "", "Global translation is disabled");
        }
        if (document.units().isEmpty()) {
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.NO_TEXT);
            return new Resolution<>(State.NO_TEXT, null, "", "");
        }

        ComponentTranslationPreparedRequest request;
        long lookupStartedAt = System.nanoTime();
        try {
            request = preparedRequest(document, targetLanguage);
        } catch (RuntimeException e) {
            ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.FALLBACK_ORIGINAL
            );
            ComponentTranslationDebugLogger.error(
                    document.route(),
                    "request preparation failed: route={} context={} reason={}",
                    document.route().wireName(),
                    requestContext == null ? "" : requestContext,
                    e.getMessage(),
                    e
            );
            return new Resolution<>(State.INELIGIBLE, null, "", e.getMessage());
        }
        ComponentTranslationDebugLogger.textContent(document, request.identity().key());

        ComponentTranslationStore.Lookup lookup;
        try {
            lookup = store(request.document().route()).lookup(request);
            T safeCacheValue = null;
            boolean safeCacheHit = false;
            if (lookup.status() == ComponentTranslationStore.Status.HIT) {
                try {
                    safeCacheValue = renderer.apply(lookup.response());
                    safeCacheHit = true;
                } catch (RuntimeException error) {
                    store(request.document().route()).remove(request);
                    ComponentTranslationMetrics.record(
                            document,
                            ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                    );
                    ComponentTranslationDebugLogger.error(
                            document.route(),
                            "Component cached response apply failed: route={} context={} phase=CACHE_HIT_APPLY key={} candidate={} reason={}",
                            document.route().wireName(),
                            requestContext == null ? "" : requestContext,
                            lookup.cacheKey(),
                            candidateHash(lookup.response()),
                            error.getMessage(),
                            error
                    );
                }
            }
            if (document.route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH
                    || STATE.hasPendingCandidate(request.identity().key())) {
                CandidateApplication<T> candidate = applyPendingCandidate(request, renderer, requestContext);
                if (candidate.accepted()) {
                    return new Resolution<>(State.CACHE_HIT, candidate.value(), request.identity().key(), "");
                }
                if (candidate.rejected()) {
                    if (safeCacheHit) {
                        ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.CACHE_HIT);
                        return new Resolution<>(State.CACHE_HIT, safeCacheValue, request.identity().key(), "");
                    }
                    ComponentTranslationRuntimeState.FailureState<FailureDisposition> failure =
                            new ComponentTranslationRuntimeState.FailureState<>(
                                    candidate.errorMessage(),
                                    failureExpiresAtMillis(
                                            request.document().route(),
                                            FailureDisposition.TERMINAL_CONTENT_FAILURE,
                                            System.currentTimeMillis()
                                    ),
                                    FailureDisposition.TERMINAL_CONTENT_FAILURE
                            );
                    STATE.putFailure(request.identity().key(), failure);
                    return new Resolution<>(
                            State.FAILED,
                            null,
                            request.identity().key(),
                            failure.message(),
                            failure.disposition()
                    );
                }
            }
            if (safeCacheHit) {
                if (document.route() == ComponentTranslationRoute.ENTITY_NAME
                        && markEntityTemplateSeeded(request.identity().key())) {
                    store(request.document().route()).put(request, lookup.response());
                }
                STATE.removeFailure(lookup.cacheKey());
                ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.CACHE_HIT);
                ComponentTranslationDebugLogger.flow(
                        document.route(),
                        "resolve route={} state=CACHE_HIT key={}",
                        document.route().wireName(),
                        lookup.cacheKey()
                );
                return new Resolution<>(State.CACHE_HIT, safeCacheValue, lookup.cacheKey(), "");
            }
            if (document.route() == ComponentTranslationRoute.ENTITY_NAME) {
                ComponentTranslationStore.Lookup templateLookup = store(request.document().route()).lookupEntityTemplate(request);
                if (templateLookup.status() == ComponentTranslationStore.Status.HIT) {
                    try {
                        T rendered = renderer.apply(templateLookup.response());
                        store(request.document().route()).put(request, templateLookup.response());
                        STATE.removeFailure(request.identity().key());
                        ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.TEMPLATE_HIT);
                        ComponentTranslationDebugLogger.entityTemplateReuse(
                                request.identity().key(),
                                templateLookup.cacheKey()
                        );
                        return new Resolution<>(State.CACHE_HIT, rendered, request.identity().key(), "");
                    } catch (RuntimeException error) {
                        store(request.document().route()).removeEntityTemplate(request);
                        ComponentTranslationMetrics.record(
                                document,
                                ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                        );
                        ComponentTranslationDebugLogger.error(
                                document.route(),
                                "Entity template response apply failed: fullKey={} templateKey={} reason={}",
                                request.identity().key(),
                                templateLookup.cacheKey(),
                                error.getMessage(),
                                error
                        );
                    }
                }
            }
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.CACHE_MISS);
            ComponentTranslationDebugLogger.entityIdentityMiss(
                    document,
                    request.targetLanguage(),
                    request.identity(),
                    lookup.status().name()
            );
        } catch (RuntimeException e) {
            ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.FALLBACK_ORIGINAL
            );
            ComponentTranslationDebugLogger.error(
                    document.route(),
                    "cache lookup failed: route={} context={} key={} reason={}",
                    document.route().wireName(),
                    requestContext == null ? "" : requestContext,
                    request.identity().key(),
                    e.getMessage(),
                    e
            );
            return new Resolution<>(
                    State.INELIGIBLE,
                    null,
                    request.identity().key(),
                    e.getMessage(),
                    FailureDisposition.INFRASTRUCTURE_FAILURE
            );
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    document.route(),
                    ComponentTranslationMetrics.Timing.CACHE_LOOKUP,
                    System.nanoTime() - lookupStartedAt
            );
        }

        if (isTooltipLegacyCompatibilityRoute(document.route()) && legacyLookup != null) {
            try {
                T legacyValue = legacyLookup.get();
                if (legacyValue != null) {
                    ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.LEGACY_HIT);
                    ComponentTranslationDebugLogger.flow(
                            document.route(),
                            "resolve route={} state=LEGACY_HIT key={}",
                            document.route().wireName(),
                            legacyKey == null ? request.identity().key() : legacyKey
                    );
                    return new Resolution<>(State.LEGACY_HIT, legacyValue, request.identity().key(), "");
                }
            } catch (RuntimeException error) {
                ComponentTranslationDebugLogger.error(
                        document.route(),
                        "legacy cache lookup failed: route={} context={} key={} reason={}",
                        document.route().wireName(),
                        requestContext == null ? "" : requestContext,
                        legacyKey == null ? request.identity().key() : legacyKey,
                        error.getMessage(),
                        error
                );
                return new Resolution<>(
                        State.FAILED,
                        null,
                        request.identity().key(),
                        error.getMessage(),
                        FailureDisposition.INFRASTRUCTURE_FAILURE
                );
            }
        }

        ComponentTranslationRuntimeState.FailureState<FailureDisposition> failure =
                STATE.activeFailure(request.identity().key(), System.currentTimeMillis());
        if (failure != null) {
            ComponentTranslationMetrics.record(
                    document,
                    ComponentTranslationMetrics.Outcome.FALLBACK_ORIGINAL
            );
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=FAILED cooldown=true key={}",
                    document.route().wireName(),
                    request.identity().key()
            );
            return new Resolution<>(
                    State.FAILED,
                    null,
                    request.identity().key(),
                    failure.message(),
                    failure.disposition()
            );
        }
        if (!queueIfMissing) {
            if (hasActiveWork(request.identity().key())) {
                return new Resolution<>(State.PENDING, null, request.identity().key(), "");
            }
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=MISS queue=false key={}",
                    document.route().wireName(),
                    request.identity().key()
            );
            return new Resolution<>(State.MISS, null, request.identity().key(), "");
        }
        if (!access().readyForTranslation()
                && document.route() != ComponentTranslationRoute.SCREEN_UI) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=PENDING reason=client_not_ready key={}",
                    document.route().wireName(),
                    request.identity().key()
            );
            return new Resolution<>(
                    State.PENDING,
                    null,
                    request.identity().key(),
                    "",
                    FailureDisposition.INFRASTRUCTURE_FAILURE
            );
        }
        boolean queued = queue(request, requestContext);
        ComponentTranslationDebugLogger.flow(
                document.route(),
                "resolve route={} state=PENDING queued={} key={}",
                document.route().wireName(),
                queued,
                request.identity().key()
        );
        return new Resolution<>(State.PENDING, null, request.identity().key(), queued ? "" : "");
    }

    public static boolean forceRefresh(ComponentTranslationDocument document, String targetLanguage) {
        if (!TranslationFeatureGate.isEnabled()
                || document == null
                || targetLanguage == null
                || targetLanguage.isBlank()) {
            return false;
        }
        ComponentTranslationPreparedRequest request = preparedRequest(document, targetLanguage);
        String key = request.identity().key();
        boolean failureRemoved = STATE.removeFailure(key);
        boolean fallbackGenerationRemoved = clearFallbackGeneration(key);
        boolean candidateRemoved = STATE.removePendingCandidate(key);
        STATE.clearEntityTemplateSeed(key);
        boolean refreshRequested = requestRefresh(key);
        boolean removed = store(document.route()).remove(request);
        boolean templateRemoved = store(document.route()).removeEntityTemplate(request);
        return removed || templateRemoved || candidateRemoved || refreshRequested || failureRemoved || fallbackGenerationRemoved;
    }

    public static long beginSession() {
        long epoch = STATE.advanceSession();
        TranslationQueueWatchdog.reset();
        clearRuntimeState();
        return epoch;
    }

    public static long endSession() {
        long epoch = STATE.advanceSession();
        TranslationQueueWatchdog.reset();
        clearRuntimeState();
        return epoch;
    }

    public static void resetSession() {
        TranslationQueueWatchdog.reset();
        clearRuntimeState();
    }

    public static long providerConfigurationChanged() {
        long epoch = STATE.advanceSession();
        TranslationQueueWatchdog.reset();
        clearRuntimeState();
        return epoch;
    }

    public static long cancelPendingTranslations() {
        long epoch = STATE.advanceSession();
        TranslationQueueWatchdog.reset();
        clearRuntimeState();
        return epoch;
    }

    private static void clearRuntimeState() {
        STATE.clear();
        for (DispatchState state : DISPATCH.values()) {
            synchronized (state) {
                state.queue.clear();
                state.active.clear();
                state.requestStartTimes.clear();
                state.drainScheduled = false;
            }
        }
    }

    public static String cacheKey(ComponentTranslationDocument document, String targetLanguage) {
        return preparedRequest(document, targetLanguage).identity().key();
    }

    public static boolean isWorkInFlight(String cacheKey) {
        return STATE.isWorkInFlight(cacheKey);
    }

    public static <T> T promoteCompatibleResponse(
            ComponentTranslationDocument currentDocument,
            ComponentTranslationDocument legacyDocument,
            String targetLanguage,
            Function<ComponentTranslationResponse, ComponentTranslationResponse> upgrader,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext
    ) {
        if (!TranslationFeatureGate.isEnabled()
                || currentDocument == null
                || legacyDocument == null
                || currentDocument.route() != legacyDocument.route()
                || targetLanguage == null
                || targetLanguage.isBlank()
                || upgrader == null
                || renderer == null) {
            return null;
        }
        ComponentTranslationPreparedRequest currentRequest = preparedRequest(currentDocument, targetLanguage);
        ComponentTranslationPreparedRequest legacyRequest = preparedRequest(legacyDocument, targetLanguage);
        ComponentTranslationStore.Lookup lookup = store(legacyDocument.route()).lookup(legacyRequest);
        if (lookup.status() != ComponentTranslationStore.Status.HIT) {
            return null;
        }
        String legacyCandidateHash = candidateHash(lookup.response());
        try {
            ComponentTranslationResponse promotedResponse = upgrader.apply(lookup.response());
            T rendered = renderer.apply(promotedResponse);
            if (!store(currentDocument.route()).put(currentRequest, promotedResponse)) {
                throw new IllegalStateException("Compatible Component response could not be promoted.");
            }
            if (!legacyRequest.identity().key().equals(currentRequest.identity().key())) {
                store(legacyDocument.route()).remove(legacyRequest);
            }
            ComponentTranslationDebugLogger.flow(
                    currentDocument.route(),
                    "candidate route={} phase=LEGACY_PROMOTION result=stored oldKey={} key={} oldCandidate={} candidate={} context={}",
                    currentDocument.route().wireName(),
                    legacyRequest.identity().key(),
                    currentRequest.identity().key(),
                    legacyCandidateHash,
                    candidateHash(promotedResponse),
                    requestContext == null ? "" : requestContext
            );
            return rendered;
        } catch (RuntimeException error) {
            store(legacyDocument.route()).remove(legacyRequest);
            ComponentTranslationDebugLogger.error(
                    currentDocument.route(),
                    "candidate route={} phase=LEGACY_PROMOTION result=rejected oldKey={} key={} oldCandidate={} context={} reason={}",
                    currentDocument.route().wireName(),
                    legacyRequest.identity().key(),
                    currentRequest.identity().key(),
                    legacyCandidateHash,
                    requestContext == null ? "" : requestContext,
                    resolvedFailureMessage(error),
                    error
            );
            return null;
        }
    }

    public static boolean claimFallbackGeneration(String primaryCacheKey) {
        if (!TranslationFeatureGate.isEnabled()) {
            return false;
        }
        if (primaryCacheKey == null || primaryCacheKey.isBlank() || !access().readyForTranslation()) {
            return true;
        }
        return STATE.claimFallbackGeneration(primaryCacheKey);
    }

    public static boolean clearFallbackGeneration(String primaryCacheKey) {
        if (primaryCacheKey == null || primaryCacheKey.isBlank()) {
            return false;
        }
        return STATE.clearFallbackGeneration(primaryCacheKey);
    }

    private static boolean queue(
            ComponentTranslationPreparedRequest request,
            String requestContext
    ) {
        if (!TranslationFeatureGate.isEnabled()) {
            return false;
        }
        long epoch = STATE.epoch();
        String cacheKey = request.identity().key();
        if (!registerQueued(request, requestContext, epoch)) {
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "queue route={} accepted=false key={} epoch={}",
                    request.document().route().wireName(),
                    cacheKey,
                    epoch
            );
            return false;
        }
        ComponentTranslationDebugLogger.flow(
                request.document().route(),
                "queue route={} accepted=true key={} epoch={}",
                request.document().route().wireName(),
                cacheKey,
                epoch
        );

        ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_QUEUED);
        DispatchRoute route = dispatchRoute(request.document().route());
        DispatchState state = DISPATCH.get(route);
        synchronized (state) {
            state.queue.add(new PendingRequest(
                    request,
                    epoch,
                    requestContext == null ? request.document().route().wireName() : requestContext
            ));
        }
        scheduleDrain(route);
        return true;
    }

    private static void scheduleDrain(DispatchRoute route) {
        scheduleDrain(route, ITEM_BATCH_COLLECT_DELAY_MILLIS);
    }

    private static void scheduleDrain(DispatchRoute route, long delayMillis) {
        DispatchState state = DISPATCH.get(route);
        synchronized (state) {
            if (state.drainScheduled) {
                return;
            }
            state.drainScheduled = true;
        }
        CompletableFuture.delayedExecutor(Math.max(0L, delayMillis), TimeUnit.MILLISECONDS).execute(() -> {
            synchronized (state) {
                state.drainScheduled = false;
            }
            drain(route);
        });
    }

    private static void drain(DispatchRoute route) {
        if (!TranslationFeatureGate.isEnabled()) {
            return;
        }
        DispatchState state = DISPATCH.get(route);
        int concurrencyLimit = maxConcurrency(route);
        int batchSizeLimit = maxBatchSize(route);
        while (true) {
            PendingBatch batch;
            long rateLimitDelayMillis;
            synchronized (state) {
                if (state.active.size() >= concurrencyLimit) {
                    return;
                }
                long now = System.currentTimeMillis();
                rateLimitDelayMillis = rateLimitDelayMillis(route, state, now);
                if (rateLimitDelayMillis > 0L) {
                    batch = null;
                } else {
                    batch = pollBatch(state, batchSizeLimit);
                    if (batch == null) {
                        return;
                    }
                    state.active.add(batch);
                    state.requestStartTimes.add(now);
                }
            }
            if (batch == null) {
                scheduleDrain(route, rateLimitDelayMillis);
                return;
            }
            startRequest(route, batch);
        }
    }

    private static PendingBatch pollBatch(DispatchState state, int maxBatchSize) {
        PendingRequest first = null;
        while ((first = state.queue.poll()) != null) {
            if (markWorkInFlight(first.cacheKey(), first.epoch())) {
                ComponentTranslationMetrics.record(first.document(), ComponentTranslationMetrics.Outcome.JOB_IN_FLIGHT);
                break;
            }
        }
        if (first == null) {
            return null;
        }

        List<PendingRequest> requests = new ArrayList<>(Math.max(1, maxBatchSize));
        requests.add(first);
        if (first.document().route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            return new PendingBatch(List.copyOf(requests));
        }
        Iterator<PendingRequest> iterator = state.queue.iterator();
        while (iterator.hasNext() && requests.size() < maxBatchSize) {
            PendingRequest candidate = iterator.next();
            if (candidate.document().route() != first.document().route()
                    || !candidate.targetLanguage().equals(first.targetLanguage())) {
                continue;
            }
            if (!ComponentTranslationBatch.canAppend(
                    requests.stream().map(PendingRequest::document).toList(),
                    candidate.document()
            )) {
                continue;
            }
            iterator.remove();
            if (markWorkInFlight(candidate.cacheKey(), candidate.epoch())) {
                ComponentTranslationMetrics.record(candidate.document(), ComponentTranslationMetrics.Outcome.JOB_IN_FLIGHT);
                requests.add(candidate);
            }
        }
        return new PendingBatch(List.copyOf(requests));
    }

    private static void startRequest(DispatchRoute route, PendingBatch batch) {
        if (!TranslationFeatureGate.isEnabled()) {
            for (PendingRequest request : batch.requests()) {
                failWork(request.cacheKey(), request.epoch());
            }
            finishRequest(route, batch);
            return;
        }
        PendingRequest first = batch.requests().get(0);
        ApiProviderProfile provider = ProviderRouteResolver.resolve(
                access().config(),
                switch (route) {
                    case ITEM -> ProviderRouteResolver.Route.ITEM;
                    case OTHER_TRANSLATIONS -> ProviderRouteResolver.Route.OTHER_TRANSLATIONS;
                    case SCOREBOARD -> ProviderRouteResolver.Route.SCOREBOARD;
                }
        );
        if (provider == null) {
            ComponentTranslationDebugLogger.flow(
                    first.document().route(),
                    "provider route={} result=missing batchSize={} key={}",
                    first.document().route().wireName(),
                    batch.requests().size(),
                    first.cacheKey()
            );
            access().onNoRoutedModel(
                    switch (route) {
                        case ITEM -> ProviderSurface.ITEM_TOOLTIP;
                        case OTHER_TRANSLATIONS -> ProviderSurface.OTHER_TRANSLATIONS;
                        case SCOREBOARD -> ProviderSurface.SCOREBOARD;
                    }
            );
            failBatch(
                    route,
                    batch,
                    "No routed model selected",
                    null,
                    FailureDisposition.INFRASTRUCTURE_FAILURE
            );
            return;
        }

        if (first.document().route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            startParagraphRequest(route, batch, first, provider);
            return;
        }

        long watchdogRequestId = TranslationQueueWatchdog.requestStarted(
                "component/" + route.name(),
                batch.requests().stream().map(PendingRequest::cacheKey).toList()
        );
        try {
            ComponentTranslationBatch translationBatch = ComponentTranslationBatch.create(
                    batch.requests().stream().map(PendingRequest::document).toList()
            );
            String batchContext = first.requestContext() + "; batch_size=" + batch.requests().size();
            access().translateResponse(
                    translationBatch.requestDocument(),
                    first.targetLanguage(),
                    provider,
                    batchContext
            ).whenComplete((result, error) -> {
                if (error != null) {
                    TranslationQueueWatchdog.requestFailed(
                            watchdogRequestId,
                            false
                    );
                    failBatch(route, batch, error.getMessage(), error, classifyFailure(error));
                    return;
                }
                try {
                    List<ComponentTranslationResponse> splitResponses = translationBatch.splitResponse(result);
                    for (int index = 0; index < batch.requests().size(); index++) {
                        PendingRequest request = batch.requests().get(index);
                        try {
                            completeRequest(request, splitResponses.get(index), batch.requests().size());
                        } catch (RuntimeException e) {
                            recordRequestFailure(request, e.getMessage(), e, classifyFailure(e));
                        }
                    }
                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                } catch (RuntimeException e) {
                    TranslationQueueWatchdog.requestFailed(watchdogRequestId, false);
                    failBatch(
                            route,
                            batch,
                            e.getMessage(),
                            e,
                            FailureDisposition.TERMINAL_CONTENT_FAILURE
                    );
                    return;
                }
                finishRequest(route, batch);
            });
        } catch (RuntimeException e) {
            TranslationQueueWatchdog.requestFailed(watchdogRequestId, false);
            failBatch(route, batch, e.getMessage(), e, FailureDisposition.TERMINAL_CONTENT_FAILURE);
        }
    }

    private static void startParagraphRequest(
            DispatchRoute route,
            PendingBatch batch,
            PendingRequest request,
            ApiProviderProfile provider
    ) {
        if (batch.requests().size() != 1) {
            failBatch(
                    route,
                    batch,
                    "Tooltip paragraph batch must contain one document",
                    null,
                    FailureDisposition.INFRASTRUCTURE_FAILURE
            );
            return;
        }
        long watchdogRequestId = TranslationQueueWatchdog.requestStarted(
                "component/" + route.name(),
                List.of(request.cacheKey())
        );
        try {
            access().translateResponse(
                    request.document(),
                    request.targetLanguage(),
                    provider,
                    request.requestContext()
            ).whenComplete((result, error) -> {
                if (error != null) {
                    TranslationQueueWatchdog.requestFailed(
                            watchdogRequestId,
                            false
                    );
                    failBatch(route, batch, error.getMessage(), error, classifyFailure(error));
                    return;
                }
                try {
                    completeRequest(request, result, 1);
                    TranslationQueueWatchdog.requestSucceeded(watchdogRequestId);
                } catch (RuntimeException e) {
                    TranslationQueueWatchdog.requestFailed(watchdogRequestId, false);
                    recordRequestFailure(request, e.getMessage(), e, classifyFailure(e));
                }
                finishRequest(route, batch);
            });
        } catch (RuntimeException e) {
            TranslationQueueWatchdog.requestFailed(watchdogRequestId, false);
            failBatch(route, batch, e.getMessage(), e, classifyFailure(e));
        }
    }

    private static void completeRequest(
            PendingRequest request,
            ComponentTranslationResponse response,
            int batchSize
    ) {
        String candidateHash = candidateHash(response);
        ComponentTranslationDebugLogger.flow(
                request.document().route(),
                "provider route={} phase=PROVIDER_VALIDATED key={} candidate={} epoch={}",
                request.document().route().wireName(),
                request.cacheKey(),
                candidateHash,
                request.epoch()
        );
        ComponentTranslationRuntimeState.WorkCompletion completion = completeAndStore(request, response);
        if (!completion.current()) {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.STALE_SESSION);
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_EXPIRED);
            return;
        }
        if (completion.refreshAfterCompletion()) {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_SUCCESS);
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "provider route={} phase=CACHE_COMMIT result=discarded_after_refresh batchSize={} key={} candidate={} epoch={}",
                    request.document().route().wireName(),
                    batchSize,
                    request.cacheKey(),
                    candidateHash,
                    request.epoch()
            );
            return;
        }
        if (completion.staged()) {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_SUCCESS);
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "provider route={} phase=CANDIDATE_STAGED result=staged batchSize={} key={} candidate={} epoch={}",
                    request.document().route().wireName(),
                    batchSize,
                    request.cacheKey(),
                    candidateHash,
                    request.epoch()
            );
            return;
        }
        if (completion.stored()) {
            STATE.removeFailure(request.cacheKey());
        }
        ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_SUCCESS);
        ComponentTranslationDebugLogger.flow(
                request.document().route(),
                "provider route={} phase=CACHE_COMMIT result=stored={} batchSize={} key={} candidate={} epoch={}",
                request.document().route().wireName(),
                completion.stored(),
                batchSize,
                request.cacheKey(),
                candidateHash,
                request.epoch()
        );
    }

    static String candidateHash(ComponentTranslationResponse response) {
        if (response == null) {
            return "sha256:" + "0".repeat(64);
        }
        StringBuilder canonical = new StringBuilder(response.protocol());
        response.translations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical
                        .append('\u001f')
                        .append(entry.getKey())
                        .append('\u001e')
                        .append(entry.getValue()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("sha256:");
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static <T> CandidateApplication<T> applyPendingCandidate(
            ComponentTranslationPreparedRequest request,
            Function<ComponentTranslationResponse, T> renderer,
            String requestContext
    ) {
        ComponentTranslationRuntimeState.PendingCandidate candidate =
                STATE.pendingCandidate(request.identity().key());
        if (candidate == null) {
            return CandidateApplication.absent();
        }
        if (candidate.epoch() != STATE.epoch()
                || !candidate.request().identity().equals(request.identity())) {
            STATE.removePendingCandidate(request.identity().key(), candidate);
            return CandidateApplication.absent();
        }
        CandidatePromotion<T> promotion = validateAndCommitCandidate(
                candidate.response(),
                renderer,
                () -> store(request.document().route()).put(request, candidate.response())
        );
        if (promotion.accepted()) {
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "candidate route={} phase=RENDER_ACCEPTED key={} candidate={} context={}",
                    request.document().route().wireName(),
                    request.identity().key(),
                    candidate.hash(),
                    requestContext == null ? candidate.requestContext() : requestContext
            );
            STATE.removePendingCandidate(request.identity().key(), candidate);
            STATE.removeFailure(request.identity().key());
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "candidate route={} phase=CACHE_COMMIT result=stored key={} candidate={}",
                    request.document().route().wireName(),
                    request.identity().key(),
                    candidate.hash()
            );
            return CandidateApplication.accepted(promotion.value());
        }
        STATE.removePendingCandidate(request.identity().key(), candidate);
        ComponentTranslationMetrics.record(
                request.document(),
                ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
        );
        ComponentTranslationDebugLogger.error(
                request.document().route(),
                "candidate route={} phase=RENDER_REJECTED key={} candidate={} context={} reason={}",
                request.document().route().wireName(),
                request.identity().key(),
                candidate.hash(),
                requestContext == null ? candidate.requestContext() : requestContext,
                promotion.errorMessage(),
                promotion.error()
        );
        return CandidateApplication.rejected(promotion.errorMessage());
    }

    static <T> CandidatePromotion<T> validateAndCommitCandidate(
            ComponentTranslationResponse response,
            Function<ComponentTranslationResponse, T> renderer,
            BooleanSupplier commit
    ) {
        try {
            T rendered = renderer.apply(response);
            if (!commit.getAsBoolean()) {
                throw new IllegalStateException("Accepted tooltip paragraph candidate could not be committed.");
            }
            return CandidatePromotion.accepted(rendered);
        } catch (RuntimeException error) {
            return CandidatePromotion.rejected(resolvedFailureMessage(error), error);
        }
    }

    private static void failBatch(
            DispatchRoute route,
            PendingBatch batch,
            String message,
            Throwable error,
            FailureDisposition disposition
    ) {
        for (PendingRequest request : batch.requests()) {
            recordRequestFailure(request, message, error, disposition);
        }
        finishRequest(route, batch);
    }

    private static void recordRequestFailure(
            PendingRequest request,
            String message,
            Throwable error,
            FailureDisposition disposition
    ) {
        failWork(request.cacheKey(), request.epoch());
        if (request.epoch() == STATE.epoch()) {
            String resolvedMessage = message == null || message.isBlank() ? "Component translation failed" : message;
            Throwable cause = error == null ? null : TranslateExceptionUtils.unwrapThrowable(error);
            STATE.putFailure(
                    request.cacheKey(),
                    new ComponentTranslationRuntimeState.FailureState<>(
                            resolvedMessage,
                            failureExpiresAtMillis(
                                    request.document().route(),
                                    disposition,
                                    System.currentTimeMillis()
                            ),
                            disposition == null ? FailureDisposition.INFRASTRUCTURE_FAILURE : disposition
                    )
            );
            ComponentTranslationMetrics.record(
                    request.document(),
                    cause instanceof ComponentJsonException
                            ? ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                            : ComponentTranslationMetrics.Outcome.PROVIDER_FAILURE
            );
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "provider route={} result=failure key={} epoch={} reason={}",
                    request.document().route().wireName(),
                    request.cacheKey(),
                    request.epoch(),
                    resolvedMessage
            );
            ComponentTranslationDebugLogger.error(
                    request.document().route(),
                    "route failed: route={} context={} reason={}",
                    request.document().route().wireName(),
                    request.requestContext(),
                    resolvedMessage,
                    error
            );
        } else {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.STALE_SESSION);
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_EXPIRED);
        }
    }

    private static void finishRequest(DispatchRoute route, PendingBatch batch) {
        DispatchState state = DISPATCH.get(route);
        synchronized (state) {
            state.active.remove(batch);
        }
        drain(route);
    }

    static long failureExpiresAtMillis(
            ComponentTranslationRoute route,
            FailureDisposition disposition,
            long nowMillis
    ) {
        if (disposition == FailureDisposition.TERMINAL_CONTENT_FAILURE) {
            return nowMillis + FAILURE_RETRY_COOLDOWN_MILLIS;
        }
        return Long.MAX_VALUE;
    }

    private static FailureDisposition classifyFailure(Throwable error) {
        Throwable cause = TranslateExceptionUtils.unwrapThrowable(error);
        return cause instanceof ComponentJsonException
                ? FailureDisposition.TERMINAL_CONTENT_FAILURE
                : FailureDisposition.INFRASTRUCTURE_FAILURE;
    }

    private static String resolvedFailureMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "Component translation failed" : error.getClass().getSimpleName();
        }
        return error.getMessage();
    }

    private static int maxConcurrency(DispatchRoute route) {
        ModConfig config = access().config();
        if (config == null) {
            return 1;
        }
        if (route == DispatchRoute.ITEM) {
            return config.itemTranslate == null ? 1 : Math.max(1, config.itemTranslate.max_concurrent_requests);
        }
        if (route == DispatchRoute.OTHER_TRANSLATIONS) {
            return config.otherTranslations == null
                    ? 1
                    : Math.max(1, config.otherTranslations.max_concurrent_requests);
        }
        return config.scoreboardTranslate == null
                ? 1
                : Math.max(1, config.scoreboardTranslate.max_concurrent_requests);
    }

    private static int maxBatchSize(DispatchRoute route) {
        ModConfig config = access().config();
        if (config == null) {
            return 1;
        }
        if (route == DispatchRoute.ITEM) {
            return config.itemTranslate == null ? 1 : Math.max(1, config.itemTranslate.max_batch_size);
        }
        if (route == DispatchRoute.OTHER_TRANSLATIONS) {
            return config.otherTranslations == null
                    ? 1
                    : Math.max(1, config.otherTranslations.max_batch_size);
        }
        return config.scoreboardTranslate == null
                ? 1
                : Math.max(1, config.scoreboardTranslate.max_batch_size);
    }

    private static long rateLimitDelayMillis(DispatchRoute route, DispatchState state, long now) {
        int requestsPerMinute = requestsPerMinute(route);
        if (requestsPerMinute <= 0) {
            state.requestStartTimes.clear();
            return 0L;
        }
        long windowStart = now - REQUEST_RATE_WINDOW_MILLIS;
        while (!state.requestStartTimes.isEmpty() && state.requestStartTimes.peek() <= windowStart) {
            state.requestStartTimes.poll();
        }
        if (state.requestStartTimes.size() < requestsPerMinute) {
            return 0L;
        }
        return Math.max(1L, state.requestStartTimes.peek() + REQUEST_RATE_WINDOW_MILLIS - now);
    }

    private static int requestsPerMinute(DispatchRoute route) {
        return route == DispatchRoute.OTHER_TRANSLATIONS ? OTHER_TRANSLATIONS_REQUESTS_PER_MINUTE : 0;
    }

    private static DispatchRoute dispatchRoute(ComponentTranslationRoute route) {
        return switch (route) {
            case ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI ->
                    DispatchRoute.OTHER_TRANSLATIONS;
            case SCOREBOARD -> DispatchRoute.SCOREBOARD;
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH, CHAT_OUTPUT -> DispatchRoute.ITEM;
        };
    }

    private static Map<DispatchRoute, DispatchState> createDispatchStates() {
        Map<DispatchRoute, DispatchState> result = new EnumMap<>(DispatchRoute.class);
        for (DispatchRoute route : DispatchRoute.values()) {
            result.put(route, new DispatchState());
        }
        return result;
    }

    private static ComponentTranslationPreparedRequest preparedRequest(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        return STATE.preparedRequest(document, targetLanguage);
    }

    private static ComponentTranslationStore store(ComponentTranslationRoute route) {
        return access().store(route);
    }

    private static boolean isTooltipLegacyCompatibilityRoute(ComponentTranslationRoute route) {
        return route == ComponentTranslationRoute.TOOLTIP_LINE
                || route == ComponentTranslationRoute.TOOLTIP_STRUCTURED
                || route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH;
    }

    private static boolean registerQueued(
            ComponentTranslationPreparedRequest request,
            String requestContext,
            long epoch
    ) {
        return STATE.registerQueued(request, requestContext, epoch);
    }

    private static boolean hasActiveWork(String cacheKey) {
        return STATE.hasActiveWork(cacheKey);
    }

    private static boolean markEntityTemplateSeeded(String fullCacheKey) {
        return STATE.markEntityTemplateSeeded(fullCacheKey);
    }

    private static boolean markWorkInFlight(String cacheKey, long epoch) {
        return STATE.markWorkInFlight(cacheKey, epoch);
    }

    private static ComponentTranslationRuntimeState.WorkCompletion completeAndStore(
            PendingRequest request,
            ComponentTranslationResponse response
    ) {
        return STATE.complete(
                request.prepared(),
                response,
                request.epoch(),
                TranslationFeatureGate.isEnabled(),
                shouldStageCandidate(request),
                candidateHash(response),
                request.requestContext(),
                () -> store(request.prepared().document().route()).put(request.prepared(), response)
        );
    }

    private static void failWork(String cacheKey, long epoch) {
        STATE.failWork(cacheKey, epoch);
    }

    private static boolean requestRefresh(String cacheKey) {
        return STATE.requestRefresh(cacheKey);
    }

    public record Resolution<T>(
            State state,
            T value,
            String cacheKey,
            String errorMessage,
            FailureDisposition failureDisposition,
            boolean inFlight
    ) {
        public Resolution(State state, T value, String cacheKey, String errorMessage) {
            this(state, value, cacheKey, errorMessage, defaultDisposition(state));
        }

        public Resolution(
                State state,
                T value,
                String cacheKey,
                String errorMessage,
                FailureDisposition failureDisposition
        ) {
            this(
                    state,
                    value,
                    cacheKey,
                    errorMessage,
                    failureDisposition,
                    state == State.PENDING && isWorkInFlight(cacheKey)
            );
        }

        public Resolution {
            cacheKey = cacheKey == null ? "" : cacheKey;
            errorMessage = errorMessage == null ? "" : errorMessage;
            failureDisposition = failureDisposition == null ? defaultDisposition(state) : failureDisposition;
            inFlight = state == State.PENDING && isWorkInFlight(cacheKey);
        }

        public boolean allowsTooltipFallback() {
            return failureDisposition == FailureDisposition.TERMINAL_CONTENT_FAILURE;
        }

        private static FailureDisposition defaultDisposition(State state) {
            if (state == State.FAILED) {
                return FailureDisposition.TERMINAL_CONTENT_FAILURE;
            }
            if (state == State.INELIGIBLE) {
                return FailureDisposition.INELIGIBLE;
            }
            return FailureDisposition.NONE;
        }
    }

    public enum FailureDisposition {
        NONE,
        TERMINAL_CONTENT_FAILURE,
        INFRASTRUCTURE_FAILURE,
        INELIGIBLE
    }

    public enum State {
        CACHE_HIT,
        LEGACY_HIT,
        PENDING,
        FAILED,
        MISS,
        NO_TEXT,
        INELIGIBLE
    }

    public enum ProviderSurface {
        ITEM_TOOLTIP,
        OTHER_TRANSLATIONS,
        SCOREBOARD
    }

    public interface Access {
        ModConfig config();

        boolean readyForTranslation();

        ComponentTranslationStore store(ComponentTranslationRoute route);

        CompletableFuture<ComponentTranslationResponse> translateResponse(
                ComponentTranslationDocument document,
                String targetLanguage,
                ApiProviderProfile provider,
                String requestContext
        );

        void onNoRoutedModel(ProviderSurface surface);

        void flow(ComponentTranslationRoute route, String message, Object... arguments);

        void error(ComponentTranslationRoute route, String message, Object... arguments);

        void textContent(ComponentTranslationDocument document, String cacheKey);

        void entityIdentityMiss(
                ComponentTranslationDocument document,
                String targetLanguage,
                ComponentTranslationCacheIdentity identity,
                String lookupStatus
        );

        void entityTemplateReuse(String fullKey, String templateKey);
    }

    private enum DispatchRoute {
        ITEM,
        OTHER_TRANSLATIONS,
        SCOREBOARD
    }

    private static final class DispatchState {
        private final Queue<PendingRequest> queue = new ArrayDeque<>();
        private final java.util.Set<PendingBatch> active = new HashSet<>();
        private final Queue<Long> requestStartTimes = new ArrayDeque<>();
        private boolean drainScheduled;
    }

    private record PendingBatch(List<PendingRequest> requests) {
        private PendingBatch {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Pending component translation batch is empty.");
            }
            requests = List.copyOf(requests);
        }
    }

    private record PendingRequest(
            ComponentTranslationPreparedRequest prepared,
            long epoch,
            String requestContext
    ) {
        private ComponentTranslationDocument document() {
            return prepared.document();
        }

        private String targetLanguage() {
            return prepared.targetLanguage();
        }

        private String cacheKey() {
            return prepared.identity().key();
        }
    }

    private static boolean shouldStageCandidate(PendingRequest request) {
        if (request == null || request.document() == null) {
            return false;
        }
        if (request.document().route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            return true;
        }
        return request.document().route() == ComponentTranslationRoute.TOOLTIP_LINE
                && "paragraph-line-fallback-v1".equals(
                request.document().semanticSettings().get("route_policy")
        );
    }

    private record CandidateApplication<T>(
            boolean present,
            boolean accepted,
            T value,
            String errorMessage
    ) {
        private boolean rejected() {
            return present && !accepted;
        }

        private static <T> CandidateApplication<T> absent() {
            return new CandidateApplication<>(false, false, null, "");
        }

        private static <T> CandidateApplication<T> accepted(T value) {
            return new CandidateApplication<>(true, true, value, "");
        }

        private static <T> CandidateApplication<T> rejected(String errorMessage) {
            return new CandidateApplication<>(true, false, null, errorMessage == null ? "" : errorMessage);
        }
    }

    private static final class ComponentTranslationDebugLogger {
        private ComponentTranslationDebugLogger() {
        }

        private static void flow(ComponentTranslationRoute route, String message, Object... arguments) {
            access().flow(route, message, arguments);
        }

        private static void error(ComponentTranslationRoute route, String message, Object... arguments) {
            access().error(route, message, arguments);
        }

        private static void textContent(ComponentTranslationDocument document, String cacheKey) {
            access().textContent(document, cacheKey);
        }

        private static void entityIdentityMiss(
                ComponentTranslationDocument document,
                String targetLanguage,
                ComponentTranslationCacheIdentity identity,
                String lookupStatus
        ) {
            access().entityIdentityMiss(document, targetLanguage, identity, lookupStatus);
        }

        private static void entityTemplateReuse(String fullKey, String templateKey) {
            access().entityTemplateReuse(fullKey, templateKey);
        }
    }

    record CandidatePromotion<T>(
            boolean accepted,
            T value,
            String errorMessage,
            RuntimeException error
    ) {
        private static <T> CandidatePromotion<T> accepted(T value) {
            return new CandidatePromotion<>(true, value, "", null);
        }

        private static <T> CandidatePromotion<T> rejected(String errorMessage, RuntimeException error) {
            return new CandidatePromotion<>(false, null, errorMessage == null ? "" : errorMessage, error);
        }
    }
}
