package com.cedarxuesong.translate_allinone.utils.componentjson;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ComponentTranslationDebugLogger {
    private static final long THROTTLE_WINDOW_MILLIS = 5_000L;
    private static final int FLOW_LOG_LIMIT = 24;
    private static final int TEXT_CONTENT_LOG_LIMIT = 12;
    private static final int ENTITY_IDENTITY_LOG_LIMIT = 12;
    private static final int TIMING_LOG_LIMIT = 8;
    private static final int ERROR_LOG_LIMIT = 8;
    private static final int MAX_TEXT_CONTENT_UNITS = 8;
    private static final int MAX_TEXT_CONTENT_CHARS_PER_UNIT = 512;
    private static final int MAX_RESPONSE_PREVIEW_CHARS = 1_024;
    private static final ConcurrentHashMap<String, ThrottleState> THROTTLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> TEXT_CONTENT_LOG_TIMES = new ConcurrentHashMap<>();

    private static volatile boolean itemFlowEnabled;
    private static volatile boolean itemTextContentEnabled;
    private static volatile boolean itemTimingEnabled;
    private static volatile boolean scoreboardFlowEnabled;
    private static volatile boolean scoreboardTimingEnabled;
    private static volatile boolean entityIdentityEnabled;

    private ComponentTranslationDebugLogger() {
    }

    public static void refresh(ModConfig config) {
        THROTTLES.clear();
        TEXT_CONTENT_LOG_TIMES.clear();
        var itemDebug = config == null || config.itemTranslate == null ? null : config.itemTranslate.debug;
        itemFlowEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_flow;
        itemTextContentEnabled = itemFlowEnabled && itemDebug.log_component_text_content;
        itemTimingEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_timing;
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
        var otherDebug = config == null || config.otherTranslations == null ? null : config.otherTranslations.debug;
        entityIdentityEnabled = otherDebug != null
                && otherDebug.enabled
                && otherDebug.log_component_entity_identity;
    }

    public static void flow(ComponentTranslationRoute route, String message, Object... arguments) {
        if (isFlowEnabled(route) && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component] " + message, arguments);
        }
    }

    public static void flowForNamespace(String namespace, String message, Object... arguments) {
        if ("item".equals(namespace) && itemFlowEnabled && permit("flow")) {
            Translate_AllinOne.LOGGER.info("[component] " + message, arguments);
        }
    }






    public static void textContent(ComponentTranslationDocument document, String cacheKey) {
        if (document == null || !isTextContentEnabled(document.route())) {
            return;
        }
        String deduplicationKey = cacheKey == null || cacheKey.isBlank()
                ? document.route().wireName() + ':' + Integer.toHexString(document.hashCode())
                : cacheKey;
        long now = System.currentTimeMillis();
        Long previous = TEXT_CONTENT_LOG_TIMES.putIfAbsent(deduplicationKey, now);
        if (previous != null && now - previous < THROTTLE_WINDOW_MILLIS) {
            return;
        }
        TEXT_CONTENT_LOG_TIMES.put(deduplicationKey, now);
        if (!permit("text-content")) {
            return;
        }
        Translate_AllinOne.LOGGER.info(
                "[component-text] route={} key={} units={} content={}",
                document.route().wireName(),
                deduplicationKey,
                document.units().size(),
                formatTextUnits(document.units())
        );
    }

    public static void entityIdentityMiss(
            ComponentTranslationDocument document,
            String targetLanguage,
            ComponentTranslationCacheIdentity identity,
            String lookupStatus
    ) {
        if (!entityIdentityEnabled
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

    public static void entityTemplateReuse(String fullKey, String templateKey) {
        if (entityIdentityEnabled && permit("entity-template")) {
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

    public static void error(ComponentTranslationRoute route, String message, Object... arguments) {
        if (permit("error")) {
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
        if ("text-content".equals(category)) {
            return TEXT_CONTENT_LOG_LIMIT;
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

    static boolean isFlowEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemFlowEnabled;
            case CHAT_OUTPUT -> false;
            case SCOREBOARD -> scoreboardFlowEnabled;
            case ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE -> false;
        };
    }

    static boolean isTextContentEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemTextContentEnabled;
            case CHAT_OUTPUT, SCOREBOARD, ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE -> false;
        };
    }

    static boolean isEntityIdentityEnabled() {
        return entityIdentityEnabled;
    }

    static boolean isTimingEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemTimingEnabled;
            case SCOREBOARD -> scoreboardTimingEnabled;
            case CHAT_OUTPUT, ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE -> false;
        };
    }

    static String formatTextUnits(List<ComponentTextUnit> units) {
        if (units == null || units.isEmpty()) {
            return "[]";
        }
        StringBuilder result = new StringBuilder("[");
        int limit = Math.min(units.size(), MAX_TEXT_CONTENT_UNITS);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                result.append(", ");
            }
            ComponentTextUnit unit = units.get(index);
            result.append("{id=\"")
                    .append(escapeForInlineLog(unit.id()))
                    .append("\", text=\"")
                    .append(previewText(unit.sourceText()))
                    .append("\"}");
        }
        if (units.size() > limit) {
            result.append(", ...(+").append(units.size() - limit).append(" units)");
        }
        return result.append(']').toString();
    }

    private static String previewText(String text) {
        String escaped = escapeForInlineLog(text);
        if (escaped.length() <= MAX_TEXT_CONTENT_CHARS_PER_UNIT) {
            return escaped;
        }
        return escaped.substring(0, MAX_TEXT_CONTENT_CHARS_PER_UNIT)
                + "...(+" + (escaped.length() - MAX_TEXT_CONTENT_CHARS_PER_UNIT) + " chars)";
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
