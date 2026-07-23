package com.cedarxuesong.translate_allinone.utils.componentjson;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class ComponentTranslationMetrics {
    private static final Map<ComponentTranslationRoute, Map<Outcome, LongAdder>> COUNTERS = createCounters();
    private static final Map<ComponentTranslationRoute, Map<Timing, LongAdder>> TIMING_NANOS = createTimings();

    private ComponentTranslationMetrics() {
    }

    public static void record(ComponentTranslationRoute route, Outcome outcome) {
        if (route != null && outcome != null) {
            COUNTERS.get(route).get(outcome).increment();
            ComponentTranslationDebugLogger.flow(
                    route,
                    "outcome route={} outcome={}",
                    route.wireName(),
                    outcome.name()
            );
        }
    }

    public static Map<Outcome, Long> snapshot(ComponentTranslationRoute route) {
        Map<Outcome, Long> result = new EnumMap<>(Outcome.class);
        if (route == null) {
            return result;
        }
        COUNTERS.get(route).forEach((outcome, count) -> result.put(outcome, count.sum()));
        return Map.copyOf(result);
    }

    public static void recordNanos(ComponentTranslationRoute route, Timing timing, long nanos) {
        if (route != null && timing != null && nanos > 0L) {
            TIMING_NANOS.get(route).get(timing).add(nanos);
            ComponentTranslationDebugLogger.timing(
                    route,
                    "route={} phase={} nanos={}",
                    route.wireName(),
                    timing.name(),
                    nanos
            );
        }
    }

    public static Map<Timing, Long> timingSnapshot(ComponentTranslationRoute route) {
        Map<Timing, Long> result = new EnumMap<>(Timing.class);
        if (route == null) {
            return result;
        }
        TIMING_NANOS.get(route).forEach((timing, value) -> result.put(timing, value.sum()));
        return Map.copyOf(result);
    }

    private static Map<ComponentTranslationRoute, Map<Outcome, LongAdder>> createCounters() {
        Map<ComponentTranslationRoute, Map<Outcome, LongAdder>> routes = new EnumMap<>(ComponentTranslationRoute.class);
        for (ComponentTranslationRoute route : ComponentTranslationRoute.values()) {
            Map<Outcome, LongAdder> outcomes = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                outcomes.put(outcome, new LongAdder());
            }
            routes.put(route, outcomes);
        }
        return routes;
    }

    private static Map<ComponentTranslationRoute, Map<Timing, LongAdder>> createTimings() {
        Map<ComponentTranslationRoute, Map<Timing, LongAdder>> routes = new EnumMap<>(ComponentTranslationRoute.class);
        for (ComponentTranslationRoute route : ComponentTranslationRoute.values()) {
            Map<Timing, LongAdder> timings = new EnumMap<>(Timing.class);
            for (Timing timing : Timing.values()) {
                timings.put(timing, new LongAdder());
            }
            routes.put(route, timings);
        }
        return routes;
    }

    public enum Outcome {
        SUCCESS,
        NO_TEXT,
        LOCAL_FALLBACK,
        PROVIDER_FAILURE,
        RESPONSE_REJECTED,
        CACHE_HIT,
        CACHE_MISS,
        LEGACY_HIT,
        STALE_SESSION
    }

    public enum Timing {
        DOCUMENT_BUILD,
        CACHE_KEY,
        CACHE_LOOKUP,
        APPLY
    }
}
