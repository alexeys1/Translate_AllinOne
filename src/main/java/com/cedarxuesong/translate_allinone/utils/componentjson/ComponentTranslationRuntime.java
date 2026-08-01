package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationStore;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ComponentTranslationRuntime {
    private static final int DOCUMENT_CACHE_LIMIT = 512;
    private static final long FAILURE_COOLDOWN_MILLIS = 30_000L;
    private static final long ITEM_BATCH_COLLECT_DELAY_MILLIS = 10L;
    private static final long REQUEST_RATE_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final int OTHER_TRANSLATIONS_REQUESTS_PER_MINUTE = 60;
    private static final ComponentTranslationClient CLIENT = new ComponentTranslationClient();
    private static final Map<DispatchRoute, DispatchState> DISPATCH = createDispatchStates();
    private static final Map<String, FailureState> FAILURES = new ConcurrentHashMap<>();
    private static final AtomicLong SESSION_EPOCH = new AtomicLong();
    private static final Object WORK_LOCK = new Object();
    private static final Map<String, TranslationWork> WORKS = new LinkedHashMap<>();
    private static final Map<DocumentMemoKey, MemoizedDocument> DOCUMENTS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<DocumentMemoKey, MemoizedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            }
    );
    private static final Map<PreparedRequestMemoKey, ComponentTranslationPreparedRequest> PREPARED_REQUESTS =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<PreparedRequestMemoKey, ComponentTranslationPreparedRequest> eldest
                ) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            });
    private static final Map<String, Boolean> ENTITY_TEMPLATE_SEEDS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            });

    private ComponentTranslationRuntime() {
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        if (route == null) {
            throw new IllegalArgumentException("Component translation document input is incomplete.");
        }
        String resolvedContext = context == null || context.isBlank() ? route.wireName() : context.trim();
        String resolvedPolicyVersion = policyVersion == null || policyVersion.isBlank() ? "1" : policyVersion.trim();
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route)
                .withContext(resolvedContext)
                .withSemanticSetting("route_policy", resolvedPolicyVersion);
        return prepare(component, policy);
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationPolicy policy
    ) {
        if (component == null || policy == null) {
            throw new IllegalArgumentException("Component translation document input is incomplete.");
        }
        DocumentMemoKey key = new DocumentMemoKey(
                policy.route(),
                policy.version(),
                policy.semanticSettings(),
                component.hashCode(),
                component.getString()
        );
        MemoizedDocument memoized = DOCUMENTS.get(key);
        if (memoized != null && memoized.source().equals(component)) {
            return memoized.document();
        }

        long startedAt = System.nanoTime();
        try {
            ComponentTranslationDocument document = new ComponentDocumentBuilder().build(component, policy);
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.DOCUMENT_BUILT);
            ComponentTranslationMetrics.recordValue(
                    document,
                    ComponentTranslationMetrics.Measurement.TEXT_UNITS,
                    document.units().size()
            );
            if (document.units().isEmpty()) {
                ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.NO_TEXT);
            }
            DOCUMENTS.put(key, new MemoizedDocument(component.copy(), document));
            return document;
        } catch (RuntimeException e) {
            ComponentTranslationMetrics.record(policy.route(), ComponentTranslationMetrics.Outcome.DOCUMENT_FAILED);
            if (e instanceof ComponentJsonException componentError
                    && componentError.kind() == ComponentJsonException.Kind.CODEC) {
                ComponentTranslationMetrics.record(
                        policy.route(),
                        ComponentTranslationMetrics.Outcome.CODEC_ENCODE_FAILURE
                );
            }
            throw e;
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    policy.route(),
                    ComponentTranslationMetrics.Timing.DOCUMENT_BUILD,
                    System.nanoTime() - startedAt
            );
        }
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
            if (lookup.status() == ComponentTranslationStore.Status.HIT) {
                try {
                    T rendered = renderer.apply(lookup.response());
                    if (document.route() == ComponentTranslationRoute.ENTITY_NAME
                            && markEntityTemplateSeeded(request.identity().key())) {
                        store(request.document().route()).put(request, lookup.response());
                    }
                    FAILURES.remove(lookup.cacheKey());
                    ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.CACHE_HIT);
                    ComponentTranslationDebugLogger.flow(
                            document.route(),
                            "resolve route={} state=CACHE_HIT key={}",
                            document.route().wireName(),
                            lookup.cacheKey()
                    );


                    return new Resolution<>(State.CACHE_HIT, rendered, lookup.cacheKey(), "");
                } catch (RuntimeException error) {
                    store(request.document().route()).remove(request);
                    ComponentTranslationMetrics.record(
                            document,
                            ComponentTranslationMetrics.Outcome.RESPONSE_REJECTED
                    );
                    ComponentTranslationDebugLogger.error(
                            document.route(),
                            "Component cached response apply failed: route={} key={} reason={}",
                            document.route().wireName(),
                            lookup.cacheKey(),
                            error.getMessage(),
                            error
                    );
                }
            }
            if (document.route() == ComponentTranslationRoute.ENTITY_NAME) {
                ComponentTranslationStore.Lookup templateLookup = store(request.document().route()).lookupEntityTemplate(request);
                if (templateLookup.status() == ComponentTranslationStore.Status.HIT) {
                    try {
                        T rendered = renderer.apply(templateLookup.response());
                        store(request.document().route()).put(request, templateLookup.response());
                        FAILURES.remove(request.identity().key());
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
            return new Resolution<>(State.INELIGIBLE, null, request.identity().key(), e.getMessage());
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    document.route(),
                    ComponentTranslationMetrics.Timing.CACHE_LOOKUP,
                    System.nanoTime() - lookupStartedAt
            );
        }

        if (isTooltipLegacyCompatibilityRoute(document.route()) && legacyLookup != null) {
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
        }

        if (store(request.document().route()).isWriteProtected()) {
            ComponentTranslationMetrics.record(document, ComponentTranslationMetrics.Outcome.FALLBACK_ORIGINAL);
            return new Resolution<>(
                    State.FAILED,
                    null,
                    request.identity().key(),
                    "Component cache is write-protected for this session."
            );
        }

        FailureState failure = activeFailure(request.identity().key());
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
            return new Resolution<>(State.FAILED, null, request.identity().key(), failure.message());
        }
        if (!queueIfMissing) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=MISS queue=false key={}",
                    document.route().wireName(),
                    request.identity().key()
            );
            return new Resolution<>(State.MISS, null, request.identity().key(), "");
        }
        if (!LifecycleEventManager.isReadyForTranslation) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=PENDING reason=client_not_ready key={}",
                    document.route().wireName(),
                    request.identity().key()
            );
            return new Resolution<>(State.PENDING, null, request.identity().key(), "");
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
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            return false;
        }
        ComponentTranslationPreparedRequest request = preparedRequest(document, targetLanguage);
        String key = request.identity().key();
        FAILURES.remove(key);
        ENTITY_TEMPLATE_SEEDS.remove(key);
        boolean refreshRequested = requestRefresh(key);
        boolean removed = store(document.route()).remove(request);
        boolean templateRemoved = store(document.route()).removeEntityTemplate(request);
        return removed || templateRemoved || refreshRequested;
    }

    public static long beginSession() {
        long epoch = SESSION_EPOCH.incrementAndGet();
        clearRuntimeState();
        return epoch;
    }

    public static long endSession() {
        long epoch = SESSION_EPOCH.incrementAndGet();
        clearRuntimeState();
        return epoch;
    }

    public static void resetSession() {
        clearRuntimeState();
    }

    private static void clearRuntimeState() {
        FAILURES.clear();
        DOCUMENTS.clear();
        PREPARED_REQUESTS.clear();
        ENTITY_TEMPLATE_SEEDS.clear();
        synchronized (WORK_LOCK) {
            WORKS.clear();
        }
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

    private static boolean queue(
            ComponentTranslationPreparedRequest request,
            String requestContext
    ) {
        long epoch = SESSION_EPOCH.get();
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
        DispatchState state = DISPATCH.get(route);
        while (true) {
            PendingBatch batch;
            long rateLimitDelayMillis;
            synchronized (state) {
                if (state.active.size() >= maxConcurrency(route)) {
                    return;
                }
                long now = System.currentTimeMillis();
                rateLimitDelayMillis = rateLimitDelayMillis(route, state, now);
                if (rateLimitDelayMillis > 0L) {
                    batch = null;
                } else {
                    batch = pollBatch(state, maxBatchSize(route));
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
        PendingRequest first = batch.requests().getFirst();
        ApiProviderProfile provider = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
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
            failBatch(route, batch, "No routed model selected", null);
            return;
        }

        if (first.document().route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
            startParagraphRequest(route, batch, first, provider);
            return;
        }

        try {
            ComponentTranslationBatch translationBatch = ComponentTranslationBatch.create(
                    batch.requests().stream().map(PendingRequest::document).toList()
            );
            String batchContext = first.requestContext() + "; batch_size=" + batch.requests().size();
            CLIENT.translateResponse(
                    translationBatch.requestDocument(),
                    first.targetLanguage(),
                    provider,
                    batchContext
            ).whenComplete((result, error) -> {
                if (error != null) {
                    failBatch(route, batch, error.getMessage(), error);
                    return;
                }
                try {
                    List<ComponentTranslationResponse> splitResponses = translationBatch.splitResponse(result);
                    for (int index = 0; index < batch.requests().size(); index++) {
                        PendingRequest request = batch.requests().get(index);
                        try {
                            completeRequest(request, splitResponses.get(index), batch.requests().size());
                        } catch (RuntimeException e) {
                            recordRequestFailure(request, e.getMessage(), e);
                        }
                    }
                } catch (RuntimeException e) {
                    failBatch(route, batch, e.getMessage(), e);
                    return;
                }
                finishRequest(route, batch);
            });
        } catch (RuntimeException e) {
            failBatch(route, batch, e.getMessage(), e);
        }
    }

    private static void startParagraphRequest(
            DispatchRoute route,
            PendingBatch batch,
            PendingRequest request,
            ApiProviderProfile provider
    ) {
        if (batch.requests().size() != 1) {
            failBatch(route, batch, "Tooltip paragraph batch must contain one document", null);
            return;
        }
        try {
            CLIENT.translateResponse(
                    request.document(),
                    request.targetLanguage(),
                    provider,
                    request.requestContext()
            ).whenComplete((result, error) -> {
                if (error != null) {
                    failBatch(route, batch, error.getMessage(), error);
                    return;
                }
                try {
                    completeRequest(request, result, 1);
                } catch (RuntimeException e) {
                    recordRequestFailure(request, e.getMessage(), e);
                }
                finishRequest(route, batch);
            });
        } catch (RuntimeException e) {
            failBatch(route, batch, e.getMessage(), e);
        }
    }

    private static void completeRequest(
            PendingRequest request,
            ComponentTranslationResponse response,
            int batchSize
    ) {
        WorkCompletion completion = completeAndStore(request, response);
        if (!completion.current()) {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.STALE_SESSION);
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_EXPIRED);
            return;
        }
        if (completion.refreshAfterCompletion()) {
            ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_SUCCESS);
            ComponentTranslationDebugLogger.flow(
                    request.document().route(),
                    "provider route={} result=discarded_after_refresh batchSize={} key={} epoch={}",
                    request.document().route().wireName(),
                    batchSize,
                    request.cacheKey(),
                    request.epoch()
            );
            return;
        }
        if (completion.stored()) {
            FAILURES.remove(request.cacheKey());
        }
        ComponentTranslationMetrics.record(request.document(), ComponentTranslationMetrics.Outcome.JOB_SUCCESS);
        ComponentTranslationDebugLogger.flow(
                request.document().route(),
                "provider route={} result=stored={} batchSize={} key={} epoch={}",
                request.document().route().wireName(),
                completion.stored(),
                batchSize,
                request.cacheKey(),
                request.epoch()
        );
    }

    private static void failBatch(DispatchRoute route, PendingBatch batch, String message, Throwable error) {
        for (PendingRequest request : batch.requests()) {
            recordRequestFailure(request, message, error);
        }
        finishRequest(route, batch);
    }

    private static void recordRequestFailure(PendingRequest request, String message, Throwable error) {
        failWork(request.cacheKey(), request.epoch());
        if (request.epoch() == SESSION_EPOCH.get()) {
            String resolvedMessage = message == null || message.isBlank() ? "Component translation failed" : message;
            FAILURES.put(
                    request.cacheKey(),
                    new FailureState(resolvedMessage, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS)
            );
            ComponentTranslationMetrics.record(
                    request.document(),
                    error instanceof ComponentJsonException
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

    private static FailureState activeFailure(String key) {
        FailureState failure = FAILURES.get(key);
        if (failure == null) {
            return null;
        }
        if (System.currentTimeMillis() <= failure.expiresAtMillis()) {
            return failure;
        }
        FAILURES.remove(key, failure);
        return null;
    }

    private static int maxConcurrency(DispatchRoute route) {
        ModConfig config = Translate_AllinOne.getConfig();
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
        ModConfig config = Translate_AllinOne.getConfig();
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
            case ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE ->
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
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Component translation document and target language are required.");
        }
        String normalizedLanguage = targetLanguage.trim();
        PreparedRequestMemoKey key = new PreparedRequestMemoKey(document, normalizedLanguage);
        ComponentTranslationPreparedRequest memoized = PREPARED_REQUESTS.get(key);
        if (memoized != null) {
            return memoized;
        }
        long startedAt = System.nanoTime();
        try {
            ComponentTranslationPreparedRequest prepared = ComponentTranslationPreparedRequest.create(document, normalizedLanguage);
            PREPARED_REQUESTS.put(key, prepared);
            return prepared;
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    document.route(),
                    ComponentTranslationMetrics.Timing.HASH,
                    System.nanoTime() - startedAt
            );
        }
    }

    private static ComponentTranslationStore store(ComponentTranslationRoute route) {
        return StoreHolder.INSTANCE.forRoute(route);
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
        synchronized (WORK_LOCK) {
            if (epoch != SESSION_EPOCH.get() || WORKS.containsKey(request.identity().key())) {
                return false;
            }
            WORKS.put(
                    request.identity().key(),
                    new TranslationWork(request, epoch, requestContext, WorkState.QUEUED, false)
            );
            return true;
        }
    }

    private static boolean markEntityTemplateSeeded(String fullCacheKey) {
        synchronized (ENTITY_TEMPLATE_SEEDS) {
            return ENTITY_TEMPLATE_SEEDS.put(fullCacheKey, Boolean.TRUE) == null;
        }
    }

    private static boolean markWorkInFlight(String cacheKey, long epoch) {
        synchronized (WORK_LOCK) {
            TranslationWork work = WORKS.get(cacheKey);
            if (work == null || work.epoch() != epoch || work.state() != WorkState.QUEUED) {
                return false;
            }
            WORKS.put(cacheKey, work.withState(WorkState.IN_FLIGHT));
            return true;
        }
    }

    private static WorkCompletion completeAndStore(
            PendingRequest request,
            ComponentTranslationResponse response
    ) {
        synchronized (WORK_LOCK) {
            TranslationWork work = WORKS.get(request.cacheKey());
            if (work == null || work.epoch() != request.epoch() || request.epoch() != SESSION_EPOCH.get()) {
                return WorkCompletion.STALE;
            }
            if (work.refreshAfterCompletion()) {
                WORKS.remove(request.cacheKey());
                return new WorkCompletion(true, true, false);
            }
            boolean stored = store(request.prepared().document().route()).put(request.prepared(), response);
            WORKS.remove(request.cacheKey());
            return new WorkCompletion(true, false, stored);
        }
    }

    private static void failWork(String cacheKey, long epoch) {
        synchronized (WORK_LOCK) {
            TranslationWork work = WORKS.get(cacheKey);
            if (work != null && work.epoch() == epoch) {
                WORKS.remove(cacheKey);
            }
        }
    }

    private static boolean requestRefresh(String cacheKey) {
        synchronized (WORK_LOCK) {
            TranslationWork work = WORKS.get(cacheKey);
            if (work == null) {
                return false;
            }
            if (work.state() == WorkState.QUEUED) {
                WORKS.remove(cacheKey);
                return true;
            }
            WORKS.put(cacheKey, work.withRefreshAfterCompletion());
            return true;
        }
    }

    private static final class StoreHolder {
        private static final ComponentTranslationStoreRegistry INSTANCE = ComponentTranslationStoreRegistry.getInstance();
    }

    public record Resolution<T>(State state, T value, String cacheKey, String errorMessage) {
        public Resolution {
            cacheKey = cacheKey == null ? "" : cacheKey;
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
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

    private record WorkCompletion(boolean current, boolean refreshAfterCompletion, boolean stored) {
        private static final WorkCompletion STALE = new WorkCompletion(false, false, false);
    }

    private enum WorkState {
        QUEUED,
        IN_FLIGHT
    }

    private record DocumentMemoKey(
            ComponentTranslationRoute route,
            int policyVersion,
            Map<String, String> semanticSettings,
            int componentHash,
            String plainText
    ) {
    }

    private record MemoizedDocument(Component source, ComponentTranslationDocument document) {
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

    private record FailureState(String message, long expiresAtMillis) {
    }
}
