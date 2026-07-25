package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheStats;
import com.cedarxuesong.translate_allinone.utils.cache.OtherTranslationsTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;

public final class VanillaAdvancementTranslationSupport {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String ADVANCEMENT_STATUS_ANIMATION_KEY = "advancement-tooltip-status";
    private static final AtomicBoolean UNEXPECTED_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final long REFRESH_HOLD_RELEASE_GRACE_MILLIS = 250L;
    private static final Set<String> refreshedKeysThisHold = new HashSet<>();
    private static volatile boolean refreshHoldActive = false;
    private static volatile long refreshHoldGraceExpiresAtMillis = 0L;
    private static volatile int refreshNoticeAdvancementSignature = 0;
    private static volatile long refreshNoticeExpiresAtMillis = 0L;

    private VanillaAdvancementTranslationSupport() {
    }

    public static boolean isVanillaAdvancement(AdvancementHolder holder) {
        return holder != null
                && holder.id() != null
                && VANILLA_NAMESPACE.equals(holder.id().getNamespace());
    }

    public static Component translateTitle(AdvancementHolder holder, Component originalTitle) {
        return translateComponent(holder, originalTitle, "title", false);
    }

    public static Component translateQueuedTitle(AdvancementHolder holder, Component originalTitle) {
        return translateComponent(holder, originalTitle, "title", true);
    }

    public static Component translateHoveredTitle(AdvancementHolder holder, Component originalTitle) {
        return translateQueuedTitle(holder, originalTitle);
    }

    public record HoveredAdvancementText(
            Component title,
            Component description,
            Component statusLine,
            Component errorStatusLine,
            boolean showRefreshNotice
    ) {
    }

    private record HoveredAdvancementStatus(Component statusLine, Component errorStatusLine) {
    }

    public static HoveredAdvancementText translateHoveredText(AdvancementHolder holder, DisplayInfo display) {
        Component originalTitle = display == null ? null : display.getTitle();
        Component styledDescription = styleDescription(display, display == null ? null : display.getDescription());
        List<Component> hoveredTexts = new ArrayList<>(2);
        hoveredTexts.add(originalTitle);
        hoveredTexts.add(styledDescription);
        Set<String> translationTemplateKeys = collectTranslationTemplateKeys(holder, hoveredTexts);
        OtherTranslationsConfig config = currentOtherTranslationsConfig();
        boolean componentV1Enabled = config != null && config.component_json_v1_advancements;
        if (!componentV1Enabled) {
            maybeForceRefreshAdvancementKeys(config, translationTemplateKeys);
        }
        Component translatedTitle = translateComponent(holder, originalTitle, "title", true, componentV1Enabled);
        Component translatedDescription = translateComponent(
                holder,
                styledDescription,
                "description",
                true,
                componentV1Enabled
        );
        boolean showRefreshNotice = !componentV1Enabled && shouldShowRefreshNotice(translationTemplateKeys);
        HoveredAdvancementStatus status = buildHoveredAdvancementStatus(config, translationTemplateKeys);
        return new HoveredAdvancementText(
                translatedTitle,
                translatedDescription,
                status.statusLine(),
                status.errorStatusLine(),
                showRefreshNotice
        );
    }

    public static Component createRefreshNoticeLine() {
        return TooltipRefreshNoticeSupport.createRefreshNoticeLine();
    }

    public static Component translateDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        Component styledDescription = styleDescription(display, originalDescription);
        return translateComponent(holder, styledDescription, "description", false);
    }

    public static Component translateHoveredDescription(
            AdvancementHolder holder,
            DisplayInfo display,
            Component originalDescription
    ) {
        Component styledDescription = styleDescription(display, originalDescription);
        return translateComponent(holder, styledDescription, "description", true);
    }

    private static Component translateComponent(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing
    ) {
        return translateComponent(holder, originalText, fieldName, queueIfMissing, true);
    }

    private static Component translateComponent(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing,
            boolean allowForceRefresh
    ) {
        if (originalText == null || originalText.getString().trim().isEmpty()) {
            return originalText;
        }
        if (!isVanillaAdvancement(holder)) {
            return originalText;
        }

        OtherTranslationsConfig config = currentOtherTranslationsConfig();
        if (!isAdvancementTranslationFeatureEnabled(config)) {
            return originalText;
        }

        if (config.component_json_v1_advancements) {
            return translateComponentV1(holder, originalText, fieldName, queueIfMissing, allowForceRefresh, config);
        }

        try {
            StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(originalText);
            TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
            String translationTemplateKey = templateResult.template();
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                return originalText;
            }

            if (queueIfMissing && allowForceRefresh) {
                maybeForceRefreshAdvancementKeys(config, Set.of(translationTemplateKey));
            }

            if (!shouldRenderTranslatedAdvancement(config)) {
                return originalText;
            }

            LookupResult lookupResult = queueIfMissing
                    ? OtherTranslationsTextCache.getInstance().lookupOrQueue(translationTemplateKey)
                    : OtherTranslationsTextCache.getInstance().peek(translationTemplateKey);
            TranslationStatus status = lookupResult.status();
            if (status == TranslationStatus.TRANSLATED) {
                String translatedTemplate = lookupResult.translation();
                if (translatedTemplate == null || translatedTemplate.isBlank()) {
                    return originalText;
                }
                String reassembledTranslated = TemplateProcessor.reassemble(translatedTemplate, templateResult.values());
                return StylePreserver.reapplyStylesFromTags(reassembledTranslated, styleResult.styleMap, true);
            }

            String animationKey = "advancement:" + holder.id() + ":" + fieldName;
            if (status == TranslationStatus.PENDING || status == TranslationStatus.IN_PROGRESS) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, false);
            }
            if (status == TranslationStatus.ERROR) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, true);
            }
        } catch (RuntimeException e) {
            if (UNEXPECTED_FAILURE_LOGGED.compareAndSet(false, true)) {
                Translate_AllinOne.LOGGER.error("Failed to translate vanilla advancement text.", e);
            }
        }

        return originalText;
    }

    private static Component translateComponentV1(
            AdvancementHolder holder,
            Component originalText,
            String fieldName,
            boolean queueIfMissing,
            boolean allowForceRefresh,
            OtherTranslationsConfig config
    ) {
        try {
            StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(originalText);
            TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
            String legacyKey = templateResult.template();
            ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                    originalText,
                    ComponentTranslationRoute.ADVANCEMENT,
                    "advancement:" + fieldName,
                    "advancement-v1"
            );
            if (document.units().isEmpty()) {
                return originalText;
            }

            if (queueIfMissing && allowForceRefresh) {
                maybeForceRefreshAdvancementDocument(config, document);
            }

            ComponentTranslationApplier applier = new ComponentTranslationApplier();
            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    config.target_language,
                    legacyKey,
                    () -> peekLegacyTranslation(styleResult, templateResult, legacyKey),
                    response -> {
                        long startedAt = System.nanoTime();
                        Component translated = applier.apply(document, response);
                        ComponentTranslationMetrics.recordNanos(
                                ComponentTranslationRoute.ADVANCEMENT,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                        return translated;
                    },
                    "advancement:" + holder.id() + ":" + fieldName,
                    queueIfMissing
            );
            if (resolution.state() == ComponentTranslationRuntime.State.V1_HIT
                    || resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT) {
                return resolution.value() == null ? originalText : resolution.value();
            }
            String animationKey = "advancement:" + holder.id() + ":" + fieldName;
            if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, false);
            }
            if (resolution.state() == ComponentTranslationRuntime.State.FAILED) {
                return AnimationManager.getAnimatedStyledText(originalText, animationKey, true);
            }
        } catch (RuntimeException e) {
            if (UNEXPECTED_FAILURE_LOGGED.compareAndSet(false, true)) {
                Translate_AllinOne.LOGGER.error("Failed to prepare vanilla advancement Component V1 text.", e);
            }
            return originalText;
        }
        return originalText;
    }

    private static Component peekLegacyTranslation(
            StylePreserver.ExtractionResult styleResult,
            TemplateProcessor.TemplateExtractionResult templateResult,
            String legacyKey
    ) {
        if (legacyKey == null || legacyKey.isBlank()) {
            return null;
        }
        LookupResult lookup = OtherTranslationsTextCache.getInstance().peek(legacyKey);
        if (lookup.status() != TranslationStatus.TRANSLATED
                || lookup.translation() == null
                || lookup.translation().isBlank()) {
            return null;
        }
        String reassembled = TemplateProcessor.reassemble(lookup.translation(), templateResult.values());
        return StylePreserver.reapplyStylesFromTags(reassembled, styleResult.styleMap, true);
    }

    private static void maybeForceRefreshAdvancementDocument(
            OtherTranslationsConfig config,
            ComponentTranslationDocument document
    ) {
        if (config == null || config.keybinding == null || config.keybinding.refreshBinding == null) {
            return;
        }
        boolean pressed = KeybindingManager.isPressed(config.keybinding.refreshBinding);
        long now = System.currentTimeMillis();
        if (!updateRefreshHoldState(pressed, now)) {
            return;
        }

        String v1Key = ComponentTranslationRuntime.cacheKey(document, config.target_language);
        synchronized (refreshedKeysThisHold) {
            if (!refreshedKeysThisHold.add("v1:" + v1Key)) {
                return;
            }
        }
        ComponentTranslationRuntime.forceRefresh(document, config.target_language);
    }

    private static void maybeForceRefreshAdvancementKeys(OtherTranslationsConfig config, Set<String> translationTemplateKeys) {
        if (!isAdvancementTranslationFeatureEnabled(config)
                || translationTemplateKeys == null
                || translationTemplateKeys.isEmpty()) {
            return;
        }
        if (config == null || config.keybinding == null || config.keybinding.refreshBinding == null) {
            return;
        }

        boolean isRefreshPressed = KeybindingManager.isPressed(config.keybinding.refreshBinding);
        long now = System.currentTimeMillis();
        if (!updateRefreshHoldState(isRefreshPressed, now)) {
            return;
        }

        List<String> keysToRefresh = new ArrayList<>();
        synchronized (refreshedKeysThisHold) {
            for (String translationTemplateKey : translationTemplateKeys) {
                if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                    continue;
                }
                if (refreshedKeysThisHold.add(translationTemplateKey)) {
                    keysToRefresh.add(translationTemplateKey);
                }
            }
        }
        if (keysToRefresh.isEmpty()) {
            return;
        }

        int refreshedCount = OtherTranslationsTextCache.getInstance().forceRefresh(keysToRefresh);
        if (refreshedCount > 0) {
            TooltipTemplateRuntime.registerForceRefreshCompatBypass(keysToRefresh);
            refreshNoticeAdvancementSignature = computeTranslationTemplateKeysSignature(translationTemplateKeys);
            refreshNoticeExpiresAtMillis = now + REFRESH_NOTICE_DURATION_MILLIS;
            Translate_AllinOne.LOGGER.info(
                    "Force-refreshed {} vanilla advancement translation key(s).",
                    refreshedCount
            );
        }
    }

    private static boolean updateRefreshHoldState(boolean isRefreshPressed, long now) {
        if (isRefreshPressed) {
            refreshHoldActive = true;
            refreshHoldGraceExpiresAtMillis = now + REFRESH_HOLD_RELEASE_GRACE_MILLIS;
            return true;
        }

        if (refreshHoldActive && now <= refreshHoldGraceExpiresAtMillis) {
            return true;
        }

        clearRefreshHoldState();
        return false;
    }

    private static void clearRefreshHoldState() {
        refreshHoldActive = false;
        refreshHoldGraceExpiresAtMillis = 0L;
        synchronized (refreshedKeysThisHold) {
            refreshedKeysThisHold.clear();
        }
    }

    private static boolean shouldShowRefreshNotice(Set<String> translationTemplateKeys) {
        long expiresAt = refreshNoticeExpiresAtMillis;
        if (expiresAt <= 0L || System.currentTimeMillis() > expiresAt) {
            return false;
        }

        if (translationTemplateKeys == null || translationTemplateKeys.isEmpty()) {
            return false;
        }
        return computeTranslationTemplateKeysSignature(translationTemplateKeys) == refreshNoticeAdvancementSignature;
    }

    private static int computeTranslationTemplateKeysSignature(Set<String> translationTemplateKeys) {
        int hash = 1;
        List<String> orderedKeys = new ArrayList<>(translationTemplateKeys.size());
        for (String translationTemplateKey : translationTemplateKeys) {
            orderedKeys.add(translationTemplateKey == null ? "" : translationTemplateKey);
        }
        Collections.sort(orderedKeys);
        for (String translationTemplateKey : orderedKeys) {
            hash = 31 * hash + translationTemplateKey.hashCode();
        }
        return 31 * hash + translationTemplateKeys.size();
    }

    private static HoveredAdvancementStatus buildHoveredAdvancementStatus(
            OtherTranslationsConfig config,
            Set<String> translationTemplateKeys
    ) {
        if (!shouldRenderTranslatedAdvancement(config)
                || config.component_json_v1_advancements
                || translationTemplateKeys == null
                || translationTemplateKeys.isEmpty()) {
            return new HoveredAdvancementStatus(null, null);
        }

        OtherTranslationsTextCache cache = OtherTranslationsTextCache.getInstance();
        boolean pending = false;
        boolean missingKeyIssue = false;
        String errorMessage = "";
        int translatableLines = 0;
        for (String translationTemplateKey : translationTemplateKeys) {
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                continue;
            }

            translatableLines++;
            LookupResult lookupResult = cache.peek(translationTemplateKey);
            if (lookupResult.status() == TranslationStatus.NOT_CACHED) {
                lookupResult = cache.lookupOrQueue(translationTemplateKey);
            }

            TranslationStatus status = lookupResult.status();
            if (status == TranslationStatus.PENDING || status == TranslationStatus.IN_PROGRESS) {
                pending = true;
            } else if (status == TranslationStatus.ERROR) {
                String lookupErrorMessage = lookupResult.errorMessage();
                if (TooltipInternalLineSupport.isMissingKeyIssue(lookupErrorMessage)) {
                    pending = true;
                    missingKeyIssue = true;
                } else if (errorMessage.isBlank()) {
                    errorMessage = lookupErrorMessage;
                }
            }
        }

        TooltipTranslationSupport.TooltipProcessingResult processedTooltip =
                new TooltipTranslationSupport.TooltipProcessingResult(
                        List.of(),
                        translatableLines,
                        pending,
                        missingKeyIssue,
                        errorMessage
                );
        CacheStats stats = cache.getCacheStats();
        Component statusLine = TooltipInternalLineSupport.shouldShowStatusLine(processedTooltip, stats)
                ? TooltipInternalLineSupport.createStatusLine(stats, missingKeyIssue, ADVANCEMENT_STATUS_ANIMATION_KEY)
                : null;
        Component errorStatusLine = TooltipInternalLineSupport.shouldShowErrorStatusLine(processedTooltip)
                ? TooltipInternalLineSupport.createErrorStatusLine(processedTooltip.errorMessage())
                : null;
        return new HoveredAdvancementStatus(statusLine, errorStatusLine);
    }

    private static Set<String> collectTranslationTemplateKeys(AdvancementHolder holder, List<Component> texts) {
        if (!isVanillaAdvancement(holder) || texts == null || texts.isEmpty()) {
            return Set.of();
        }

        OtherTranslationsConfig config = currentOtherTranslationsConfig();
        if (!isAdvancementTranslationFeatureEnabled(config)) {
            return Set.of();
        }

        LinkedHashSet<String> translationTemplateKeys = new LinkedHashSet<>();
        for (Component text : texts) {
            String translationTemplateKey = extractTranslationTemplateKey(text);
            if (translationTemplateKey != null && !translationTemplateKey.isBlank()) {
                translationTemplateKeys.add(translationTemplateKey);
            }
        }
        return translationTemplateKeys.isEmpty() ? Set.of() : Collections.unmodifiableSet(translationTemplateKeys);
    }

    private static String extractTranslationTemplateKey(Component text) {
        if (text == null || text.getString().trim().isEmpty()) {
            return "";
        }

        try {
            StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(text);
            TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
            return templateResult.template();
        } catch (RuntimeException e) {
            if (UNEXPECTED_FAILURE_LOGGED.compareAndSet(false, true)) {
                Translate_AllinOne.LOGGER.error("Failed to collect vanilla advancement translation key.", e);
            }
            return "";
        }
    }

    private static Component styleDescription(DisplayInfo display, Component originalDescription) {
        if (display != null && originalDescription != null && display.getType() != null) {
            return ComponentUtils.mergeStyles(
                    originalDescription,
                    Style.EMPTY.withColor(display.getType().getChatColor())
            );
        }
        return originalDescription;
    }

    private static boolean isAdvancementTranslationFeatureEnabled(OtherTranslationsConfig config) {
        return config != null && config.enabled && config.enabled_translate_vanilla_advancements;
    }

    private static OtherTranslationsConfig currentOtherTranslationsConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config == null ? null : config.otherTranslations;
    }

    private static boolean shouldRenderTranslatedAdvancement(OtherTranslationsConfig config) {
        if (!isAdvancementTranslationFeatureEnabled(config)) {
            return false;
        }

        OtherTranslationsConfig.KeybindingMode mode = config.keybinding == null || config.keybinding.mode == null
                ? OtherTranslationsConfig.KeybindingMode.DISABLED
                : config.keybinding.mode;
        boolean keyPressed = config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.binding);
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> keyPressed;
            case HOLD_TO_SEE_ORIGINAL -> !keyPressed;
            case DISABLED -> true;
        };
    }
}
