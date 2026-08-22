package com.alexeys.translate_allinone.utils.translate;

import java.util.List;
import net.minecraft.network.chat.Component;

public final class TooltipDecorativeContextSupport {
    private TooltipDecorativeContextSupport() {
    }

    public static boolean isDecorativeTooltipContext(List<Component> tooltip) {
        if (TooltipTranslationContext.shouldRequireStrictParagraphStyleCoverage()
                || TooltipRecentRenderGuardSupport.looksLikeDedicatedWynnmodTooltip(tooltip)) {
            return true;
        }
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        for (Component line : tooltip) {
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
