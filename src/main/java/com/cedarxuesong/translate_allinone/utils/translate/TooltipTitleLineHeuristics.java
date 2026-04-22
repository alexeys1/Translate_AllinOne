package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.text.Text;

final class TooltipTitleLineHeuristics {
    private TooltipTitleLineHeuristics() {
    }

    record TitleLineEvaluation(
            boolean firstContentLine,
            boolean consumesNameSlot,
            String nextFirstTitleComparisonText
    ) {
    }

    static TitleLineEvaluation evaluateLine(
            Text line,
            boolean nameSlotAvailable,
            boolean wynnCompatibilityEnabled,
            String firstTitleComparisonText
    ) {
        boolean hasMeaningfulContent = TooltipTextMatcherSupport.hasMeaningfulContent(line, wynnCompatibilityEnabled);
        boolean consumesNameSlot = nameSlotAvailable && hasMeaningfulContent;
        boolean duplicateWynnTitleLine = !nameSlotAvailable
                && wynnCompatibilityEnabled
                && hasMeaningfulContent
                && looksLikeDuplicateWynnTitleLine(line, firstTitleComparisonText);
        boolean firstContentLine = consumesNameSlot || duplicateWynnTitleLine;
        return new TitleLineEvaluation(
                firstContentLine,
                consumesNameSlot,
                consumesNameSlot ? extractTitleComparisonText(line) : firstTitleComparisonText
        );
    }

    private static boolean looksLikeDuplicateWynnTitleLine(Text line, String firstTitleComparisonText) {
        if (line == null
                || firstTitleComparisonText == null
                || firstTitleComparisonText.isBlank()) {
            return false;
        }

        String raw = line.getString();
        if (raw == null || raw.isBlank() || !TooltipTemplateRuntime.containsDecorativeGlyph(raw)) {
            return false;
        }

        String currentComparisonText = extractTitleComparisonText(line);
        if (currentComparisonText.isBlank()) {
            return false;
        }

        return currentComparisonText.equals(firstTitleComparisonText)
                || hasEquivalentNumericBracketSuffix(currentComparisonText, firstTitleComparisonText);
    }

    private static String extractTitleComparisonText(Text line) {
        if (line == null) {
            return "";
        }

        return TooltipRoutePlanner.normalizeTooltipText(
                TooltipTemplateRuntime.stripDecorativeGlyphsForHeuristics(line.getString())
        );
    }

    private static boolean hasEquivalentNumericBracketSuffix(String currentText, String firstTitleComparisonText) {
        if (currentText == null
                || firstTitleComparisonText == null
                || !currentText.startsWith(firstTitleComparisonText)) {
            return false;
        }

        String suffix = currentText.substring(firstTitleComparisonText.length()).trim();
        return !suffix.isEmpty() && suffix.matches("\\[[0-9./%]+]");
    }
}
