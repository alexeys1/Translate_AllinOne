package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TooltipTranslationSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String TOOLTIP_REFRESH_NOTICE_KEY = "text.translate_allinone.item.tooltip_refresh_forced";
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");
    private static final Set<Integer> refreshedTooltipSignaturesThisHold = new HashSet<>();
    private static volatile int refreshNoticeTooltipSignature = 0;
    private static volatile long refreshNoticeExpiresAtMillis = 0L;

    private TooltipTranslationSupport() {
    }

    public record TooltipLineResult(Text translatedLine, boolean pending, boolean missingKeyIssue) {
    }

    public static boolean shouldShowOriginal(ItemTranslateConfig.KeybindingMode mode, boolean isKeyPressed) {
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> !isKeyPressed;
            case HOLD_TO_SEE_ORIGINAL -> isKeyPressed;
            case DISABLED -> false;
        };
    }

    public static TooltipLineResult translateLine(Text line) {
        return translateLine(line, false);
    }

    public static TooltipLineResult translateLine(Text line, boolean useTagStylePreservation) {
        StylePreserver.ExtractionResult styleResult = useTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(line)
                : StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = useTagStylePreservation
                ? TemplateProcessor.extractDecorativeGlyphTags(unicodeTemplate)
                : new TemplateProcessor.DecorativeGlyphExtractionResult(unicodeTemplate, List.of());
        String normalizedTemplate = glyphResult.template();
        String translationTemplateKey = useTagStylePreservation
                ? normalizedTemplate
                : StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);

        ItemTemplateCache.LookupResult lookupResult = ItemTemplateCache.getInstance().lookupOrQueue(translationTemplateKey);
        ItemTemplateCache.TranslationStatus status = lookupResult.status();
        boolean pending = status == ItemTemplateCache.TranslationStatus.PENDING || status == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = false;

        String translatedTemplate = lookupResult.translation();
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(normalizedTemplate, templateResult.values()),
                glyphResult.values()
        );
        Text originalTextObject = useTagStylePreservation
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, styleResult.styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, styleResult.styleMap);

        Text finalTooltipLine;
        if (status == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                    TemplateProcessor.reassemble(translatedTemplate, templateResult.values()),
                    glyphResult.values()
            );
            finalTooltipLine = useTagStylePreservation
                    ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, styleResult.styleMap, true)
                    : StylePreserver.fromLegacyText(reassembledTranslated);
        } else if (status == ItemTemplateCache.TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (isMissingKeyIssue(errorMessage)) {
                pending = true;
                missingKeyIssue = true;
                finalTooltipLine = originalTextObject;
            } else {
                finalTooltipLine = Text.literal("Error: " + errorMessage).formatted(Formatting.RED);
            }
        } else {
            finalTooltipLine = AnimationManager.getAnimatedStyledText(originalTextObject, translationTemplateKey, false);
        }

        return new TooltipLineResult(finalTooltipLine, pending, missingKeyIssue);
    }

    public static void maybeForceRefreshCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        boolean isRefreshPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
        if (!isRefreshPressed) {
            synchronized (refreshedTooltipSignaturesThisHold) {
                refreshedTooltipSignaturesThisHold.clear();
            }
            return;
        }

        Set<String> keysToRefresh = collectTranslatableTemplateKeys(tooltip, config);
        if (keysToRefresh.isEmpty()) {
            return;
        }

        int tooltipSignature = computeTooltipSignature(keysToRefresh);
        synchronized (refreshedTooltipSignaturesThisHold) {
            if (!refreshedTooltipSignaturesThisHold.add(tooltipSignature)) {
                return;
            }
        }

        int refreshedCount = ItemTemplateCache.getInstance().forceRefresh(keysToRefresh);
        if (refreshedCount > 0) {
            refreshNoticeTooltipSignature = tooltipSignature;
            refreshNoticeExpiresAtMillis = System.currentTimeMillis() + REFRESH_NOTICE_DURATION_MILLIS;
            LOGGER.info("Forced refresh of {} current item tooltip translation key(s).", refreshedCount);
        }
    }

    public static List<Text> buildTranslatedTooltip(List<Text> originalTooltip, String animationKey) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return originalTooltip;
        }

        List<Text> tooltip = stripInternalGeneratedLines(originalTooltip);
        if (tooltip.isEmpty()) {
            return tooltip;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return tooltip;
        }

        maybeForceRefreshCurrentTooltip(tooltip, config);
        boolean showRefreshNotice = shouldShowRefreshNotice(tooltip, config);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }

        try {
            List<Text> mirroredTooltip = new ArrayList<>();
            boolean isFirstLine = true;
            int translatableLines = 0;
            boolean isCurrentItemStackPending = false;
            boolean hasMissingKeyIssue = false;

            for (Text line : tooltip) {
                if (line == null || line.getString().trim().isEmpty()) {
                    mirroredTooltip.add(line);
                    continue;
                }

                boolean shouldTranslate = false;
                if (isFirstLine) {
                    if (config.enabled_translate_item_custom_name) {
                        shouldTranslate = true;
                    }
                    isFirstLine = false;
                } else if (config.enabled_translate_item_lore) {
                    shouldTranslate = true;
                }

                if (!shouldTranslate) {
                    mirroredTooltip.add(line);
                    continue;
                }

                translatableLines++;

                TooltipLineResult lineResult = translateLine(line, config.wynn_item_compatibility);
                if (lineResult.pending()) {
                    isCurrentItemStackPending = true;
                }
                if (lineResult.missingKeyIssue()) {
                    hasMissingKeyIssue = true;
                }

                mirroredTooltip.add(lineResult.translatedLine());
            }

            if (translatableLines > 0) {
                ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
                boolean isAnythingPending = stats.total() > stats.translated();
                boolean shouldShowStatus = isCurrentItemStackPending || hasMissingKeyIssue || isAnythingPending;

                if (shouldShowStatus) {
                    mirroredTooltip.add(createStatusLine(
                            stats,
                            hasMissingKeyIssue,
                            animationKey
                    ));
                }
            }

            return appendRefreshNoticeLine(mirroredTooltip, showRefreshNotice);
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip", e);
            return appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }
    }

    public static Text createStatusLine(
            ItemTemplateCache.CacheStats stats,
            boolean hasMissingKeyIssue,
            String animationKey
    ) {
        float percentage = (stats.total() > 0) ? ((float) stats.translated() / stats.total()) * 100 : 100;
        String progressText = String.format(" (%d/%d) - %.0f%%", stats.translated(), stats.total(), percentage);

        Text statusMessage = hasMissingKeyIssue
                ? Text.literal("Item translation key mismatch, retrying...").formatted(Formatting.RED)
                : Text.literal("Translating...").formatted(Formatting.GRAY);

        MutableText statusText = AnimationManager.getAnimatedStyledText(statusMessage, animationKey, hasMissingKeyIssue);
        return statusText.append(Text.literal(progressText).formatted(Formatting.YELLOW));
    }

    public static boolean isMissingKeyIssue(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains(MISSING_KEY_HINT) || lower.contains(KEY_MISMATCH_HINT);
    }

    public static boolean isInternalStatusLine(Text line) {
        if (line == null) {
            return false;
        }

        String content = line.getString();
        return content.startsWith("Translating...")
                || content.startsWith("Item translation key mismatch, retrying...");
    }

    public static boolean isRefreshNoticeLine(Text line) {
        if (line == null) {
            return false;
        }
        return createRefreshNoticeLine().getString().equals(line.getString());
    }

    public static boolean isInternalGeneratedLine(Text line) {
        return isInternalStatusLine(line) || isRefreshNoticeLine(line);
    }

    public static List<Text> stripInternalGeneratedLines(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        List<Text> sanitized = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);
            if (!isInternalGeneratedLine(line)) {
                if (sanitized != null) {
                    sanitized.add(line);
                }
                continue;
            }

            if (sanitized == null) {
                sanitized = new ArrayList<>(tooltip.size());
                sanitized.addAll(tooltip.subList(0, i));
            }
        }
        return sanitized == null ? tooltip : sanitized;
    }

    public static boolean shouldShowRefreshNotice(List<Text> tooltip, ItemTranslateConfig config) {
        long expiresAt = refreshNoticeExpiresAtMillis;
        if (expiresAt <= 0L || System.currentTimeMillis() > expiresAt) {
            return false;
        }

        Set<String> keys = collectTranslatableTemplateKeys(tooltip, config);
        if (keys.isEmpty()) {
            return false;
        }
        return computeTooltipSignature(keys) == refreshNoticeTooltipSignature;
    }

    public static Text createRefreshNoticeLine() {
        return Text.translatable(TOOLTIP_REFRESH_NOTICE_KEY).formatted(Formatting.GREEN);
    }

    public static List<Text> appendRefreshNoticeLine(List<Text> tooltip, boolean showRefreshNotice) {
        if (!showRefreshNotice || tooltip == null) {
            return tooltip;
        }

        for (Text line : tooltip) {
            if (isRefreshNoticeLine(line)) {
                return tooltip;
            }
        }

        List<Text> tooltipWithNotice = new ArrayList<>(tooltip.size() + 1);
        tooltipWithNotice.addAll(tooltip);
        tooltipWithNotice.add(createRefreshNoticeLine());
        return tooltipWithNotice;
    }

    private static Set<String> collectTranslatableTemplateKeys(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keys = new LinkedHashSet<>();
        if (tooltip == null || tooltip.isEmpty() || config == null) {
            return keys;
        }

        boolean isFirstLine = true;
        for (Text line : tooltip) {
            if (line == null || line.getString().trim().isEmpty() || isInternalGeneratedLine(line)) {
                continue;
            }

            boolean shouldTranslate = isFirstLine
                    ? config.enabled_translate_item_custom_name
                    : config.enabled_translate_item_lore;
            isFirstLine = false;
            if (!shouldTranslate) {
                continue;
            }

            String translationTemplateKey = extractTemplateKey(line, config.wynn_item_compatibility);
            if (translationTemplateKey != null && !translationTemplateKey.isBlank()) {
                keys.add(translationTemplateKey);
            }
        }
        return keys;
    }

    private static String extractTemplateKey(Text line, boolean useTagStylePreservation) {
        StylePreserver.ExtractionResult styleResult = useTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(line)
                : StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = useTagStylePreservation
                ? TemplateProcessor.extractDecorativeGlyphTags(unicodeTemplate)
                : new TemplateProcessor.DecorativeGlyphExtractionResult(unicodeTemplate, List.of());
        String normalizedTemplate = glyphResult.template();
        return useTagStylePreservation
                ? normalizedTemplate
                : StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);
    }

    private static int computeTooltipSignature(Set<String> keys) {
        int hash = 1;
        for (String key : keys) {
            hash = 31 * hash + key.hashCode();
        }
        return 31 * hash + keys.size();
    }
}
