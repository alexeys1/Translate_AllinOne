package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.registration.LifecycleEventManager;
import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import com.alexeys.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import com.alexeys.translate_allinone.utils.text.TemplateProcessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
public final class WynntilsTaskTrackerTranslationSupport {
    private static final Set<String> refreshedTrackerKeysThisHold = new HashSet<>();
    private static final WynnSharedDictionaryService SHARED_DICTIONARY_SERVICE = WynnSharedDictionaryService.getInstance();
    private static final java.util.Map<String, Long> QUEST_LOCAL_HIT_LOG_TIMESTAMPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long QUEST_LOCAL_HIT_LOG_THROTTLE_MILLIS = 5000L;
    private static final String TRACKER_TRANSLATION_ERROR_KEY = "text.translate_allinone.wynntils.task_tracker.translation_error";

    private WynntilsTaskTrackerTranslationSupport() {
    }

    public static boolean isTrackerTranslationEnabled() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return TranslationFeatureGate.isEnabled() && hasAnyTranslatedSectionEnabled(trackerConfig);
    }

    public static String translateTitle(String originalText) {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        if (!shouldTranslateField(trackerConfig, trackerConfig != null && trackerConfig.translate_title)) {
            return originalText;
        }
        return translateTemplateText(originalText, false);
    }

    public static String translateDescription(String originalText) {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        if (!shouldTranslateField(trackerConfig, trackerConfig != null && trackerConfig.translate_description)) {
            return originalText;
        }
        return translateTemplateText(originalText, true);
    }

    private static String translateTemplateText(String originalText, boolean legacyFormatted) {
        if (originalText == null || originalText.isBlank()) {
            return originalText;
        }

        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        if (!shouldRenderTranslatedText(trackerConfig)) {
            return originalText;
        }

        if (!LifecycleEventManager.isReadyForTranslation) {
            return originalText;
        }

        Component originalTextObject = legacyFormatted
                ? mergeAdjacentStyleRuns(StylePreserver.fromLegacyText(originalText))
                : Component.literal(originalText);
        WynnSharedDictionaryService.LookupResult localLookup = SHARED_DICTIONARY_SERVICE.lookupQuestText(
                originalTextObject.getString()
        );
        if (localLookup.hit()) {
            logQuestLocalHit(originalTextObject.getString(), localLookup.translation(), localLookup);
            return renderQuestLocalTranslation(localLookup.translation(), legacyFormatted, originalTextObject);
        }
        logQuestLocalMiss(originalTextObject.getString(), legacyFormatted);

        if (!hasConfiguredRoute()) {
            return originalText;
        }

        boolean useTagStylePreservation = legacyFormatted;
        StylePreserver.ExtractionResult styleResult = useTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(originalTextObject)
                : StylePreserver.extractAndMark(originalTextObject);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        String translationTemplateKey = useTagStylePreservation
                ? unicodeTemplate
                : StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);
        maybeForceRefreshCurrentTemplate(translationTemplateKey);

        LookupResult lookupResult =
                WynntilsTaskTrackerTextCache.getInstance().lookupOrQueue(translationTemplateKey);

        if (lookupResult.status() == TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassemble(lookupResult.translation(), templateResult.values());
            if (!useTagStylePreservation) {
                return reassembledTranslated;
            }

            Component translatedTextObject = StylePreserver.reapplyStylesFromTags(
                    reassembledTranslated,
                    styleResult.styleMap,
                    true);
            return LegacyComponentTextCodec.encode(translatedTextObject);
        }

        if (lookupResult.status() == TranslationStatus.PENDING
                || lookupResult.status() == TranslationStatus.IN_PROGRESS) {
            Component animatedText = AnimationManager.getAnimatedStyledText(
                    originalTextObject,
                    translationTemplateKey,
                    false);
            return LegacyComponentTextCodec.encode(animatedText);
        }

        if (lookupResult.status() == TranslationStatus.ERROR) {
            String reason = lookupResult.errorMessage();
            if (reason != null && !reason.isBlank()) {
                return originalText + " §c" + Component.translatable(TRACKER_TRANSLATION_ERROR_KEY, TranslationErrorTextSupport.localizeReason(reason)).getString();
            }
            return originalText + " §c" + Component.translatable(TRACKER_TRANSLATION_ERROR_KEY, "").getString();
        }

        return originalText;
    }

    public static boolean shouldShowOriginal(WynnCraftConfig.KeybindingMode mode, boolean isKeyPressed) {
        if (mode == null) {
            return false;
        }
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> !isKeyPressed;
            case HOLD_TO_SEE_ORIGINAL -> isKeyPressed;
            case DISABLED -> false;
        };
    }

    private static WynnCraftConfig.WynntilsTaskTrackerConfig getTrackerConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null) {
            return null;
        }
        return config.wynnCraft.wynntils_task_tracker;
    }

    public static String getTargetLanguage() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null
                || config.wynnCraft.target_language == null
                || config.wynnCraft.target_language.isBlank()) {
            return WynnCraftConfig.DEFAULT_TARGET_LANGUAGE;
        }
        return config.wynnCraft.target_language.trim();
    }

    public static boolean isDebugEnabled() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return trackerConfig != null && trackerConfig.debug != null && trackerConfig.debug.enabled;
    }

    public static boolean shouldUsePlainTitleFont() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return TranslationFeatureGate.isEnabled()
                && shouldTranslateField(trackerConfig, trackerConfig != null && trackerConfig.translate_title)
                && LifecycleEventManager.isReadyForTranslation
                && (hasConfiguredRoute() || SHARED_DICTIONARY_SERVICE.hasQuestDictionaryEntries());
    }

    private static boolean hasConfiguredRoute() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config != null
                && config.providerManager != null
                && ProviderRouteResolver.resolve(config, ProviderRouteResolver.Route.WYNNTILS_TASK_TRACKER) != null;
    }

    private static boolean hasAnyTranslatedSectionEnabled(WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig) {
        return trackerConfig != null
                && trackerConfig.enabled
                && (trackerConfig.translate_title || trackerConfig.translate_description);
    }

    static boolean shouldTranslateField(
            WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig,
            boolean fieldEnabled
    ) {
        return shouldTranslateField(trackerConfig, fieldEnabled, isTranslationHotkeyPressed(trackerConfig));
    }

    static boolean shouldTranslateField(
            WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig,
            boolean fieldEnabled,
            boolean isKeyPressed
    ) {
        return fieldEnabled && shouldRenderTranslatedText(trackerConfig, isKeyPressed);
    }

    static boolean shouldRenderTranslatedText(WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig) {
        return shouldRenderTranslatedText(trackerConfig, isTranslationHotkeyPressed(trackerConfig));
    }

    static boolean shouldRenderTranslatedText(
            WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig,
            boolean isKeyPressed
    ) {
        return hasAnyTranslatedSectionEnabled(trackerConfig)
                && !shouldShowOriginal(resolveKeybindingMode(trackerConfig), isKeyPressed);
    }

    private static WynnCraftConfig.KeybindingMode resolveKeybindingMode(
            WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig
    ) {
        if (trackerConfig == null || trackerConfig.keybinding == null || trackerConfig.keybinding.mode == null) {
            return WynnCraftConfig.KeybindingMode.DISABLED;
        }
        return trackerConfig.keybinding.mode;
    }

    private static boolean isTranslationHotkeyPressed(WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig) {
        return trackerConfig != null
                && trackerConfig.keybinding != null
                && KeybindingManager.isPressed(trackerConfig.keybinding.binding);
    }

    public static void devLog(String message, Object... args) {
        if (!isDebugEnabled()) {
            return;
        }
        Translate_AllinOne.LOGGER.info("[WynntilsTaskTracker] " + message, args);
    }

    private static boolean isQuestLocalHitLoggingEnabled() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return trackerConfig != null
                && trackerConfig.debug != null
                && trackerConfig.debug.log_quests_local_hits;
    }

    private static void maybeForceRefreshCurrentTemplate(String translationTemplateKey) {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        boolean isRefreshPressed = trackerConfig != null
                && trackerConfig.keybinding != null
                && KeybindingManager.isPressed(trackerConfig.keybinding.refreshBinding);

        synchronized (refreshedTrackerKeysThisHold) {
            if (!isRefreshPressed) {
                refreshedTrackerKeysThisHold.clear();
                return;
            }

            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                return;
            }

            if (!refreshedTrackerKeysThisHold.add(translationTemplateKey)) {
                return;
            }
        }

        int refreshedCount = WynntilsTaskTrackerTextCache.getInstance().forceRefresh(List.of(translationTemplateKey));
        if (refreshedCount > 0) {
            Translate_AllinOne.LOGGER.info(
                    "Force-refreshed {} current Wynntils task tracker translation key(s).",
                    refreshedCount);
            devLog("force_refresh key={}", translationTemplateKey);
        }
    }

    private static Component mergeAdjacentStyleRuns(Component text) {
        if (text == null) {
            return Component.empty();
        }

        List<StyleRun> runs = new ArrayList<>();
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            if (!runs.isEmpty() && Objects.equals(runs.getLast().style(), style)) {
                runs.getLast().text().append(string);
            } else {
                runs.add(new StyleRun(style, new StringBuilder(string)));
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent merged = Component.empty();
        for (StyleRun run : runs) {
            merged.append(Component.literal(run.text().toString()).setStyle(run.style()));
        }
        return merged;
    }

    private record StyleRun(Style style, StringBuilder text) {
    }

    private static String renderQuestLocalTranslation(String translation, boolean legacyFormatted, Component originalTextObject) {
        if (!legacyFormatted) {
            return translation;
        }

        if (translation == null || translation.isBlank()) {
            return "";
        }

        if (translation.contains("§")) {
            return translation;
        }

        Style primaryStyle = resolvePrimaryStyle(originalTextObject);
        Component translatedTextObject = Component.literal(translation).setStyle(primaryStyle);
        return LegacyComponentTextCodec.encode(translatedTextObject);
    }

    private static Style resolvePrimaryStyle(Component sourceText) {
        if (sourceText == null) {
            return Style.EMPTY;
        }

        final Style[] resolvedStyle = {Style.EMPTY};
        sourceText.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            resolvedStyle[0] = style == null ? Style.EMPTY : style;
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return resolvedStyle[0] == null ? Style.EMPTY : resolvedStyle[0];
    }

    private static void logQuestLocalHit(
            String originalText,
            String translation,
            WynnSharedDictionaryService.LookupResult lookupResult
    ) {
        if (!isQuestLocalHitLoggingEnabled()) {
            return;
        }

        String normalizedInput = normalizeForLog(originalText);
        String logKey = "quests_local_hit:" + Integer.toHexString(normalizedInput.hashCode());
        long now = System.currentTimeMillis();
        Long lastAt = QUEST_LOCAL_HIT_LOG_TIMESTAMPS.get(logKey);
        if (lastAt != null && now - lastAt < QUEST_LOCAL_HIT_LOG_THROTTLE_MILLIS) {
            return;
        }

        QUEST_LOCAL_HIT_LOG_TIMESTAMPS.put(logKey, now);
        Translate_AllinOne.LOGGER.info(
                "[WynntilsTaskTracker] quests_local_hit dictionary={} match={} input=\"{}\" output=\"{}\"",
                lookupResult.dictionaryId(),
                lookupResult.matchType() == null ? "" : lookupResult.matchType().name().toLowerCase(),
                TooltipTemplateRuntime.truncateForLog(normalizedInput, 220),
                TooltipTemplateRuntime.truncateForLog(normalizeForLog(translation), 220)
        );
    }

    private static void logQuestLocalMiss(String originalText, boolean legacyFormatted) {
        if (!isQuestLocalHitLoggingEnabled()) {
            return;
        }

        String normalizedInput = normalizeForLog(originalText);
        if (normalizedInput.isBlank()) {
            return;
        }

        String logKey = "quests_local_miss:" + Integer.toHexString((normalizedInput + "|" + legacyFormatted).hashCode());
        long now = System.currentTimeMillis();
        Long lastAt = QUEST_LOCAL_HIT_LOG_TIMESTAMPS.get(logKey);
        if (lastAt != null && now - lastAt < QUEST_LOCAL_HIT_LOG_THROTTLE_MILLIS) {
            return;
        }

        QUEST_LOCAL_HIT_LOG_TIMESTAMPS.put(logKey, now);
        Translate_AllinOne.LOGGER.info(
                "[WynntilsTaskTracker] quests_local_miss legacyFormatted={} input=\"{}\" normalized=\"{}\"",
                legacyFormatted,
                TooltipTemplateRuntime.truncateForLog(originalText, 220),
                TooltipTemplateRuntime.truncateForLog(normalizedInput, 220)
        );
    }

    private static String normalizeForLog(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return AnimationManager.stripFormatting(value).replaceAll("\\s+", " ").trim();
    }

}
