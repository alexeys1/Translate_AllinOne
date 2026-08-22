package com.alexeys.translate_allinone.utils.componentjson;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class ComponentTranslationMetrics {
    private static final Map<ComponentTranslationRoute, RouteMetrics> ROUTES = createRoutes();
    private static final ConcurrentMap<RoutePolicyKey, RouteMetrics> POLICIES = new ConcurrentHashMap<>();

    private ComponentTranslationMetrics() {
    }

    public static void record(ComponentTranslationRoute route, Outcome outcome) {
        if (route == null || outcome == null) {
            return;
        }
        ROUTES.get(route).outcomes().get(outcome).increment();
        logOutcome(route, outcome);
    }

    public static void record(ComponentTranslationDocument document, Outcome outcome) {
        if (document == null || outcome == null) {
            return;
        }
        record(document.route(), outcome);
        policyMetrics(document).outcomes().get(outcome).increment();
    }

    public static void record(ComponentTranslationRoute route, int policyVersion, Outcome outcome) {
        if (route == null || policyVersion < 1 || outcome == null) {
            return;
        }
        record(route, outcome);
        policyMetrics(route, policyVersion).outcomes().get(outcome).increment();
    }

    public static Map<Outcome, Long> snapshot(ComponentTranslationRoute route) {
        if (route == null) {
            return Map.of();
        }
        return outcomeSnapshot(ROUTES.get(route));
    }

    public static Map<Outcome, Long> snapshot(ComponentTranslationRoute route, int policyVersion) {
        if (route == null || policyVersion < 1) {
            return Map.of();
        }
        RouteMetrics metrics = POLICIES.get(new RoutePolicyKey(route, policyVersion));
        return metrics == null ? emptyOutcomeSnapshot() : outcomeSnapshot(metrics);
    }

    public static void recordNanos(ComponentTranslationRoute route, Timing timing, long nanos) {
        if (route == null || timing == null || nanos < 0L) {
            return;
        }
        ROUTES.get(route).timings().get(timing).record(nanos);
        logTiming(route, timing, nanos);
    }

    public static void recordNanos(ComponentTranslationDocument document, Timing timing, long nanos) {
        if (document == null || timing == null || nanos < 0L) {
            return;
        }
        recordNanos(document.route(), timing, nanos);
        policyMetrics(document).timings().get(timing).record(nanos);
    }

    public static void recordNanos(
            ComponentTranslationRoute route,
            int policyVersion,
            Timing timing,
            long nanos
    ) {
        if (route == null || policyVersion < 1 || timing == null || nanos < 0L) {
            return;
        }
        recordNanos(route, timing, nanos);
        policyMetrics(route, policyVersion).timings().get(timing).record(nanos);
    }

    public static Map<Timing, Long> timingSnapshot(ComponentTranslationRoute route) {
        Map<Timing, Long> result = new EnumMap<>(Timing.class);
        if (route == null) {
            return result;
        }
        ROUTES.get(route).timings().forEach((timing, value) -> result.put(timing, value.totalNanos()));
        return Map.copyOf(result);
    }

    public static Map<Timing, TimingStats> timingStatsSnapshot(
            ComponentTranslationRoute route,
            int policyVersion
    ) {
        if (route == null || policyVersion < 1) {
            return Map.of();
        }
        RouteMetrics metrics = POLICIES.get(new RoutePolicyKey(route, policyVersion));
        if (metrics == null) {
            return emptyTimingStatsSnapshot();
        }
        Map<Timing, TimingStats> result = new EnumMap<>(Timing.class);
        metrics.timings().forEach((timing, value) -> result.put(timing, value.snapshot()));
        return Map.copyOf(result);
    }

    public static void recordValue(ComponentTranslationRoute route, Measurement measurement, long value) {
        if (route == null || measurement == null || value < 0L) {
            return;
        }
        ROUTES.get(route).measurements().get(measurement).add(value);
    }

    public static void recordValue(
            ComponentTranslationDocument document,
            Measurement measurement,
            long value
    ) {
        if (document == null || measurement == null || value < 0L) {
            return;
        }
        recordValue(document.route(), measurement, value);
        policyMetrics(document).measurements().get(measurement).add(value);
    }

    public static void recordValue(
            ComponentTranslationRoute route,
            int policyVersion,
            Measurement measurement,
            long value
    ) {
        if (route == null || policyVersion < 1 || measurement == null || value < 0L) {
            return;
        }
        recordValue(route, measurement, value);
        policyMetrics(route, policyVersion).measurements().get(measurement).add(value);
    }

    public static Map<Measurement, Long> measurementSnapshot(
            ComponentTranslationRoute route,
            int policyVersion
    ) {
        if (route == null || policyVersion < 1) {
            return Map.of();
        }
        RouteMetrics metrics = POLICIES.get(new RoutePolicyKey(route, policyVersion));
        if (metrics == null) {
            return emptyMeasurementSnapshot();
        }
        Map<Measurement, Long> result = new EnumMap<>(Measurement.class);
        metrics.measurements().forEach((measurement, value) -> result.put(measurement, value.sum()));
        return Map.copyOf(result);
    }

    public static RoutePolicySnapshot rolloutSnapshot(
            ComponentTranslationRoute route,
            int policyVersion
    ) {
        return new RoutePolicySnapshot(
                route,
                policyVersion,
                snapshot(route, policyVersion),
                timingStatsSnapshot(route, policyVersion),
                measurementSnapshot(route, policyVersion)
        );
    }

    static void resetForTests() {
        ROUTES.values().forEach(RouteMetrics::reset);
        POLICIES.clear();
    }

    private static RouteMetrics policyMetrics(ComponentTranslationDocument document) {
        return policyMetrics(document.route(), document.policyVersion());
    }

    private static RouteMetrics policyMetrics(ComponentTranslationRoute route, int policyVersion) {
        return POLICIES.computeIfAbsent(new RoutePolicyKey(route, policyVersion), ignored -> RouteMetrics.create());
    }

    private static void logOutcome(ComponentTranslationRoute route, Outcome outcome) {
        ComponentTranslationDebugLogger.flow(
                route,
                "metric route={} outcome={}",
                route.wireName(),
                outcome.name()
        );
    }

    private static void logTiming(ComponentTranslationRoute route, Timing timing, long nanos) {
        ComponentTranslationDebugLogger.timing(
                route,
                "route={} phase={} nanos={}",
                route.wireName(),
                timing.name(),
                nanos
        );
    }

    private static Map<ComponentTranslationRoute, RouteMetrics> createRoutes() {
        Map<ComponentTranslationRoute, RouteMetrics> routes = new EnumMap<>(ComponentTranslationRoute.class);
        for (ComponentTranslationRoute route : ComponentTranslationRoute.values()) {
            routes.put(route, RouteMetrics.create());
        }
        return routes;
    }

    private static Map<Outcome, Long> outcomeSnapshot(RouteMetrics metrics) {
        Map<Outcome, Long> result = new EnumMap<>(Outcome.class);
        metrics.outcomes().forEach((outcome, count) -> result.put(outcome, count.sum()));
        return Map.copyOf(result);
    }

    private static Map<Outcome, Long> emptyOutcomeSnapshot() {
        Map<Outcome, Long> result = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            result.put(outcome, 0L);
        }
        return Map.copyOf(result);
    }

    private static Map<Timing, TimingStats> emptyTimingStatsSnapshot() {
        Map<Timing, TimingStats> result = new EnumMap<>(Timing.class);
        for (Timing timing : Timing.values()) {
            result.put(timing, new TimingStats(0L, 0L, 0L));
        }
        return Map.copyOf(result);
    }

    private static Map<Measurement, Long> emptyMeasurementSnapshot() {
        Map<Measurement, Long> result = new EnumMap<>(Measurement.class);
        for (Measurement measurement : Measurement.values()) {
            result.put(measurement, 0L);
        }
        return Map.copyOf(result);
    }

    public enum Outcome {
        SUCCESS,
        NO_TEXT,
        LOCAL_FALLBACK,
        PROVIDER_FAILURE,
        RESPONSE_REJECTED,
        CACHE_HIT,
        TEMPLATE_HIT,
        CACHE_MISS,
        LEGACY_HIT,
        LOCAL_DICTIONARY_HIT,
        STALE_SESSION,
        DOCUMENT_BUILT,
        DOCUMENT_FAILED,
        CODEC_ENCODE_FAILURE,
        CODEC_DECODE_FAILURE,
        PROVIDER_STRUCTURED_OUTPUT,
        PROVIDER_FALLBACK_OUTPUT,
        RESPONSE_PARSE_FAILURE,
        VALIDATION_ID_FAILURE,
        VALIDATION_TOKEN_FAILURE,
        VALIDATION_LENGTH_FAILURE,
        VALIDATION_STRUCTURE_FAILURE,
        JOB_QUEUED,
        JOB_IN_FLIGHT,
        JOB_SUCCESS,
        JOB_FAILURE,
        JOB_EXPIRED,
        FALLBACK_LEGACY,
        FALLBACK_ORIGINAL
    }

    public enum Timing {
        DOCUMENT_BUILD,
        CACHE_KEY,
        CACHE_LOOKUP,
        HASH,
        PROVIDER,
        APPLY,
        SCOREBOARD_FRAME_PREPARE
    }

    public enum Measurement {
        REQUEST_BYTES,
        RESPONSE_BYTES,
        TEXT_UNITS,
        SCOREBOARD_FRAME_ENTRIES
    }

    public record TimingStats(long totalNanos, long samples, long maxNanos) {
    }

    public record RoutePolicySnapshot(
            ComponentTranslationRoute route,
            int policyVersion,
            Map<Outcome, Long> outcomes,
            Map<Timing, TimingStats> timings,
            Map<Measurement, Long> measurements
    ) {
    }

    private record RoutePolicyKey(ComponentTranslationRoute route, int policyVersion) {
    }

    private record RouteMetrics(
            Map<Outcome, LongAdder> outcomes,
            Map<Timing, TimingAccumulator> timings,
            Map<Measurement, LongAdder> measurements
    ) {
        private static RouteMetrics create() {
            Map<Outcome, LongAdder> outcomes = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                outcomes.put(outcome, new LongAdder());
            }
            Map<Timing, TimingAccumulator> timings = new EnumMap<>(Timing.class);
            for (Timing timing : Timing.values()) {
                timings.put(timing, new TimingAccumulator());
            }
            Map<Measurement, LongAdder> measurements = new EnumMap<>(Measurement.class);
            for (Measurement measurement : Measurement.values()) {
                measurements.put(measurement, new LongAdder());
            }
            return new RouteMetrics(outcomes, timings, measurements);
        }

        private void reset() {
            outcomes.values().forEach(LongAdder::reset);
            timings.values().forEach(TimingAccumulator::reset);
            measurements.values().forEach(LongAdder::reset);
        }
    }

    private static final class TimingAccumulator {
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder samples = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private void record(long nanos) {
            totalNanos.add(nanos);
            samples.increment();
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        private long totalNanos() {
            return totalNanos.sum();
        }

        private TimingStats snapshot() {
            return new TimingStats(totalNanos.sum(), samples.sum(), maxNanos.get());
        }

        private void reset() {
            totalNanos.reset();
            samples.reset();
            maxNanos.set(0L);
        }
    }
}
