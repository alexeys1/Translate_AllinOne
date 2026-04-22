package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.cedarxuesong.translate_allinone.utils.config.ProviderRouteResolver;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WynntilsTaskTrackerTranslationSupport {
    private static final Set<String> refreshedTrackerKeysThisHold = new HashSet<>();

    private WynntilsTaskTrackerTranslationSupport() {
    }

    public static boolean isTrackerTranslationEnabled() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return hasAnyTranslatedSectionEnabled(trackerConfig);
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

        if (!hasConfiguredRoute()) {
            return originalText;
        }

        Text originalTextObject = legacyFormatted
                ? mergeAdjacentStyleRuns(StylePreserver.fromLegacyText(originalText))
                : Text.literal(originalText);
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

        WynntilsTaskTrackerTextCache.LookupResult lookupResult =
                WynntilsTaskTrackerTextCache.getInstance().lookupOrQueue(translationTemplateKey);

        if (lookupResult.status() == WynntilsTaskTrackerTextCache.TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassemble(lookupResult.translation(), templateResult.values());
            if (!useTagStylePreservation) {
                return reassembledTranslated;
            }

            Text translatedTextObject = StylePreserver.reapplyStylesFromTags(
                    reassembledTranslated,
                    styleResult.styleMap,
                    true);
            return toLegacyStringPreservingResets(translatedTextObject);
        }

        if (lookupResult.status() == WynntilsTaskTrackerTextCache.TranslationStatus.PENDING
                || lookupResult.status() == WynntilsTaskTrackerTextCache.TranslationStatus.IN_PROGRESS) {
            Text animatedText = AnimationManager.getAnimatedStyledText(
                    originalTextObject,
                    translationTemplateKey,
                    false);
            return toLegacyStringPreservingResets(animatedText);
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
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        if (trackerConfig == null || trackerConfig.target_language == null || trackerConfig.target_language.isBlank()) {
            return "Chinese";
        }
        return trackerConfig.target_language.trim();
    }

    public static boolean isDebugEnabled() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return trackerConfig != null && trackerConfig.debug != null && trackerConfig.debug.enabled;
    }

    public static boolean shouldUsePlainTitleFont() {
        WynnCraftConfig.WynntilsTaskTrackerConfig trackerConfig = getTrackerConfig();
        return shouldTranslateField(trackerConfig, trackerConfig != null && trackerConfig.translate_title)
                && LifecycleEventManager.isReadyForTranslation
                && hasConfiguredRoute();
    }

    private static boolean hasConfiguredRoute() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config != null
                && config.providerManager != null
                && config.providerManager.routes != null
                && config.providerManager.routes.wynntils_task_tracker != null
                && !config.providerManager.routes.wynntils_task_tracker.isBlank()
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

    private static Text mergeAdjacentStyleRuns(Text text) {
        if (text == null) {
            return Text.empty();
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

        MutableText merged = Text.empty();
        for (StyleRun run : runs) {
            merged.append(Text.literal(run.text().toString()).setStyle(run.style()));
        }
        return merged;
    }

    private record StyleRun(Style style, StringBuilder text) {
    }

    private static String toLegacyStringPreservingResets(Text text) {
        if (text == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        final Style[] previousStyle = {null};
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            Style currentStyle = style == null ? Style.EMPTY : style;
            if (previousStyle[0] == null) {
                appendLegacyStyleCodes(builder, currentStyle, false);
            } else if (!Objects.equals(previousStyle[0], currentStyle)) {
                appendLegacyStyleCodes(builder, currentStyle, previousStyle[0] != null && !previousStyle[0].isEmpty());
            }

            builder.append(string);
            previousStyle[0] = currentStyle;
            return Optional.empty();
        }, Style.EMPTY);
        return builder.toString();
    }

    private static void appendLegacyStyleCodes(StringBuilder builder, Style style, boolean resetBeforeStyle) {
        if (style == null || style.isEmpty()) {
            if (resetBeforeStyle) {
                builder.append("§r");
            }
            return;
        }

        if (resetBeforeStyle) {
            builder.append("§r");
        }

        if (style.getColor() != null) {
            boolean appendedFormattingColor = false;
            for (Formatting formatting : Formatting.values()) {
                if (formatting.isColor()
                        && formatting.getColorValue() != null
                        && formatting.getColorValue().equals(style.getColor().getRgb())) {
                    builder.append('§').append(formatting.getCode());
                    appendedFormattingColor = true;
                    break;
                }
            }
            if (!appendedFormattingColor) {
                builder.append("§").append(toWynntilsHexColor(style.getColor()));
            }
        }
        if (style.isBold()) builder.append("§l");
        if (style.isItalic()) builder.append("§o");
        if (style.isUnderlined()) builder.append("§n");
        if (style.isStrikethrough()) builder.append("§m");
        if (style.isObfuscated()) builder.append("§k");
    }

    private static String toWynntilsHexColor(TextColor textColor) {
        if (textColor == null) {
            return "";
        }

        int rgb = textColor.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return String.format("#%02x%02x%02xff", r, g, b);
    }
}
