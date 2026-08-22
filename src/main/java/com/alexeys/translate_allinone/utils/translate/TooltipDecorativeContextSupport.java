package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;

import java.util.List;

public final class TooltipDecorativeContextSupport {
    private TooltipDecorativeContextSupport() {
    }

    public static boolean isDecorativeTooltipContext(List<Text> tooltip) {
        if (TooltipTranslationContext.shouldRequireStrictParagraphStyleCoverage()
                || TooltipRecentRenderGuardSupport.looksLikeDedicatedWynnmodTooltip(tooltip)) {
            return true;
        }
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        for (Text line : tooltip) {
            if (line == null || TooltipInternalLineSupport.isInternalGeneratedLine(line)) {
                continue;
            }

            String raw = line.getString();
            if (raw != null && TooltipTemplateRuntime.containsDecorativeGlyph(raw)) {
                return true;
            }
        }
        return false;
    }
}
