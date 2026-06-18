package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class TooltipRecentRenderGuardSupport {
    private static final FontDescription.Resource WYNNCRAFT_TOOLTIP_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft", "language/wynncraft"));

    private TooltipRecentRenderGuardSupport() {
    }

    public static void rememberMirroredTooltip(List<Component> originalTooltip, List<Component> mirroredTooltip) {
        rememberMirroredTooltip(originalTooltip, mirroredTooltip, false);
    }

    public static void rememberMirroredTooltip(
            List<Component> originalTooltip,
            List<Component> mirroredTooltip,
            boolean tooltipLocallyStable
    ) {
        if (mirroredTooltip == originalTooltip) {
            clearRememberedTooltip();
            return;
        }
        rememberTooltipIfStable(originalTooltip, tooltipLocallyStable);
    }

    public static void rememberTooltipIfStable(List<Component> tooltip) {
        rememberTooltipIfStable(tooltip, false);
    }

    public static void rememberTooltipIfStable(List<Component> tooltip, boolean tooltipLocallyStable) {
        TooltipTranslationContext.rememberRecentTranslatedTooltip(
                stableTooltipForRemembering(tooltip, tooltipLocallyStable)
        );
    }

    public static void clearRememberedTooltip() {
        TooltipTranslationContext.rememberRecentTranslatedTooltip(null);
    }

    public static boolean shouldSkipDuplicateRender(List<Component> tooltip, boolean showRefreshNotice) {
        if (showRefreshNotice) {
            return false;
        }

        List<Component> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        return sanitizedTooltip != null
                && !sanitizedTooltip.isEmpty()
                && TooltipTranslationContext.matchesRecentTranslatedTooltip(sanitizedTooltip);
    }

    public static boolean canRememberRecentTranslatedTooltip(List<Component> tooltip) {
        return stableTooltipForRemembering(tooltip, false) != null;
    }

    public static boolean looksLikeDedicatedWynnmodTooltip(List<Component> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        List<Component> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return false;
        }

        boolean hasWynncraftFont = false;
        boolean hasMeaningfulLine = false;
        boolean hasDecorativeGlyph = false;
        for (Component line : sanitizedTooltip) {
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

    private static List<Component> stableTooltipForRemembering(List<Component> tooltip, boolean tooltipLocallyStable) {
        if (tooltip == null || tooltip.isEmpty()) {
            return null;
        }

        boolean containsInternalGeneratedLine = false;
        for (Component line : tooltip) {
            if (TooltipInternalLineSupport.isInternalGeneratedLine(line)) {
                containsInternalGeneratedLine = true;
                break;
            }
        }
        if (containsInternalGeneratedLine && !tooltipLocallyStable) {
            return null;
        }

        List<Component> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        return sanitizedTooltip == null || sanitizedTooltip.isEmpty() ? null : sanitizedTooltip;
    }
}
