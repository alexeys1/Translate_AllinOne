package com.alexeys.translate_allinone.utils.componentjson;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;

/** Controls opt-in diagnostics for the structure-preserving translation runtime. */
public final class ComponentTranslationDebugLogger {
    private static final long THROTTLE_WINDOW_MILLIS = 5_000L;
    private static final int FLOW_LOG_LIMIT = 24;
    private static final int ENTITY_IDENTITY_LOG_LIMIT = 12;
    private static final int TIMING_LOG_LIMIT = 8;
    private static final int ERROR_LOG_LIMIT = 8;
    private static final int MAX_LOG_TEXT_CHUNK_CHARS = 256;
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

    public static void register() {
        ComponentTranslationMetrics.configureLogging(
                ComponentTranslationDebugLogger::flow,
                ComponentTranslationDebugLogger::timing
        );
    }

    public static void refresh(ModConfig config) {
        THROTTLES.clear();
        TEXT_CONTENT_LOG_TIMES.clear();
        var itemDebug = config == null || config.itemTranslate == null ? null : config.itemTranslate.debug;
        itemFlowEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_flow;
        itemTextContentEnabled = itemDebug != null && itemDebug.enabled && itemDebug.log_component_text_content;
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
        if (!registerTextContentKey(deduplicationKey)) {
            return;
        }
        List<ComponentTextUnit> units = document.units();
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            ComponentTextUnit unit = units.get(unitIndex);
            String unitId = unit == null ? "" : unit.id();
            String sourceText = unit == null ? "" : unit.sourceText();
            List<String> chunks = splitTextForLog(sourceText);
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Translate_AllinOne.LOGGER.info(
                        "[component-text] route={} key={} unit={}/{} id=\"{}\" chunk={}/{} text=\"{}\"",
                        document.route().wireName(),
                        deduplicationKey,
                        unitIndex + 1,
                        units.size(),
                        escapeForInlineLog(unitId),
                        chunkIndex + 1,
                        chunks.size(),
                        escapeForInlineLog(chunks.get(chunkIndex))
                );
            }
        }
    }

    public static void tooltipText(
            ComponentTranslationRoute route,
            String context,
            String marker,
            Component label
    ) {
        tooltipText(route, context, marker, label == null ? List.of() : List.of(label));
    }

    public static void tooltipText(
            ComponentTranslationRoute route,
            String context,
            String marker,
            List<Component> labels
    ) {
        if (!isTextContentEnabled(route)) {
            return;
        }
        String resolvedContext = context == null ? "" : context;
        String resolvedMarker = marker == null || marker.isBlank() ? "UNKNOWN" : marker;
        List<Component> resolvedLabels = labels == null ? List.of() : labels;
        String deduplicationKey = route.wireName()
                + ':' + resolvedContext
                + ':' + resolvedMarker
                + ':' + Integer.toHexString(tooltipTextIdentity(resolvedLabels).hashCode());
        if (!registerTextContentKey(deduplicationKey)) {
            return;
        }
        if (resolvedLabels.isEmpty()) {
            Translate_AllinOne.LOGGER.info(
                    "[component-text] route={} context={} marker={} labels=0",
                    route.wireName(),
                    resolvedContext,
                    resolvedMarker
            );
            return;
        }
        for (int labelIndex = 0; labelIndex < resolvedLabels.size(); labelIndex++) {
            Component label = resolvedLabels.get(labelIndex);
            List<String> chunks = splitTextForLog(label == null ? "" : label.getString());
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Translate_AllinOne.LOGGER.info(
                        "[component-text] route={} context={} marker={} label={}/{} chunk={}/{} text=\"{}\"",
                        route.wireName(),
                        resolvedContext,
                        resolvedMarker,
                        labelIndex + 1,
                        resolvedLabels.size(),
                        chunkIndex + 1,
                        chunks.size(),
                        escapeForInlineLog(chunks.get(chunkIndex))
                );
            }
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

    /** Logs a safe, opt-in record when an entity response is reused across Component metadata. */
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

    /** Logs a validation/provider failure with a lower, independent limit. */
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
            case ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI -> false;
        };
    }

    static boolean isTextContentEnabled(ComponentTranslationRoute route) {
        if (route == null) {
            return false;
        }
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> itemTextContentEnabled;
            case CHAT_OUTPUT, SCOREBOARD, ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI -> false;
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
            case CHAT_OUTPUT, ADVANCEMENT, SIGN_FACE, SIGN_CONTINUOUS, ENTITY_NAME, TEXT_DISPLAY, BOOK_PAGE, SCREEN_UI -> false;
        };
    }

    private static boolean registerTextContentKey(String deduplicationKey) {
        long now = System.currentTimeMillis();
        Long previous = TEXT_CONTENT_LOG_TIMES.putIfAbsent(deduplicationKey, now);
        if (previous != null && now - previous < THROTTLE_WINDOW_MILLIS) {
            return false;
        }
        TEXT_CONTENT_LOG_TIMES.put(deduplicationKey, now);
        return true;
    }

    static List<String> splitTextForLog(String text) {
        String source = text == null ? "" : text;
        if (source.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>((source.length() + MAX_LOG_TEXT_CHUNK_CHARS - 1) / MAX_LOG_TEXT_CHUNK_CHARS);
        for (int start = 0; start < source.length(); ) {
            int end = Math.min(start + MAX_LOG_TEXT_CHUNK_CHARS, source.length());
            if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) {
                end--;
            }
            chunks.add(source.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private static String tooltipTextIdentity(List<Component> labels) {
        StringBuilder identity = new StringBuilder();
        for (Component label : labels) {
            identity.append(label == null ? "" : label.getString()).append('\u0000');
        }
        return identity.toString();
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
