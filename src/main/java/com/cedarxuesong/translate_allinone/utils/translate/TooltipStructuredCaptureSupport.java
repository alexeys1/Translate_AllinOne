package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.textmatcher.ContentMatcher;
import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import com.cedarxuesong.translate_allinone.utils.textmatcher.TextMatchResult;
import com.cedarxuesong.translate_allinone.utils.textmatcher.TextPattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

final class TooltipStructuredCaptureSupport {
    private static final TextPattern STRUCTURED_LABEL_VALUE_PATTERN = TextPattern.builder()
            .zeroOrMore(
                    ContentMatcher.any(),
                    "prefix",
                    nodePredicate(TooltipStructuredCaptureSupport::isStructuredPrefixSegment),
                    true
            )
            .oneOrMore(
                    ContentMatcher.any(),
                    "label",
                    nodePredicate(TooltipStructuredCaptureSupport::isStructuredLabelSegment)
            )
            .one(
                    ContentMatcher.textMatching(TooltipStructuredCaptureSupport::isStructuredColonText),
                    "colon",
                    nodePredicate(node -> true)
            )
            .zeroOrMore(
                    ContentMatcher.any(),
                    "spacing",
                    nodePredicate(TooltipStructuredCaptureSupport::isWhitespaceOnlySegment),
                    true
            )
            .oneOrMore(
                    ContentMatcher.any(),
                    "value",
                    nodePredicate(TooltipStructuredCaptureSupport::isStructuredGenericValueSegment)
            )
            .build();

    private TooltipStructuredCaptureSupport() {
    }

    static StructuredTooltipLineResult tryTranslateStructuredLine(Text line, boolean useTagStylePreservation) {
        if (containsEmbeddedLegacyFormattingCodes(line)) {
            return null;
        }

        List<FlatNode> splitNodes = splitStructuredNodes(line);
        if (splitNodes.isEmpty()) {
            return null;
        }

        StructuredLineMatch structuredLine = matchStructuredLine(splitNodes, useTagStylePreservation);
        if (structuredLine == null) {
            EnchantListMatch enchantListMatch = matchEnchantListLine(splitNodes, useTagStylePreservation);
            return enchantListMatch == null ? null : translateEnchantListLine(enchantListMatch, useTagStylePreservation);
        }
        return translateLabelValueStructuredLine(structuredLine, useTagStylePreservation);
    }

    static Set<String> collectStructuredTemplateKeys(Text line, boolean useTagStylePreservation) {
        if (containsEmbeddedLegacyFormattingCodes(line)) {
            return Set.of();
        }

        List<FlatNode> splitNodes = splitStructuredNodes(line);
        if (splitNodes.isEmpty()) {
            return Set.of();
        }

        StructuredLineMatch structuredLine = matchStructuredLine(splitNodes, useTagStylePreservation);
        if (structuredLine != null) {
            return structuredLine.translationTemplateKeys();
        }

        EnchantListMatch enchantListMatch = matchEnchantListLine(splitNodes, useTagStylePreservation);
        return enchantListMatch == null ? Set.of() : enchantListMatch.translationTemplateKeys();
    }

    private static boolean containsEmbeddedLegacyFormattingCodes(Text line) {
        if (line == null) {
            return false;
        }

        String raw = line.getString();
        if (raw == null || raw.isEmpty() || raw.indexOf('§') < 0) {
            return false;
        }
        return !AnimationManager.stripFormatting(raw).equals(raw);
    }

    private static StructuredTooltipLineResult translateLabelValueStructuredLine(
            StructuredLineMatch structuredLine,
            boolean useTagStylePreservation
    ) {
        String debugSummary = buildDetailedDebugSummary(structuredLine);
        TooltipTranslationSupport.TooltipLineResult labelTranslation =
                TooltipTemplateRuntime.translateLine(structuredLine.labelTranslationText(), useTagStylePreservation);
        if (isErrorTranslation(labelTranslation.translatedLine())) {
            return new StructuredTooltipLineResult(
                    labelTranslation,
                    structuredLine.translationTemplateKeys(),
                    debugSummary
            );
        }

        TooltipTranslationSupport.TooltipLineResult valueTranslation = null;
        if (structuredLine.kind().translateValue()) {
            valueTranslation = TooltipTemplateRuntime.translateLine(structuredLine.valueTranslationText(), useTagStylePreservation);
            if (isErrorTranslation(valueTranslation.translatedLine())) {
                return new StructuredTooltipLineResult(
                        valueTranslation,
                        structuredLine.translationTemplateKeys(),
                        debugSummary
                );
            }
        }

        MutableText combined = Text.empty();
        appendPreservedPrefixPassThroughText(combined, structuredLine.prefixText());
        if (labelTranslation.translatedLine() != null) {
            combined.append(labelTranslation.translatedLine());
        }
        appendNormalizedPassThroughText(combined, structuredLine.colonText());
        appendNormalizedPassThroughText(combined, structuredLine.spacingText());
        combined.append(valueTranslation == null
                ? TooltipTemplateRuntime.normalizeDecorativePassthroughText(structuredLine.valueText())
                : valueTranslation.translatedLine());
        return new StructuredTooltipLineResult(
                new TooltipTranslationSupport.TooltipLineResult(
                        combined,
                        labelTranslation.pending() || (valueTranslation != null && valueTranslation.pending()),
                        labelTranslation.missingKeyIssue() || (valueTranslation != null && valueTranslation.missingKeyIssue())
                ),
                structuredLine.translationTemplateKeys(),
                debugSummary
        );
    }

    private static StructuredLineMatch matchStructuredLine(List<FlatNode> splitNodes, boolean useTagStylePreservation) {
        StructuredLineMatch colonStructuredLine = matchColonStructuredLine(splitNodes, useTagStylePreservation);
        if (colonStructuredLine != null) {
            return colonStructuredLine;
        }
        return matchDecorativeStructuredLine(splitNodes, useTagStylePreservation);
    }

    private static StructuredLineMatch matchColonStructuredLine(
            List<FlatNode> splitNodes,
            boolean useTagStylePreservation
    ) {
        TextMatchResult match = STRUCTURED_LABEL_VALUE_PATTERN.match(splitNodes);
        if (!match.success()) {
            return null;
        }

        Text prefixText = toText(match.groupNodes("prefix"));
        List<FlatNode> labelNodes = trimWhitespaceNodes(match.groupNodes("label"));
        List<FlatNode> valueNodes = trimWhitespaceNodes(match.groupNodes("value"));
        Text labelText = toText(labelNodes);
        Text colonText = toText(match.groupNodes("colon"));
        Text spacingText = toText(match.groupNodes("spacing"));
        Text valueText = toText(valueNodes);
        if (labelText == null || valueText == null) {
            return null;
        }

        return buildStructuredLineMatch(
                labelNodes,
                valueNodes,
                toText(match.groupNodes("prefix")),
                toText(match.groupNodes("colon")),
                toText(match.groupNodes("spacing")),
                labelText,
                valueText,
                useTagStylePreservation,
                false
        );
    }

    private static StructuredLineMatch matchDecorativeStructuredLine(
            List<FlatNode> splitNodes,
            boolean useTagStylePreservation
    ) {
        if (splitNodes == null || splitNodes.isEmpty()) {
            return null;
        }

        int prefixEnd = 0;
        while (prefixEnd < splitNodes.size() && isStructuredPrefixSegment(splitNodes.get(prefixEnd))) {
            prefixEnd++;
        }
        if (prefixEnd >= splitNodes.size()) {
            return null;
        }

        int separatorStart = -1;
        for (int index = prefixEnd; index < splitNodes.size(); index++) {
            if (isDecorativeStructuredSeparatorSegment(splitNodes.get(index))) {
                separatorStart = index;
                break;
            }
        }
        if (separatorStart <= prefixEnd || separatorStart >= splitNodes.size() - 1) {
            return null;
        }

        int valueStart = separatorStart;
        while (valueStart < splitNodes.size()
                && (isDecorativeStructuredSeparatorSegment(splitNodes.get(valueStart))
                || isWhitespaceOnlySegment(splitNodes.get(valueStart)))) {
            valueStart++;
        }
        if (valueStart >= splitNodes.size()) {
            return null;
        }

        List<FlatNode> labelNodes = trimWhitespaceNodes(splitNodes.subList(prefixEnd, separatorStart));
        List<FlatNode> valueNodes = trimWhitespaceNodes(splitNodes.subList(valueStart, splitNodes.size()));
        Text labelText = toText(labelNodes);
        Text valueText = toText(valueNodes);
        if (labelText == null || valueText == null) {
            return null;
        }

        return buildStructuredLineMatch(
                labelNodes,
                valueNodes,
                prefixEnd <= 0 ? null : toText(splitNodes.subList(0, prefixEnd)),
                null,
                toText(splitNodes.subList(separatorStart, valueStart)),
                labelText,
                valueText,
                useTagStylePreservation,
                true
        );
    }

    private static StructuredLineMatch buildStructuredLineMatch(
            List<FlatNode> labelNodes,
            List<FlatNode> valueNodes,
            Text prefixText,
            Text colonText,
            Text spacingText,
            Text labelText,
            Text valueText,
            boolean useTagStylePreservation,
            boolean allowWordPhraseValue
    ) {
        StructuredLineKind kind = classifyStructuredLineKind(labelText, valueText, allowWordPhraseValue);
        if (kind == null) {
            return null;
        }

        Text labelTranslationText = toCompactedText(labelNodes);
        String labelKey = TooltipTemplateRuntime.extractTemplateKeyForLine(labelTranslationText, useTagStylePreservation);
        if (labelKey == null || labelKey.isBlank()) {
            return null;
        }

        String valueKey = null;
        Text valueTranslationText = valueText;
        if (kind.translateValue()) {
            valueTranslationText = toCompactedText(valueNodes);
            valueKey = TooltipTemplateRuntime.extractTemplateKeyForLine(valueTranslationText, useTagStylePreservation);
            if (valueKey == null || valueKey.isBlank()) {
                return null;
            }
        }

        return new StructuredLineMatch(
                kind,
                prefixText,
                labelText,
                labelTranslationText,
                colonText,
                spacingText,
                valueText,
                valueTranslationText,
                labelKey,
                valueKey
        );
    }

    private static StructuredTooltipLineResult translateEnchantListLine(
            EnchantListMatch enchantListMatch,
            boolean useTagStylePreservation
    ) {
        if (enchantListMatch == null || enchantListMatch.entries().isEmpty()) {
            return null;
        }

        MutableText combined = Text.empty();
        boolean pending = false;
        boolean missingKeyIssue = false;

        for (EnchantListEntry entry : enchantListMatch.entries()) {
            if (entry.leadingText() != null) {
                combined.append(entry.leadingText());
            }

            TooltipTranslationSupport.TooltipLineResult nameTranslation =
                    TooltipTemplateRuntime.translateLine(entry.nameTranslationText(), useTagStylePreservation);
            if (isErrorTranslation(nameTranslation.translatedLine())) {
                return new StructuredTooltipLineResult(
                        nameTranslation,
                        enchantListMatch.translationTemplateKeys(),
                        buildDebugSummary(enchantListMatch)
                );
            }

            pending |= nameTranslation.pending();
            missingKeyIssue |= nameTranslation.missingKeyIssue();
            combined.append(nameTranslation.translatedLine());
            if (entry.bridgeText() != null) {
                combined.append(entry.bridgeText());
            }
            if (entry.levelText() != null) {
                combined.append(entry.levelText());
            }
            if (entry.trailingText() != null) {
                combined.append(entry.trailingText());
            }
            if (entry.delimiterText() != null) {
                combined.append(entry.delimiterText());
            }
        }

        return new StructuredTooltipLineResult(
                new TooltipTranslationSupport.TooltipLineResult(combined, pending, missingKeyIssue),
                enchantListMatch.translationTemplateKeys(),
                buildDebugSummary(enchantListMatch)
        );
    }

    private static void appendNormalizedPassThroughText(MutableText target, Text text) {
        if (target == null || text == null) {
            return;
        }

        Text normalized = TooltipTemplateRuntime.normalizeDecorativePassthroughText(text);
        if (normalized != null) {
            target.append(normalized);
        }
    }

    private static void appendPreservedPrefixPassThroughText(MutableText target, Text text) {
        if (target == null || text == null) {
            return;
        }

        Text preserved = TooltipTemplateRuntime.preserveDecorativePrefixPassthroughText(text);
        if (preserved != null) {
            target.append(preserved);
        }
    }

    private static StructuredLineKind classifyStructuredLineKind(
            Text labelText,
            Text valueText,
            boolean allowWordPhraseValue
    ) {
        String normalizedLabel = normalizeLabel(textString(labelText));
        String normalizedValue = normalizeValue(textString(valueText));
        if (normalizedLabel.isEmpty() || normalizedValue.isEmpty()) {
            return null;
        }

        if (isStructuredNumericLikeValue(normalizedValue)) {
            return StructuredLineKind.NUMERIC;
        }
        if (isCooldownLikeLabel(normalizedLabel) && isTimedUnitLikeValue(normalizedValue)) {
            return StructuredLineKind.TIMED_VALUE;
        }
        if (isPriceLikeLabel(normalizedLabel) && isCoinsLikeValue(normalizedValue)) {
            return StructuredLineKind.PRICE_VALUE;
        }
        if (isGenericMeasuredValue(normalizedValue)) {
            return StructuredLineKind.MEASURED_VALUE;
        }
        if (allowWordPhraseValue && isWordPhraseValue(normalizedValue)) {
            return StructuredLineKind.WORD_VALUE;
        }
        if (isSelectedLikeLabel(normalizedLabel) && isWordPhraseValue(normalizedValue)) {
            return StructuredLineKind.SELECTED_VALUE;
        }
        if (isMuseumLikeLabel(normalizedLabel) && isDonatedStatusValue(normalizedValue)) {
            return StructuredLineKind.DONATED_STATUS;
        }
        if (isListLikeLabel(normalizedLabel) && isWordListValue(normalizedValue)) {
            return StructuredLineKind.WORD_LIST;
        }

        return null;
    }

    private static EnchantListMatch matchEnchantListLine(List<FlatNode> splitNodes, boolean useTagStylePreservation) {
        if (splitNodes == null || splitNodes.isEmpty()) {
            return null;
        }

        List<EnchantListEntry> entries = new ArrayList<>();
        List<FlatNode> currentEntryNodes = new ArrayList<>();
        for (int index = 0; index < splitNodes.size(); index++) {
            FlatNode node = splitNodes.get(index);
            if (isCommaSegment(node)) {
                if (currentEntryNodes.isEmpty()) {
                    return null;
                }

                List<FlatNode> delimiterNodes = new ArrayList<>();
                delimiterNodes.add(node);
                int nextIndex = index + 1;
                while (nextIndex < splitNodes.size() && isWhitespaceOnlySegment(splitNodes.get(nextIndex))) {
                    delimiterNodes.add(splitNodes.get(nextIndex));
                    nextIndex++;
                }

                EnchantListEntry entry = parseEnchantListEntry(currentEntryNodes, delimiterNodes, useTagStylePreservation);
                if (entry == null) {
                    return null;
                }
                entries.add(entry);
                currentEntryNodes = new ArrayList<>();
                index = nextIndex - 1;
                continue;
            }
            currentEntryNodes.add(node);
        }

        if (entries.isEmpty() || currentEntryNodes.isEmpty()) {
            return null;
        }

        EnchantListEntry lastEntry = parseEnchantListEntry(currentEntryNodes, List.of(), useTagStylePreservation);
        if (lastEntry == null) {
            return null;
        }
        entries.add(lastEntry);
        if (entries.size() < 2) {
            return null;
        }

        return new EnchantListMatch(List.copyOf(entries));
    }

    private static EnchantListEntry parseEnchantListEntry(
            List<FlatNode> entryNodes,
            List<FlatNode> delimiterNodes,
            boolean useTagStylePreservation
    ) {
        if (entryNodes == null || entryNodes.isEmpty()) {
            return null;
        }

        int start = 0;
        int end = entryNodes.size();
        while (start < end && isWhitespaceOnlySegment(entryNodes.get(start))) {
            start++;
        }
        while (end > start && isWhitespaceOnlySegment(entryNodes.get(end - 1))) {
            end--;
        }
        if (start >= end) {
            return null;
        }

        List<FlatNode> leadingNodes = start > 0 ? List.copyOf(entryNodes.subList(0, start)) : List.of();
        List<FlatNode> trailingNodes = end < entryNodes.size() ? List.copyOf(entryNodes.subList(end, entryNodes.size())) : List.of();
        List<FlatNode> coreNodes = List.copyOf(entryNodes.subList(start, end));

        int levelEnd = coreNodes.size();
        int levelStart = levelEnd;
        while (levelStart > 0 && isEnchantLevelSegment(coreNodes.get(levelStart - 1))) {
            levelStart--;
        }
        if (levelStart >= levelEnd) {
            return null;
        }

        int bridgeEnd = levelStart;
        int bridgeStart = bridgeEnd;
        while (bridgeStart > 0 && isWhitespaceOnlySegment(coreNodes.get(bridgeStart - 1))) {
            bridgeStart--;
        }
        if (bridgeStart == bridgeEnd || bridgeStart <= 0) {
            return null;
        }

        List<FlatNode> nameNodes = List.copyOf(coreNodes.subList(0, bridgeStart));
        List<FlatNode> bridgeNodes = List.copyOf(coreNodes.subList(bridgeStart, bridgeEnd));
        List<FlatNode> levelNodes = List.copyOf(coreNodes.subList(levelStart, levelEnd));
        if (!isLikelyEnchantName(nameNodes)) {
            return null;
        }

        Text nameText = toText(nameNodes);
        Text nameTranslationText = toCompactedText(nameNodes);
        Text levelText = toText(levelNodes);
        if (nameText == null || nameTranslationText == null || levelText == null) {
            return null;
        }

        String nameKey = TooltipTemplateRuntime.extractTemplateKeyForLine(nameTranslationText, useTagStylePreservation);
        if (nameKey == null || nameKey.isBlank()) {
            return null;
        }

        return new EnchantListEntry(
                toText(leadingNodes),
                nameText,
                nameTranslationText,
                toText(bridgeNodes),
                levelText,
                toText(trailingNodes),
                toText(delimiterNodes),
                nameKey
        );
    }

    private static boolean isErrorTranslation(Text text) {
        return text != null && text.getString().startsWith("Error: ");
    }

    private static String buildDebugSummary(StructuredLineMatch structuredLine) {
        if (structuredLine == null) {
            return "";
        }
        String prefix = truncateForLog(textString(structuredLine.prefixText()), 48);
        return "pattern=" + structuredLine.kind().debugName()
                + ", translateValue=" + structuredLine.kind().translateValue()
                + (prefix.isEmpty() ? "" : ", prefix=\"" + prefix + "\"")
                + ", label=\"" + truncateForLog(textString(structuredLine.labelText()), 96) + "\""
                + ", value=\"" + truncateForLog(textString(structuredLine.valueText()), 96) + "\""
                + ", labelKey=\"" + truncateForLog(structuredLine.labelKey(), 140) + "\""
                + (structuredLine.valueKey() == null ? "" : ", valueKey=\"" + truncateForLog(structuredLine.valueKey(), 140) + "\"");
    }

    private static String buildDetailedDebugSummary(StructuredLineMatch structuredLine) {
        if (structuredLine == null) {
            return "";
        }

        StringBuilder detail = new StringBuilder(buildDebugSummary(structuredLine));
        detail.append(", labelLookup=\"")
                .append(truncateForLog(
                        TooltipTemplateRuntime.describeLocalDictionaryLookup(structuredLine.labelTranslationText()),
                        180
                ))
                .append('"');
        if (structuredLine.kind().translateValue()) {
            detail.append(", valueLookup=\"")
                    .append(truncateForLog(
                            TooltipTemplateRuntime.describeLocalDictionaryLookup(structuredLine.valueTranslationText()),
                            180
                    ))
                    .append('"');
        }
        return detail.toString();
    }

    private static String buildDebugSummary(EnchantListMatch enchantListMatch) {
        if (enchantListMatch == null || enchantListMatch.entries() == null || enchantListMatch.entries().isEmpty()) {
            return "";
        }

        List<String> names = new ArrayList<>();
        for (EnchantListEntry entry : enchantListMatch.entries()) {
            names.add(truncateForLog(textString(entry.nameText()), 48));
        }
        return "pattern=enchant-list"
                + ", entries=" + enchantListMatch.entries().size()
                + ", names=\"" + truncateForLog(String.join(" | ", names), 220) + "\"";
    }

    private static Predicate<FlatNode> nodePredicate(Predicate<FlatNode> predicate) {
        return predicate;
    }

    private static Text toText(List<FlatNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        MutableText text = Text.empty();
        for (FlatNode node : nodes) {
            text.append(node.toText());
        }
        return text;
    }

    private static Text toCompactedText(List<FlatNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return toText(FlatNode.compact(nodes));
    }

    private static List<FlatNode> trimWhitespaceNodes(List<FlatNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        int start = 0;
        int end = nodes.size();
        while (start < end && isWhitespaceOnlySegment(nodes.get(start))) {
            start++;
        }
        while (end > start && isWhitespaceOnlySegment(nodes.get(end - 1))) {
            end--;
        }
        if (start >= end) {
            return List.of();
        }
        return List.copyOf(nodes.subList(start, end));
    }

    private static List<FlatNode> splitStructuredNodes(Text line) {
        if (line == null) {
            return List.of();
        }
        return splitInlineNodes(line);
    }

    private static String textString(Text text) {
        return text == null ? "" : text.getString();
    }

    private static String truncateForLog(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String normalizeLabel(String label) {
        return normalizeSpaces(label).trim();
    }

    private static String normalizeValue(String value) {
        return normalizeSpaces(value).trim();
    }

    private static String normalizeSpaces(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isCooldownLikeLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.equals("cooldown")
                || lower.endsWith(" cooldown")
                || lower.endsWith(" cost")
                || lower.endsWith(" time");
    }

    private static boolean isPriceLikeLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains("price")
                || lower.endsWith(" value")
                || lower.contains("sell value")
                || lower.contains("item value");
    }

    private static boolean isSelectedLikeLabel(String label) {
        return label.toLowerCase(Locale.ROOT).startsWith("selected ");
    }

    private static boolean isMuseumLikeLabel(String label) {
        return label.toLowerCase(Locale.ROOT).startsWith("museum");
    }

    private static boolean isListLikeLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.equals("attracts")
                || lower.endsWith(" attracts");
    }

    private static boolean isStructuredNumericLikeValue(String value) {
        if (value == null || value.isBlank() || containsLetter(value)) {
            return false;
        }

        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                sawNonWhitespace = true;
            }
            if (!(Character.isWhitespace(codePoint)
                    || Character.isDigit(codePoint)
                    || isStructuredValueSymbol(codePoint)
                    || isPrivateUseCodePoint(codePoint))) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return sawNonWhitespace;
    }

    private static boolean isTimedUnitLikeValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.matches("(?i)[+-]?\\d+(?:\\.\\d+)?\\s*[a-z]{1,6}")
                || normalized.matches("(?i)[+-]?\\d+(?:\\.\\d+)?%")
                || normalized.matches("(?i)[+-]?\\d+(?:\\.\\d+)?\\s*x");
    }

    private static boolean isGenericMeasuredValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || !containsDigit(normalized) || !containsLetter(normalized)) {
            return false;
        }

        return normalized.matches("(?iu)[+-]?(?:\\d+(?:[.,/-]\\d+)*|\\.\\d+)\\s*[\\p{L}]{1,8}")
                || normalized.matches("(?iu)[+-]?(?:\\d+(?:[.,/-]\\d+)*|\\.\\d+)(?:\\s+[\\p{L}][\\p{L}'-]{0,15}){1,3}");
    }

    private static boolean isCoinsLikeValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || !containsDigit(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.endsWith("coins")
                || lower.endsWith("coin")
                || lower.contains(" coins");
    }

    private static boolean isWordPhraseValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || !containsLetter(normalized) || containsDigit(normalized)) {
            return false;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (!(Character.isLetter(codePoint)
                    || Character.isWhitespace(codePoint)
                    || codePoint == '\''
                    || codePoint == '-'
                    || codePoint == '&'
                    || codePoint == '/'
                    || codePoint == '('
                    || codePoint == ')')) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isWordListValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || !containsLetter(normalized)) {
            return false;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (!(Character.isLetter(codePoint)
                    || Character.isWhitespace(codePoint)
                    || codePoint == ','
                    || codePoint == '\''
                    || codePoint == '-'
                    || codePoint == '&'
                    || isPrivateUseCodePoint(codePoint))) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return normalized.indexOf(',') >= 0 || normalized.indexOf(' ') >= 0;
    }

    private static boolean isDonatedStatusValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("donated")
                || lower.contains("not donated")
                || lower.contains("donate");
    }

    private static boolean containsLetter(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsDigit(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isDigit(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isCommaSegment(FlatNode node) {
        return ",".equals(node == null ? "" : node.extractString());
    }

    private static boolean isEnchantLevelSegment(FlatNode node) {
        String value = normalizeSpaces(node == null ? "" : node.extractString());
        return !value.isEmpty() && value.matches("\\d+");
    }

    private static boolean isLikelyEnchantName(List<FlatNode> nameNodes) {
        Text nameText = toText(nameNodes);
        String normalized = normalizeLabel(textString(nameText));
        if (normalized.isEmpty()
                || normalized.indexOf(':') >= 0
                || normalized.indexOf('：') >= 0
                || !containsLetter(normalized)) {
            return false;
        }

        String[] tokens = normalized.split("\\s+");
        if (tokens.length == 0 || tokens.length > 4) {
            return false;
        }

        for (String token : tokens) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            for (int offset = 0; offset < trimmed.length(); ) {
                int codePoint = trimmed.codePointAt(offset);
                if (!(Character.isLetter(codePoint)
                        || codePoint == '\''
                        || codePoint == '-'
                        || codePoint == '&'
                        || codePoint == '/')) {
                    return false;
                }
                offset += Character.charCount(codePoint);
            }
        }
        return true;
    }

    private static List<FlatNode> splitInlineNodes(Text line) {
        List<FlatNode> compactNodes = FlatNode.compact(FlatNode.flatten(line));
        List<FlatNode> splitNodes = new ArrayList<>();
        for (FlatNode node : compactNodes) {
            TextContent content = node.content();
            if (!(content instanceof PlainTextContent plainTextContent)) {
                splitNodes.add(node);
                continue;
            }

            String text = plainTextContent.string();
            if (text == null || text.isEmpty()) {
                continue;
            }

            StringBuilder segment = new StringBuilder();
            InlineSegmentType currentType = null;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                InlineSegmentType nextType = classifyInlineSegmentType(codePoint);
                if (currentType != null && currentType != nextType && segment.length() > 0) {
                    splitNodes.add(new FlatNode(PlainTextContent.of(segment.toString()), node.style()));
                    segment.setLength(0);
                }
                currentType = nextType;
                segment.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
            }

            if (segment.length() > 0) {
                splitNodes.add(new FlatNode(PlainTextContent.of(segment.toString()), node.style()));
            }
        }
        return splitNodes;
    }

    private static InlineSegmentType classifyInlineSegmentType(int codePoint) {
        if (codePoint > 0xFFFF || isPrivateUseCodePoint(codePoint)) {
            return InlineSegmentType.OTHER;
        }
        if (codePoint == ':' || codePoint == '：') {
            return InlineSegmentType.COLON;
        }
        if (Character.isWhitespace(codePoint)) {
            return InlineSegmentType.WHITESPACE;
        }
        if (Character.isLetter(codePoint)) {
            return InlineSegmentType.LETTER;
        }
        if (Character.isDigit(codePoint)) {
            return InlineSegmentType.DIGIT;
        }
        return InlineSegmentType.OTHER;
    }

    private static boolean isStructuredColonText(String value) {
        return ":".equals(value) || "：".equals(value);
    }

    private static boolean isStructuredPrefixSegment(FlatNode node) {
        return isWhitespaceOnlySegment(node) || isDecorativePrefixSegment(node);
    }

    private static boolean isStructuredLabelSegment(FlatNode node) {
        String value = node == null ? "" : node.extractString();
        if (value.isEmpty()) {
            return false;
        }
        if (isWhitespaceOnlySegment(node)) {
            return true;
        }
        if (containsLetter(value)) {
            return true;
        }
        return isAllowedLabelPunctuation(value);
    }

    private static boolean isDecorativePrefixSegment(FlatNode node) {
        String value = node == null ? "" : node.extractString();
        if (value.isEmpty()) {
            return false;
        }

        boolean sawDecorative = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            if (isPrivateUseCodePoint(codePoint) || isDecorativePrefixCodePoint(codePoint)) {
                sawDecorative = true;
                offset += Character.charCount(codePoint);
                continue;
            }
            if (Character.isLetterOrDigit(codePoint) || codePoint == ':' || codePoint == '：') {
                return false;
            }
            return false;
        }
        return sawDecorative;
    }

    private static boolean isDecorativeStructuredSeparatorSegment(FlatNode node) {
        String value = node == null ? "" : node.extractString();
        if (value.isEmpty()) {
            return false;
        }

        boolean sawDecorative = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            if (isPrivateUseCodePoint(codePoint) || isDecorativePrefixCodePoint(codePoint)) {
                sawDecorative = true;
                offset += Character.charCount(codePoint);
                continue;
            }
            if (Character.isLetterOrDigit(codePoint) || codePoint == ':' || codePoint == '：') {
                return false;
            }
            if (!isStructuredValueSymbol(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return sawDecorative;
    }

    private static boolean isWhitespaceOnlySegment(FlatNode node) {
        String value = node == null ? "" : node.extractString();
        if (value.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isDecorativePrefixCodePoint(int codePoint) {
        if (codePoint <= 0x7F) {
            return false;
        }

        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isStructuredGenericValueSegment(FlatNode node) {
        String value = node == null ? "" : node.extractString();
        if (value.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (codePoint == ':' || codePoint == '：') {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isAllowedLabelPunctuation(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!(codePoint == '('
                    || codePoint == ')'
                    || codePoint == '.'
                    || codePoint == '\''
                    || codePoint == '-'
                    || codePoint == '/'
                    || codePoint == '&')) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isStructuredValueSymbol(int codePoint) {
        if (codePoint == ':' || codePoint == '：') {
            return false;
        }
        if (codePoint == '+' || codePoint == '-' || codePoint == '%' || codePoint == '.' || codePoint == ','
                || codePoint == '(' || codePoint == ')' || codePoint == '[' || codePoint == ']'
                || codePoint == '/' || codePoint == '\\' || codePoint == '*' || codePoint == 'x'
                || codePoint == 'X') {
            return true;
        }

        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isPrivateUseCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED
                || codePoint > 0xFFFF
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFF);
    }

    private enum InlineSegmentType {
        COLON,
        WHITESPACE,
        LETTER,
        DIGIT,
        OTHER
    }

    private enum StructuredLineKind {
        NUMERIC("structured-numeric", false),
        TIMED_VALUE("structured-timed", true),
        PRICE_VALUE("structured-price", true),
        MEASURED_VALUE("structured-measured", true),
        WORD_VALUE("structured-word-value", true),
        SELECTED_VALUE("structured-selected", true),
        DONATED_STATUS("structured-donated", true),
        WORD_LIST("structured-list", true);

        private final String debugName;
        private final boolean translateValue;

        StructuredLineKind(String debugName, boolean translateValue) {
            this.debugName = debugName;
            this.translateValue = translateValue;
        }

        public String debugName() {
            return debugName;
        }

        public boolean translateValue() {
            return translateValue;
        }
    }

    record StructuredTooltipLineResult(
            TooltipTranslationSupport.TooltipLineResult lineResult,
            Set<String> translationTemplateKeys,
            String debugSummary
    ) {
        StructuredTooltipLineResult {
            translationTemplateKeys = translationTemplateKeys == null
                    ? Set.of()
                    : new LinkedHashSet<>(translationTemplateKeys);
            debugSummary = debugSummary == null ? "" : debugSummary;
        }
    }

    private record StructuredLineMatch(
            StructuredLineKind kind,
            Text prefixText,
            Text labelText,
            Text labelTranslationText,
            Text colonText,
            Text spacingText,
            Text valueText,
            Text valueTranslationText,
            String labelKey,
            String valueKey
    ) {
        private Set<String> translationTemplateKeys() {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            if (labelKey != null && !labelKey.isBlank()) {
                keys.add(labelKey);
            }
            if (valueKey != null && !valueKey.isBlank()) {
                keys.add(valueKey);
            }
            return keys;
        }
    }

    private record EnchantListMatch(List<EnchantListEntry> entries) {
        private Set<String> translationTemplateKeys() {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            if (entries == null) {
                return keys;
            }

            for (EnchantListEntry entry : entries) {
                if (entry == null || entry.nameKey() == null || entry.nameKey().isBlank()) {
                    continue;
                }
                keys.add(entry.nameKey());
            }
            return keys;
        }
    }

    private record EnchantListEntry(
            Text leadingText,
            Text nameText,
            Text nameTranslationText,
            Text bridgeText,
            Text levelText,
            Text trailingText,
            Text delimiterText,
            String nameKey
    ) {
    }
}
