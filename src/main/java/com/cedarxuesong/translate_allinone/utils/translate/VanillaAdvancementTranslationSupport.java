package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheStats;
import com.cedarxuesong.translate_allinone.utils.cache.OtherTranslationsTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.LookupResult;
import com.cedarxuesong.translate_allinone.utils.cache.TranslationStatus;
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

        maybeForceRefreshAdvancementKeys(currentOtherTranslationsConfig(), translationTemplateKeys);
        boolean showRefreshNotice = shouldShowRefreshNotice(translationTemplateKeys);
        Component translatedTitle = translateComponent(holder, originalTitle, "title", true, false);
        Component translatedDescription = translateComponent(holder, styledDescription, "description", true, false);
        HoveredAdvancementStatus status = buildHoveredAdvancementStatus(currentOtherTranslationsConfig(), translationTemplateKeys);
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
