package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;

import java.util.concurrent.ConcurrentHashMap;

/** Controls opt-in diagnostics for the structure-preserving translation runtime. */
public final class ComponentTranslationDebugLogger {
    private static final long THROTTLE_WINDOW_MILLIS = 5_000L;
    private static final int FLOW_LOG_LIMIT = 24;
    private static final int TIMING_LOG_LIMIT = 8;
    private static final int ERROR_LOG_LIMIT = 8;
    private static final ConcurrentHashMap<String, ThrottleState> THROTTLES = new ConcurrentHashMap<>();

    private static volatile boolean itemFlowEnabled;
    private static volatile boolean itemTimingEnabled;
    private static volatile boolean scoreboardFlowEnabled;
    private static volatile boolean scoreboardTimingEnabled;

    private ComponentTranslationDebugLogger() {
    }

    public static void refresh(ModConfig config) {
        THROTTLES.clear();
        var itemDebug = config == null || config.itemTranslate == null ? null : config.itemTranslate.debug;
        itemFlowEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_v1_flow;
        itemTimingEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_v1_timing;
        var scoreboardDebug = config == null || config.scoreboardTranslate == null
                ? null
                : config.scoreboardTranslate.debug;
        scoreboardFlowEnabled = scoreboardDebug != null
                && scoreboardDebug.enabled
                && (scoreboardDebug.log_batch_lifecycle
                || scoreboardDebug.log_response_validation
                || scoreboardDebug.log_retry_flow);
        scoreboardTimingEnabled = scoreboardDebug != null
                && scoreboardDebug.enabled
                && scoreboardDebug.log_batch_timing;
    }

    public static void flow(ComponentTranslationRoute route, String message, Object... arguments) {
        if (isFlowEnabled(route) && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component-v1] " + message, arguments);
        }
    }

    public static void flowForNamespace(String namespace, String message, Object... arguments) {
        if ("item".equals(namespace) && itemFlowEnabled && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component-v1] " + message, arguments);
        }
    }

    public static void timing(ComponentTranslationRoute route, String message, Object... arguments) {
        if (isTimingEnabled(route) && permit("timing")) {
            Translate_AllinOne.LOGGER.info("[component-v1-timing] " + message, arguments);
        }
    }

    /** Logs a caller-gated diagnostic while sharing the Component V1 throttle window. */
    public static void throttled(String category, String message, Object... arguments) {
        if (permit(category)) {
            Translate_AllinOne.LOGGER.info(message, arguments);
        }
    }

    /** Logs a validation/provider failure with a lower, independent limit. */
    public static void error(ComponentTranslationRoute route, String message, Object... arguments) {
        if (permit("error")) {
            Translate_AllinOne.LOGGER.warn("[component-v1-error] " + message, arguments);
        }
    }

    private static boolean permit(String category) {
        String resolvedCategory = category == null || category.isBlank() ? "other" : category;
        ThrottleState state = THROTTLES.computeIfAbsent(resolvedCategory, ignored -> new ThrottleState());
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (now - state.windowStartedAt >= THROTTLE_WINDOW_MILLIS) {
                if (state.suppressed > 0) {
                    Translate_AllinOne.LOGGER.info(
                            "[component-v1] throttled category={} suppressed={}",
                            resolvedCategory,
                            state.suppressed
                    );
                }
                state.windowStartedAt = now;
                state.emitted = 0;
                state.suppressed = 0;
            }

            int limit = limitFor(resolvedCategory);
            if (state.emitted < limit) {
                state.emitted++;
                return true;
            }
            state.suppressed++;
            return false;
        }
    }

    private static int limitFor(String category) {
        if ("timing".equals(category)) {
            return TIMING_LOG_LIMIT;
        }
        if ("error".equals(category) || category.endsWith("-error")) {
            return ERROR_LOG_LIMIT;
        }
        return FLOW_LOG_LIMIT;
    }

    private static final class ThrottleState {
        private long windowStartedAt = System.currentTimeMillis();
        private int emitted;
        private int suppressed;
    }

    private static boolean isFlowEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemFlowEnabled;
            case CHAT_OUTPUT -> false;
            case SCOREBOARD -> scoreboardFlowEnabled;
            case ADVANCEMENT -> false;
        };
    }

    private static boolean isTimingEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemTimingEnabled;
            case SCOREBOARD -> scoreboardTimingEnabled;
            case CHAT_OUTPUT, ADVANCEMENT -> false;
        };
    }
}
