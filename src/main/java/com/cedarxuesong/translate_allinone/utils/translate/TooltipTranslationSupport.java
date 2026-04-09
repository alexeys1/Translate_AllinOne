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
import java.util.List;
import java.util.Locale;

public final class TooltipTranslationSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");

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

    public static List<Text> buildTranslatedTooltip(List<Text> originalTooltip, String animationKey) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return originalTooltip;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return originalTooltip;
        }

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return originalTooltip;
        }

        try {
            List<Text> mirroredTooltip = new ArrayList<>();
            boolean isFirstLine = true;
            int translatableLines = 0;
            boolean isCurrentItemStackPending = false;
            boolean hasMissingKeyIssue = false;

            for (Text line : originalTooltip) {
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

            return mirroredTooltip;
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip", e);
            return originalTooltip;
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
}
