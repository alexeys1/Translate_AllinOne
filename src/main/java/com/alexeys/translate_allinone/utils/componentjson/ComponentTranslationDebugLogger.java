package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DebugConfig;
import com.alexeys.translate_allinone.utils.config.pojos.LogLevel;

import java.util.concurrent.ConcurrentHashMap;

/** Controls opt-in diagnostics for the structure-preserving translation runtime. */
public final class ComponentTranslationDebugLogger {
    private static final long THROTTLE_WINDOW_MILLIS = 5_000L;
    private static final int FLOW_LOG_LIMIT = 24;
    private static final int ENTITY_IDENTITY_LOG_LIMIT = 12;
    private static final int TIMING_LOG_LIMIT = 8;
    private static final int ERROR_LOG_LIMIT = 8;
    private static final int MAX_RESPONSE_PREVIEW_CHARS = 1_024;
    private static final ConcurrentHashMap<String, ThrottleState> THROTTLES = new ConcurrentHashMap<>();

    private static volatile LogLevel flowLevel = LogLevel.OFF;
    private static volatile LogLevel timingLevel = LogLevel.OFF;

    private ComponentTranslationDebugLogger() {
    }

    public static void register() {
        ComponentTranslationMetrics.configureLogging(
                ComponentTranslationDebugLogger::flow,
                ComponentTranslationDebugLogger::timing
        );
    }

    public static void refresh(ModConfig config) {
        THROTTLES.clear();
        DebugConfig debug = config == null ? null : config.debug;
        flowLevel = levelOf(debug, LevelDimension.FLOW);
        timingLevel = levelOf(debug, LevelDimension.TIMING);
    }

    public static void flow(ComponentTranslationRoute route, String message, Object... arguments) {
        if (allowsFlow(route) && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component] " + message, arguments);
        }
    }

    public static void flowForNamespace(String namespace, String message, Object... arguments) {
        if (enabled(flowLevel, LogLevel.SUMMARY) && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component] " + message, arguments);
        }
    }

    /**
     * Logs only the hashed cache-identity material for entity-name Component cache misses. Source and translated
     * text are intentionally excluded so the switch is safe to use when investigating key churn.
     */
    public static void entityIdentityMiss(
            ComponentTranslationDocument document,
            String targetLanguage,
            ComponentTranslationCacheIdentity identity,
            String lookupStatus
    ) {
        if (!enabled(flowLevel, LogLevel.DETAIL)
                || document == null
                || document.route() != ComponentTranslationRoute.ENTITY_NAME
                || identity == null
                || !permit("entity-identity")) {
            return;
        }
        ComponentTranslationCacheKey.Metadata metadata = ComponentTranslationCacheKey.metadata(document, targetLanguage);
        Translate_AllinOne.LOGGER.info(
                "[component-entity-identity] lookup={} key={} binding={} keyMatches={} bindingMatches={} "
                        + "structure={} source={} tokens={} units={} semanticSettings={}",
                lookupStatus == null ? "UNKNOWN" : lookupStatus,
                identity.key(),
                identity.binding(),
                identity.key().equals(metadata.key()),
                identity.binding().equals(metadata.binding()),
                metadata.structureFingerprint(),
                metadata.sourceFingerprint(),
                metadata.tokenFingerprint(),
                document.units().size(),
                document.semanticSettings()
        );
    }

    /** Logs a safe, opt-in record when an entity response is reused across Component metadata. */
    public static void entityTemplateReuse(String fullKey, String templateKey) {
        if (enabled(flowLevel, LogLevel.DETAIL) && permit("entity-template")) {
            Translate_AllinOne.LOGGER.info(
                    "[component-entity-template] fullKey={} templateKey={} action=reused",
                    fullKey,
                    templateKey
            );
        }
    }

    public static void timing(ComponentTranslationRoute route, String message, Object... arguments) {
        if (isTimingEnabled(route) && permit("timing")) {
            Translate_AllinOne.LOGGER.info("[component-timing] " + message, arguments);
        }
    }

    public static void throttled(String category, String message, Object... arguments) {
        if (permit(category)) {
            Translate_AllinOne.LOGGER.info(message, arguments);
        }
    }

    /** Logs a validation/provider failure with a lower, independent limit. */
    public static void error(ComponentTranslationRoute route, String message, Object... arguments) {
        if (enabled(flowLevel, LogLevel.SUMMARY) && permit("error")) {
            Translate_AllinOne.LOGGER.warn("[component-error] " + message, arguments);
        }
    }

    static String responsePreview(String response) {
        if (response == null) {
            return "<null>";
        }
        String escaped = escapeForInlineLog(response);
        if (escaped.length() <= MAX_RESPONSE_PREVIEW_CHARS) {
            return escaped;
        }
        return escaped.substring(0, MAX_RESPONSE_PREVIEW_CHARS)
                + "...(+" + (escaped.length() - MAX_RESPONSE_PREVIEW_CHARS) + " chars)";
    }

    private static boolean permit(String category) {
        String resolvedCategory = category == null || category.isBlank() ? "other" : category;
        ThrottleState state = THROTTLES.computeIfAbsent(resolvedCategory, ignored -> new ThrottleState());
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (now - state.windowStartedAt >= THROTTLE_WINDOW_MILLIS) {
                if (state.suppressed > 0) {
                    Translate_AllinOne.LOGGER.info(
                            "[component] throttled category={} suppressed={}",
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
        if ("entity-identity".equals(category)) {
            return ENTITY_IDENTITY_LOG_LIMIT;
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

    static boolean isEntityIdentityEnabled() {
        return enabled(flowLevel, LogLevel.DETAIL);
    }

    static boolean isTimingEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH, SCOREBOARD -> enabled(timingLevel, LogLevel.SUMMARY);
            case CHAT_OUTPUT, ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI -> false;
        };
    }

    private static boolean allowsFlow(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case CHAT_OUTPUT -> false;
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH, SCOREBOARD, ADVANCEMENT, SIGN_FACE,
                 SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI -> enabled(flowLevel, LogLevel.SUMMARY);
        };
    }

    private static LogLevel levelOf(DebugConfig debug, LevelDimension dimension) {
        if (debug == null) {
            return LogLevel.OFF;
        }
        return switch (dimension) {
            case FLOW -> debug.flow;
            case TIMING -> debug.timing;
        };
    }

    private static boolean enabled(LogLevel level, LogLevel minimum) {
        return level != null && level.ordinal() >= minimum.ordinal();
    }

    private enum LevelDimension {
        FLOW,
        TIMING
    }

    private static String escapeForInlineLog(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }
}
