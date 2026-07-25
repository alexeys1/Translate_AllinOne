package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationCache;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ComponentTranslationRuntime {
    private static final int DOCUMENT_CACHE_LIMIT = 512;
    private static final long FAILURE_COOLDOWN_MILLIS = 30_000L;
    private static final ComponentTranslationCache CACHE = ComponentTranslationCache.getInstance();
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
        boolean queued = CACHE.queueJob(document, targetLanguage, legacyKey, epoch);
        ComponentTranslationDebugLogger.flow(
                document.route(),
                "queue route={} accepted={} key={} epoch={}",
                document.route().wireName(),
                queued,
                cacheKey,
                epoch
        );
        return queued;
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
