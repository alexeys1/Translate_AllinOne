package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationCache;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationJob;
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
    private static final ComponentTranslationClient CLIENT = new ComponentTranslationClient();
    private static final ComponentTranslationCache CACHE = ComponentTranslationCache.getInstance();
    private static final Map<DispatchRoute, DispatchState> DISPATCH = createDispatchStates();
    private static final Map<String, FailureState> FAILURES = new ConcurrentHashMap<>();
    private static final Map<DocumentMemoKey, MemoizedDocument> DOCUMENTS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<DocumentMemoKey, MemoizedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_LIMIT;
                }
            }
    );

    private ComponentTranslationRuntime() {
    }

    public static ComponentTranslationDocument prepare(
            Component component,
            ComponentTranslationRoute route,
            String context,
            String policyVersion
    ) {
        if (component == null || route == null) {
            throw new IllegalArgumentException("Component translation document input is incomplete.");
        }
        String resolvedContext = context == null || context.isBlank() ? route.wireName() : context.trim();
        String resolvedPolicyVersion = policyVersion == null || policyVersion.isBlank() ? "1" : policyVersion.trim();
        DocumentMemoKey key = new DocumentMemoKey(
                route,
                resolvedContext,
                resolvedPolicyVersion,
                component.hashCode(),
                component.getString()
        );
        MemoizedDocument memoized = DOCUMENTS.get(key);
        if (memoized != null && memoized.source().equals(component)) {
            return memoized.document();
        }

        long startedAt = System.nanoTime();
        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(route)
                .withContext(resolvedContext)
                .withSemanticSetting("route_policy", resolvedPolicyVersion);
        ComponentTranslationDocument document = new ComponentDocumentBuilder().build(component, policy);
        ComponentTranslationMetrics.recordNanos(
                route,
                ComponentTranslationMetrics.Timing.DOCUMENT_BUILD,
                System.nanoTime() - startedAt
        );
        DOCUMENTS.put(key, new MemoizedDocument(component.copy(), document));
        return document;
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
            return new Resolution<>(State.INELIGIBLE, null, "", "Incomplete Component V1 request");
        }
        if (document.units().isEmpty()) {
            return new Resolution<>(State.NO_TEXT, null, "", "");
        }

        long lookupStartedAt = System.nanoTime();
        ComponentTranslationCache.DualReadResult<T> lookup;
        try {
            lookup = CACHE.lookupWithLegacy(document, targetLanguage, renderer, legacyLookup);
        } catch (RuntimeException e) {
            return new Resolution<>(State.INELIGIBLE, null, "", e.getMessage());
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    document.route(),
                    ComponentTranslationMetrics.Timing.CACHE_LOOKUP,
                    System.nanoTime() - lookupStartedAt
            );
        }

        if (lookup.source() == ComponentTranslationCache.Source.V1) {
            FAILURES.remove(lookup.cacheKey());
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=V1_HIT key={}",
                    document.route().wireName(),
                    lookup.cacheKey()
            );
            return new Resolution<>(State.V1_HIT, lookup.value(), lookup.cacheKey(), "");
        }
        if (lookup.source() == ComponentTranslationCache.Source.LEGACY) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=LEGACY_HIT key={}",
                    document.route().wireName(),
                    lookup.cacheKey()
            );
            return new Resolution<>(State.LEGACY_HIT, lookup.value(), lookup.cacheKey(), "");
        }

        FailureState failure = activeFailure(lookup.cacheKey());
        if (failure != null) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=FAILED cooldown=true key={}",
                    document.route().wireName(),
                    lookup.cacheKey()
            );
            return new Resolution<>(State.FAILED, null, lookup.cacheKey(), failure.message());
        }
        if (!queueIfMissing) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=MISS queue=false key={}",
                    document.route().wireName(),
                    lookup.cacheKey()
            );
            return new Resolution<>(State.MISS, null, lookup.cacheKey(), "");
        }
        if (!LifecycleEventManager.isReadyForTranslation) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "resolve route={} state=PENDING reason=client_not_ready key={}",
                    document.route().wireName(),
                    lookup.cacheKey()
            );
            return new Resolution<>(State.PENDING, null, lookup.cacheKey(), "");
        }
        boolean queued = queue(document, targetLanguage, legacyKey, requestContext);
        ComponentTranslationDebugLogger.flow(
                document.route(),
                "resolve route={} state=PENDING queued={} key={}",
                document.route().wireName(),
                queued,
                lookup.cacheKey()
        );
        return new Resolution<>(State.PENDING, null, lookup.cacheKey(), queued ? "" : "");
    }

    public static boolean forceRefresh(ComponentTranslationDocument document, String targetLanguage) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            return false;
        }
        String key = CACHE.cacheKey(document, targetLanguage);
        FAILURES.remove(key);
        return CACHE.forceRefresh(document, targetLanguage);
    }

    public static void resetSession() {
        FAILURES.clear();
        DOCUMENTS.clear();
        for (DispatchState state : DISPATCH.values()) {
            synchronized (state) {
                state.queue.clear();
                state.active.clear();
                state.drainScheduled = false;
            }
        }
    }

    public static String cacheKey(ComponentTranslationDocument document, String targetLanguage) {
        return CACHE.cacheKey(document, targetLanguage);
    }

    private static boolean queue(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            String requestContext
    ) {
        long epoch = CACHE.currentSessionEpoch();
        long keyStartedAt = System.nanoTime();
        String cacheKey = CACHE.cacheKey(document, targetLanguage);
        ComponentTranslationMetrics.recordNanos(
                document.route(),
                ComponentTranslationMetrics.Timing.CACHE_KEY,
                System.nanoTime() - keyStartedAt
        );
        if (!CACHE.queueJob(document, targetLanguage, legacyKey, epoch)) {
            ComponentTranslationDebugLogger.flow(
                    document.route(),
                    "queue route={} accepted=false key={} epoch={}",
                    document.route().wireName(),
                    cacheKey,
                    epoch
            );
            return false;
        }
        ComponentTranslationDebugLogger.flow(
                document.route(),
                "queue route={} accepted=true key={} epoch={}",
                document.route().wireName(),
                cacheKey,
                epoch
        );

        DispatchRoute route = dispatchRoute(document.route());
        DispatchState state = DISPATCH.get(route);
        synchronized (state) {
            state.queue.add(new PendingRequest(
                    document,
                    targetLanguage.trim(),
                    cacheKey,
                    legacyKey,
                    epoch,
                    requestContext == null ? document.route().wireName() : requestContext
            ));
        }
        scheduleDrain(route);
        return true;
    }

    private static void scheduleDrain(DispatchRoute route) {
        DispatchState state = DISPATCH.get(route);
        synchronized (state) {
            if (state.drainScheduled) {
                return;
            }
            state.drainScheduled = true;
        }
        CompletableFuture.delayedExecutor(ITEM_BATCH_COLLECT_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
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
            synchronized (state) {
                if (state.active.size() >= maxConcurrency(route)) {
                    return;
                }
                batch = pollBatch(state, maxBatchSize(route));
                if (batch == null) {
                    return;
                }
                state.active.add(batch);
            }
            startRequest(route, batch);
        }
    }

    private static PendingBatch pollBatch(DispatchState state, int maxBatchSize) {
        PendingRequest first = null;
        while ((first = state.queue.poll()) != null) {
            if (CACHE.markJobInFlight(first.cacheKey(), first.epoch())) {
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
            if (CACHE.markJobInFlight(candidate.cacheKey(), candidate.epoch())) {
                requests.add(candidate);
            }
        }
        return new PendingBatch(List.copyOf(requests));
    }

    private static void startRequest(DispatchRoute route, PendingBatch batch) {
        PendingRequest first = batch.requests().getFirst();
        ApiProviderProfile provider = ProviderRouteResolver.resolve(
                Translate_AllinOne.getConfig(),
                route == DispatchRoute.ITEM
                        ? ProviderRouteResolver.Route.ITEM
                        : ProviderRouteResolver.Route.OTHER_TRANSLATIONS
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
        boolean stored = CACHE.put(
                request.document(),
                request.targetLanguage(),
                response,
                request.epoch()
        );
        Optional<ComponentTranslationJob> refresh = CACHE.completeJob(
                request.cacheKey(),
                request.epoch()
        );
        if (stored) {
            FAILURES.remove(request.cacheKey());
        }
        ComponentTranslationDebugLogger.flow(
                request.document().route(),
                "provider route={} result=stored={} batchSize={} key={} epoch={}",
                request.document().route().wireName(),
                stored,
                batchSize,
                request.cacheKey(),
                request.epoch()
        );
        if (refresh.isPresent()) {
            // A forced refresh invalidates this in-flight result. The next normal
            // render decides whether a new request is allowed and queues it.
            CACHE.forceRefresh(request.document(), request.targetLanguage());
        }
    }

    private static void failBatch(DispatchRoute route, PendingBatch batch, String message, Throwable error) {
        for (PendingRequest request : batch.requests()) {
            recordRequestFailure(request, message, error);
        }
        finishRequest(route, batch);
    }

    private static void recordRequestFailure(PendingRequest request, String message, Throwable error) {
        CACHE.failJob(request.cacheKey(), request.epoch());
        if (request.epoch() == CACHE.currentSessionEpoch()) {
            String resolvedMessage = message == null || message.isBlank() ? "Component V1 translation failed" : message;
            FAILURES.put(
                    request.cacheKey(),
                    new FailureState(resolvedMessage, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS)
            );
            ComponentTranslationMetrics.record(
                    request.document().route(),
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
        return config.otherTranslations == null
                ? 1
                : Math.max(1, config.otherTranslations.max_concurrent_requests);
    }

    private static int maxBatchSize(DispatchRoute route) {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null) {
            return 1;
        }
        if (route == DispatchRoute.ITEM) {
            return config.itemTranslate == null ? 1 : Math.max(1, config.itemTranslate.max_batch_size);
        }
        return config.otherTranslations == null
                ? 1
                : Math.max(1, config.otherTranslations.max_batch_size);
    }

    private static DispatchRoute dispatchRoute(ComponentTranslationRoute route) {
        return route == ComponentTranslationRoute.ADVANCEMENT
                ? DispatchRoute.OTHER_TRANSLATIONS
                : DispatchRoute.ITEM;
    }

    private static Map<DispatchRoute, DispatchState> createDispatchStates() {
        Map<DispatchRoute, DispatchState> result = new EnumMap<>(DispatchRoute.class);
        for (DispatchRoute route : DispatchRoute.values()) {
            result.put(route, new DispatchState());
        }
        return result;
    }

    public record Resolution<T>(State state, T value, String cacheKey, String errorMessage) {
        public Resolution {
            cacheKey = cacheKey == null ? "" : cacheKey;
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    public enum State {
        V1_HIT,
        LEGACY_HIT,
        PENDING,
        FAILED,
        MISS,
        NO_TEXT,
        INELIGIBLE
    }

    private enum DispatchRoute {
        ITEM,
        OTHER_TRANSLATIONS
    }

    private static final class DispatchState {
        private final Queue<PendingRequest> queue = new ArrayDeque<>();
        private final java.util.Set<PendingBatch> active = new HashSet<>();
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
            ComponentTranslationDocument document,
            String targetLanguage,
            String cacheKey,
            String legacyKey,
            long epoch,
            String requestContext
    ) {
    }

    private record DocumentMemoKey(
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            int componentHash,
            String plainText
    ) {
    }

    private record MemoizedDocument(Component source, ComponentTranslationDocument document) {
    }

    private record FailureState(String message, long expiresAtMillis) {
    }
}
