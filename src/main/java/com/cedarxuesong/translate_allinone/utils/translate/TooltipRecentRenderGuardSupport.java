package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class TooltipRecentRenderGuardSupport {
    private static final StyleSpriteSource.Font WYNNCRAFT_TOOLTIP_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "language/wynncraft"));

    private TooltipRecentRenderGuardSupport() {
    }

    public static void rememberMirroredTooltip(List<Text> originalTooltip, List<Text> mirroredTooltip) {
        rememberMirroredTooltip(originalTooltip, mirroredTooltip, false);
    }

    public static void rememberMirroredTooltip(
            List<Text> originalTooltip,
            List<Text> mirroredTooltip,
            boolean tooltipLocallyStable
    ) {
        if (mirroredTooltip == originalTooltip) {
            clearRememberedTooltip();
            return;
        }
        rememberTooltipIfStable(mirroredTooltip, tooltipLocallyStable);
    }

    public static void rememberTooltipIfStable(List<Text> tooltip) {
        rememberTooltipIfStable(tooltip, false);
    }

    public static void rememberTooltipIfStable(List<Text> tooltip, boolean tooltipLocallyStable) {
        TooltipTranslationContext.rememberRecentTranslatedTooltip(
                stableTooltipForRemembering(tooltip, tooltipLocallyStable)
        );
    }

    public static void clearRememberedTooltip() {
        TooltipTranslationContext.rememberRecentTranslatedTooltip(null);
    }

    public static boolean shouldSkipDuplicateRender(List<Text> tooltip, boolean showRefreshNotice) {
        if (showRefreshNotice) {
            return false;
        }

        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        return sanitizedTooltip != null
                && !sanitizedTooltip.isEmpty()
                && TooltipTranslationContext.matchesRecentTranslatedTooltip(sanitizedTooltip);
    }

    public static boolean canRememberRecentTranslatedTooltip(List<Text> tooltip) {
        return stableTooltipForRemembering(tooltip, false) != null;
    }

    public static boolean looksLikeDedicatedWynnmodTooltip(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return false;
        }

        boolean hasWynncraftFont = false;
        boolean hasMeaningfulLine = false;
        boolean hasDecorativeGlyph = false;
        for (Text line : sanitizedTooltip) {
            if (line == null) {
                continue;
            }

            String raw = line.getString();
            if (!hasDecorativeGlyph && raw != null && TooltipTemplateRuntime.containsDecorativeGlyph(raw)) {
                hasDecorativeGlyph = true;
            }

            if (!hasMeaningfulLine && TooltipTextMatcherSupport.hasMeaningfulContent(line)) {
                hasMeaningfulLine = true;
            }

            if (hasWynncraftFont) {
                continue;
            }

            for (FlatNode node : FlatNode.compact(FlatNode.flatten(line))) {
                if (node.style() != null && WYNNCRAFT_TOOLTIP_FONT.equals(node.style().getFont())) {
                    hasWynncraftFont = true;
                    break;
                }
            }
        }
        return hasWynncraftFont && hasMeaningfulLine && hasDecorativeGlyph;
    }

    private static List<Text> stableTooltipForRemembering(List<Text> tooltip, boolean tooltipLocallyStable) {
        if (tooltip == null || tooltip.isEmpty()) {
            return null;
        }

        boolean containsInternalGeneratedLine = false;
        for (Text line : tooltip) {
            if (TooltipInternalLineSupport.isInternalGeneratedLine(line)) {
                containsInternalGeneratedLine = true;
                break;
            }
        }
        if (containsInternalGeneratedLine && !tooltipLocallyStable) {
            return null;
        }

        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        return sanitizedTooltip == null || sanitizedTooltip.isEmpty() ? null : sanitizedTooltip;
    }
}
