package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public final class TooltipTranslationSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";

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
        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template;
        String legacyTemplateKey = StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);

        ItemTemplateCache.LookupResult lookupResult = ItemTemplateCache.getInstance().lookupOrQueue(legacyTemplateKey);
        ItemTemplateCache.TranslationStatus status = lookupResult.status();
        boolean pending = status == ItemTemplateCache.TranslationStatus.PENDING || status == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = false;

        String translatedTemplate = lookupResult.translation();
        String reassembledOriginal = TemplateProcessor.reassemble(unicodeTemplate, templateResult.values);
        Text originalTextObject = StylePreserver.reapplyStyles(reassembledOriginal, styleResult.styleMap);

        Text finalTooltipLine;
        if (status == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassemble(translatedTemplate, templateResult.values);
            finalTooltipLine = StylePreserver.fromLegacyText(reassembledTranslated);
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
            finalTooltipLine = AnimationManager.getAnimatedStyledText(originalTextObject, legacyTemplateKey, false);
        }

        return new TooltipLineResult(finalTooltipLine, pending, missingKeyIssue);
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
