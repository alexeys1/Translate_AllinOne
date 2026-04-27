package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipTextDebugCopySupport {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String ORIGINAL_LINE_PREFIX = "original：";
    private static final Pattern LEGACY_FORMATTING_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern SIMPLE_TAG_PATTERN = Pattern.compile("</?[A-Za-z0-9]+>");
    private static final Pattern BRACE_TOKEN_PATTERN = Pattern.compile("\\{[A-Za-z0-9]+}");
    private static final Pattern DYNAMIC_TAIL_START_PATTERN = Pattern.compile("^[\\s+%\\-()\\[\\]]*\\{[A-Za-z0-9]+}");
    private static final Pattern ACTUAL_DYNAMIC_TOKEN_PATTERN = Pattern.compile("[+\\-\\u2212\\u00B1]?\\d+(?:[.,]\\d+)?");
    private static final Pattern ACTUAL_DYNAMIC_TAIL_START_PATTERN = Pattern.compile("^[\\s+%\\-()\\[\\]（）【】]*[+\\-\\u2212\\u00B1]?\\d");
    private static final Pattern FORMAT_PLACEHOLDER_PATTERN = Pattern.compile("%(?:\\d+\\$)?[sdfSDF]");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern EMPTY_DYNAMIC_GROUP_PATTERN = Pattern.compile("\\(\\s*[+%\\-]*\\s*\\)");
    private static final Pattern LEADING_LIST_MARKER_PATTERN = Pattern.compile("^[\\s•*\\-–—●▪◦‣]+");
    private static final Pattern LEADING_TEMPLATE_DYNAMIC_PATTERN = Pattern.compile("^(?:§.)*[\\s+%\\-\\u2212\\u00B1()\\[\\]（）【】]*\\{[A-Za-z0-9]+}\\s*%?");
    private static final Pattern DYNAMIC_VALUE_CONNECTOR_END_PATTERN = Pattern.compile("(?i)(?:^|.*\\s)(?:by|for|from|in|into|of|on|per|than|to|up|with|without)$");
    private static final Set<String> DYNAMIC_VALUE_UNITS = Set.of(
            "block",
            "blocks",
            "coin",
            "coins",
            "hour",
            "hours",
            "level",
            "levels",
            "minute",
            "minutes",
            "second",
            "seconds",
            "xp"
    );
    private static final Pattern TRAILING_UNIT_VALUE_PATTERN = Pattern.compile("\\s*[:：]\\s*(?:coins?|blocks?|seconds?|levels?|xp)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_COLON_PATTERN = Pattern.compile("\\s*[:：]+\\s*$");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static boolean copyShortcutWasDown;
    private static boolean copySuccessMessageWasSent;
    private static CopyCandidate lastCopiedCandidate;
    private static String lastCopiedClipboardText = "";

    private TooltipTextDebugCopySupport() {
    }

    public static boolean isEnabled() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            return config != null
                    && config.dictionary != null
                    && config.dictionary.isTextDebugEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void maybeCopyCurrentTooltip(List<Text> tooltipLines) {
        maybeCopyEntries(collectDictionaryEntries(tooltipLines), "text.translate_allinone.tooltip_debug.copied");
    }

    public static void maybeCopyTextEntries(List<TextDebugEntry> entries, String copiedMessageKey) {
        maybeCopyEntries(entries, copiedMessageKey);
    }

    public static void tick(MinecraftClient client) {
        if (!isEnabled() || !isCopyShortcutDown(client)) {
            resetCopyShortcutState();
        }
    }

    private static boolean isCopyShortcutDown(MinecraftClient client) {
        if (client == null || client.getWindow() == null || client.keyboard == null) {
            return false;
        }

        try {
            boolean controlDown = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            return controlDown && InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_C);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void maybeCopyEntries(List<TextDebugEntry> entries, String copiedMessageKey) {
        if (!isEnabled()) {
            resetCopyShortcutState();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean shortcutDown = isCopyShortcutDown(client);
        if (!shortcutDown) {
            resetCopyShortcutState();
            return;
        }
        if (entries == null || entries.isEmpty()) {
            return;
        }

        String clipboardText = buildDictionaryJsonTemplate(entries);
        CopyCandidate candidate = CopyCandidate.from(entries);
        if (copyShortcutWasDown && !shouldReplaceCopiedCandidate(candidate, clipboardText)) {
            return;
        }

        copyShortcutWasDown = true;
        lastCopiedCandidate = candidate;
        lastCopiedClipboardText = clipboardText;
        client.keyboard.setClipboard(clipboardText);
        if (!copySuccessMessageWasSent && client.player != null) {
            copySuccessMessageWasSent = true;
            String messageKey = copiedMessageKey == null || copiedMessageKey.isBlank()
                    ? "text.translate_allinone.tooltip_debug.copied"
                    : copiedMessageKey;
            client.player.sendMessage(Text.translatable(messageKey).formatted(Formatting.GOLD), false);
        }
    }

    private static void resetCopyShortcutState() {
        copyShortcutWasDown = false;
        copySuccessMessageWasSent = false;
        lastCopiedCandidate = null;
        lastCopiedClipboardText = "";
    }

    private static boolean shouldReplaceCopiedCandidate(CopyCandidate candidate, String clipboardText) {
        if (candidate == null || clipboardText == null || clipboardText.equals(lastCopiedClipboardText)) {
            return false;
        }
        if (lastCopiedCandidate == null) {
            return true;
        }
        if (candidate.entryKeys().containsAll(lastCopiedCandidate.entryKeys())
                && candidate.entryKeys().size() > lastCopiedCandidate.entryKeys().size()) {
            return true;
        }
        if (candidate.normalKeys().containsAll(lastCopiedCandidate.normalKeys())
                && candidate.normalKeys().size() > lastCopiedCandidate.normalKeys().size()) {
            return true;
        }
        if (candidate.normalKeys().size() > lastCopiedCandidate.normalKeys().size()
                && candidate.meaningfulTextScore() >= lastCopiedCandidate.meaningfulTextScore()) {
            return true;
        }
        return candidate.entryKeys().size() > lastCopiedCandidate.entryKeys().size()
                && candidate.meaningfulTextScore() > lastCopiedCandidate.meaningfulTextScore();
    }

    private static List<TextDebugEntry> collectDictionaryEntries(List<Text> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return List.of();
        }

        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltipLines);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return List.of();
        }

        Set<String> sourceKeys = new LinkedHashSet<>();
        List<TextDebugEntry> entries = new ArrayList<>();
        ItemTranslateConfig itemConfig = currentItemTranslateConfig();
        if (itemConfig != null) {
            TooltipRoutePlanner.TooltipPlan tooltipPlan = TooltipRoutePlanner.planTooltip(
                    sanitizedTooltip,
                    itemConfig,
                    TooltipDecorativeContextSupport.isDecorativeTooltipContext(sanitizedTooltip)
            );
            if (tooltipPlan != null && tooltipPlan.segments() != null && !tooltipPlan.segments().isEmpty()) {
                for (TooltipRoutePlanner.TooltipRouteSegment segment : tooltipPlan.segments()) {
                    addRouteSegmentEntries(entries, sourceKeys, segment);
                }
                return entries;
            }
        }

        for (Text line : sanitizedTooltip) {
            if (line == null || TooltipInternalLineSupport.isInternalGeneratedLine(line)) {
                continue;
            }

            addFallbackTextDebugEntries(entries, sourceKeys, line);
        }
        return entries;
    }

    private static void addRouteSegmentEntries(
            List<TextDebugEntry> entries,
            Set<String> sourceKeys,
            TooltipRoutePlanner.TooltipRouteSegment segment
    ) {
        if (segment == null) {
            return;
        }

        if (segment.kind() == TooltipRoutePlanner.TooltipRouteKind.PARAGRAPH_BLOCK) {
            addParagraphBlockEntries(entries, sourceKeys, segment.paragraphBlock());
            return;
        }

        if (segment.kind() == TooltipRoutePlanner.TooltipRouteKind.STRUCTURED_LINE
                && segment.candidate() != null
                && segment.candidate().line() != null) {
            List<TooltipStructuredCaptureSupport.StructuredTextDebugSegment> structuredSegments =
                    TooltipStructuredCaptureSupport.collectStructuredTextDebugSegments(segment.candidate().line(), false);
            if (!structuredSegments.isEmpty()) {
                for (TooltipStructuredCaptureSupport.StructuredTextDebugSegment structuredSegment : structuredSegments) {
                    addTextDebugSegmentEntries(entries, sourceKeys, structuredSegment.text());
                }
                return;
            }
        }

        if (segment.candidate() != null && segment.candidate().line() != null) {
            addFallbackTextDebugEntries(entries, sourceKeys, segment.candidate().line());
        }
    }

    private static void addParagraphBlockEntries(
            List<TextDebugEntry> entries,
            Set<String> sourceKeys,
            TooltipRoutePlanner.TooltipParagraphBlock paragraphBlock
    ) {
        if (paragraphBlock == null || paragraphBlock.preparedLines() == null || paragraphBlock.preparedLines().isEmpty()) {
            return;
        }

        String valueText = buildParagraphLegacyValueText(paragraphBlock.preparedLines());
        if (valueText.isBlank()) {
            return;
        }

        addDictionaryEntry(entries, sourceKeys, valueText);
        String originalKey = TooltipParagraphSupport.buildParagraphLocalDictionaryLookupSource(paragraphBlock);
        addDictionaryEntry(entries, sourceKeys, originalKey, valueText, ORIGINAL_LINE_PREFIX);
    }

    private static String buildParagraphLegacyValueText(
            List<TooltipTemplateRuntime.PreparedTooltipTemplate> preparedLines
    ) {
        StringBuilder builder = new StringBuilder();
        for (TooltipTemplateRuntime.PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }

            String lineValue = cleanDictionaryValueText(TooltipTemplateRuntime.buildLegacyCompatibilityKey(preparedLine.sourceLine()));
            if (lineValue.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(lineValue);
        }
        return builder.toString();
    }

    private static void addTextDebugSegmentEntries(List<TextDebugEntry> entries, Set<String> sourceKeys, Text text) {
        if (text == null) {
            return;
        }

        String valueText = cleanDictionaryValueText(TooltipTemplateRuntime.buildLegacyCompatibilityKey(text));
        if (valueText.isBlank()) {
            return;
        }

        addDictionaryEntry(entries, sourceKeys, valueText);
        String originalKey = cleanDictionaryOriginalKey(text.getString());
        addDictionaryEntry(entries, sourceKeys, originalKey, valueText, ORIGINAL_LINE_PREFIX);
    }

    private static void addFallbackTextDebugEntries(List<TextDebugEntry> entries, Set<String> sourceKeys, Text line) {
        String valueText = cleanDictionaryValueText(TooltipTemplateRuntime.buildLegacyCompatibilityKey(line));
        List<String> valueSegments = splitDictionaryValueSegments(valueText);
        List<String> originalKeySegments = splitDictionaryOriginalKeySegments(line.getString(), valueSegments);
        for (int i = 0; i < valueSegments.size(); i++) {
            String valueSegment = valueSegments.get(i);
            addDictionaryEntry(entries, sourceKeys, valueSegment);
            if (i < originalKeySegments.size()) {
                addDictionaryEntry(entries, sourceKeys, originalKeySegments.get(i), valueSegment, ORIGINAL_LINE_PREFIX);
            }
        }
    }

    private static ItemTranslateConfig currentItemTranslateConfig() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            return config == null ? null : config.itemTranslate;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void addDictionaryEntry(List<TextDebugEntry> entries, Set<String> sourceKeys, String valueText) {
        addDictionaryEntry(entries, sourceKeys, cleanDictionarySourceKey(valueText), valueText);
    }

    private static void addDictionaryEntry(
            List<TextDebugEntry> entries,
            Set<String> sourceKeys,
            String sourceKey,
            String valueText
    ) {
        addDictionaryEntry(entries, sourceKeys, sourceKey, valueText, "");
    }

    private static void addDictionaryEntry(
            List<TextDebugEntry> entries,
            Set<String> sourceKeys,
            String sourceKey,
            String valueText,
            String linePrefix
    ) {
        if (sourceKey != null
                && !sourceKey.isBlank()
                && valueText != null
                && !valueText.isBlank()
                && sourceKeys.add((linePrefix == null ? "" : linePrefix) + sourceKey)) {
            entries.add(new TextDebugEntry(sourceKey, valueText, linePrefix));
        }
    }

    private static List<String> splitDictionaryValueSegments(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return List.of();
        }

        int colonIndex = findSplitColonIndex(valueText);
        if (colonIndex < 0) {
            String cleanedValue = removeStandaloneTrailingDynamicValue(valueText);
            if (cleanedValue.isBlank()) {
                return List.of();
            }
            List<String> dynamicPrefixedSegments = splitDynamicPrefixedLabelSegments(cleanedValue);
            if (!dynamicPrefixedSegments.isEmpty()) {
                return dynamicPrefixedSegments;
            }
            List<String> styleSegments = splitDictionaryStyleRunSegments(cleanedValue);
            return styleSegments.size() > 1 ? styleSegments : List.of(cleanedValue);
        }

        String labelSegment = trimLegacySegment(valueText.substring(0, colonIndex));
        String tailSegment = trimLegacySegment(valueText.substring(colonIndex + 1));
        if (labelSegment.isBlank() || tailSegment.isBlank()) {
            return List.of(valueText);
        }

        String visibleTail = visibleTextForSplitDecision(tailSegment);
        if (startsWithDynamicTail(visibleTail)) {
            if (containsMeaningfulText(removeTemplatePlaceholders(visibleTail))) {
                return List.of(labelSegment, tailSegment);
            }
            return List.of(labelSegment);
        }

        if (startsWithNonTextMarker(visibleTail) && containsMeaningfulText(visibleTail)) {
            return List.of(labelSegment, tailSegment);
        }
        return List.of(valueText);
    }

    private static List<String> splitDictionaryOriginalKeySegments(String originalText, List<String> valueSegments) {
        List<String> segments = splitDictionaryOriginalKeySegments(originalText);
        if (segments.size() == (valueSegments == null ? 0 : valueSegments.size())) {
            return segments;
        }

        List<String> alignedSegments = splitOriginalKeyByValueSegments(cleanDictionaryOriginalKey(originalText), valueSegments);
        return alignedSegments.isEmpty() ? segments : alignedSegments;
    }

    private static List<String> splitDictionaryOriginalKeySegments(String originalText) {
        String originalKey = cleanDictionaryOriginalKey(originalText);
        if (originalKey.isBlank()) {
            return List.of();
        }

        int colonIndex = findSplitColonIndex(originalKey);
        if (colonIndex < 0) {
            String cleanedKey = removeStandaloneTrailingOriginalDynamicValue(originalKey);
            return cleanedKey.isBlank() ? List.of() : List.of(cleanedKey);
        }

        String labelSegment = trimLegacySegment(originalKey.substring(0, colonIndex));
        String tailSegment = trimLegacySegment(originalKey.substring(colonIndex + 1));
        if (labelSegment.isBlank() || tailSegment.isBlank()) {
            return List.of(originalKey);
        }

        String visibleTail = visibleTextForSplitDecision(tailSegment);
        if (startsWithDynamicTail(visibleTail) || startsWithActualDynamicTail(visibleTail)) {
            if (containsMeaningfulText(removeTemplatePlaceholders(visibleTail))) {
                return List.of(labelSegment, tailSegment);
            }
            return List.of(labelSegment);
        }

        if (startsWithNonTextMarker(visibleTail) && containsMeaningfulText(visibleTail)) {
            return List.of(labelSegment, tailSegment);
        }
        return List.of(originalKey);
    }

    private static List<String> splitDictionaryStyleRunSegments(String valueText) {
        List<String> segments = splitLegacyStyleRuns(valueText);
        if (segments.size() < 2 || !allSegmentsHaveLocalDictionaryHits(segments)) {
            return List.of();
        }
        return segments;
    }

    private static List<String> splitDynamicPrefixedLabelSegments(String valueText) {
        List<String> legacyRuns = splitLegacyRunsPreservingDynamic(valueText);
        if (legacyRuns.isEmpty()) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        boolean sawDynamicPrefix = false;
        for (String legacyRun : legacyRuns) {
            DynamicPrefixRemoval removal = removeLeadingTemplateDynamicPrefix(legacyRun);
            if (removal.removed()) {
                sawDynamicPrefix = true;
            }
            addDynamicPrefixedLabelSegment(segments, removal.text());
        }

        if (!sawDynamicPrefix || segments.isEmpty()) {
            return List.of();
        }
        return segments;
    }

    private static List<String> splitLegacyRunsPreservingDynamic(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return List.of();
        }

        List<String> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder(valueText.length());
        for (int i = 0; i < valueText.length(); ) {
            char currentChar = valueText.charAt(i);
            if (currentChar == '§' && i + 1 < valueText.length()) {
                addLegacyRun(runs, current.toString());
                current.setLength(0);
                current.append(currentChar).append(valueText.charAt(i + 1));
                i += 2;
                continue;
            }

            int codePoint = valueText.codePointAt(i);
            current.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        addLegacyRun(runs, current.toString());
        return runs;
    }

    private static void addLegacyRun(List<String> runs, String run) {
        String cleaned = trimLegacySegment(run);
        if (!cleaned.isBlank()) {
            runs.add(cleaned);
        }
    }

    private static DynamicPrefixRemoval removeLeadingTemplateDynamicPrefix(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return new DynamicPrefixRemoval("", false);
        }

        Matcher matcher = LEADING_TEMPLATE_DYNAMIC_PATTERN.matcher(valueText);
        if (!matcher.find()) {
            return new DynamicPrefixRemoval(valueText, false);
        }

        String cleaned = trimLegacySegment(leadingLegacyFormattingCodes(valueText) + valueText.substring(matcher.end()));
        return new DynamicPrefixRemoval(cleaned, true);
    }

    private static String leadingLegacyFormattingCodes(String valueText) {
        if (valueText == null || valueText.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index + 1 < valueText.length() && valueText.charAt(index) == '§') {
            builder.append(valueText, index, index + 2);
            index += 2;
        }
        return builder.toString();
    }

    private static void addDynamicPrefixedLabelSegment(List<String> segments, String segment) {
        String cleaned = trimLegacySegment(segment);
        String visible = removeTemplatePlaceholders(visibleTextForSplitDecision(cleaned));
        if (!cleaned.isBlank()
                && containsMeaningfulText(visible)
                && !isDynamicValueUnitOnly(visible)) {
            segments.add(cleaned);
        }
    }

    private static boolean isDynamicValueUnitOnly(String visibleText) {
        if (visibleText == null || visibleText.isBlank()) {
            return false;
        }
        return DYNAMIC_VALUE_UNITS.contains(visibleText.trim().toLowerCase(Locale.ROOT));
    }

    private static List<String> splitLegacyStyleRuns(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder(valueText.length());
        for (int i = 0; i < valueText.length(); ) {
            char currentChar = valueText.charAt(i);
            if (currentChar == '§' && i + 1 < valueText.length()) {
                if (containsMeaningfulText(removeTemplatePlaceholders(visibleTextForSplitDecision(current.toString())))) {
                    addLegacyStyleRunSegment(segments, current.toString());
                    current.setLength(0);
                }
                current.append(currentChar).append(valueText.charAt(i + 1));
                i += 2;
                continue;
            }

            int codePoint = valueText.codePointAt(i);
            current.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        addLegacyStyleRunSegment(segments, current.toString());
        return segments;
    }

    private static void addLegacyStyleRunSegment(List<String> segments, String segment) {
        String cleaned = trimLegacySegment(segment);
        if (!cleaned.isBlank()
                && containsMeaningfulText(removeTemplatePlaceholders(visibleTextForSplitDecision(cleaned)))) {
            segments.add(cleaned);
        }
    }

    private static boolean allSegmentsHaveLocalDictionaryHits(List<String> segments) {
        for (String segment : segments) {
            String lookupText = cleanDictionaryOriginalKey(visibleTextForSplitDecision(segment));
            if (lookupText.isBlank()) {
                return false;
            }

            WynnSharedDictionaryService.LookupResult lookupResult =
                    WynnSharedDictionaryService.getInstance().lookupItemLine(lookupText);
            if (lookupResult == null || !lookupResult.hit()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitOriginalKeyByValueSegments(String originalKey, List<String> valueSegments) {
        if (originalKey == null || originalKey.isBlank() || valueSegments == null || valueSegments.size() < 2) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        int searchStart = 0;
        for (String valueSegment : valueSegments) {
            String visibleSegment = cleanDictionaryOriginalKey(visibleTextForSplitDecision(valueSegment));
            if (visibleSegment.isBlank()) {
                return List.of();
            }

            int start = indexOfIgnoreCase(originalKey, visibleSegment, searchStart);
            if (start < 0) {
                return List.of();
            }

            segments.add(originalKey.substring(start, start + visibleSegment.length()).trim());
            searchStart = start + visibleSegment.length();
        }
        return segments;
    }

    private static int indexOfIgnoreCase(String sourceText, String targetText, int fromIndex) {
        if (sourceText == null || targetText == null || targetText.isEmpty()) {
            return -1;
        }

        int maxIndex = sourceText.length() - targetText.length();
        for (int i = Math.max(0, fromIndex); i <= maxIndex; i++) {
            if (sourceText.regionMatches(true, i, targetText, 0, targetText.length())) {
                return i;
            }
        }
        return -1;
    }

    private static int findSplitColonIndex(String valueText) {
        for (int i = 0; i < valueText.length(); i++) {
            char current = valueText.charAt(i);
            if (current == ':' || current == '：') {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithDynamicTail(String visibleText) {
        return visibleText != null && DYNAMIC_TAIL_START_PATTERN.matcher(visibleText).find();
    }

    private static boolean startsWithActualDynamicTail(String visibleText) {
        return visibleText != null && ACTUAL_DYNAMIC_TAIL_START_PATTERN.matcher(visibleText).find();
    }

    private static boolean startsWithNonTextMarker(String visibleText) {
        int firstCodePoint = firstVisibleCodePoint(visibleText);
        return firstCodePoint >= 0 && !Character.isLetterOrDigit(firstCodePoint);
    }

    private static String visibleTextForSplitDecision(String sourceText) {
        String cleaned = normalizeDictionaryDebugText(sourceText);
        cleaned = LEGACY_FORMATTING_CODE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("§", "");
        return WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private static String removeTemplatePlaceholders(String sourceText) {
        String cleaned = BRACE_TOKEN_PATTERN.matcher(sourceText).replaceAll(" ");
        cleaned = FORMAT_PLACEHOLDER_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replace('/', ' ');
        cleaned = DIGIT_PATTERN.matcher(cleaned).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private static String removeStandaloneTrailingDynamicValue(String valueText) {
        String current = valueText;
        while (true) {
            TrailingDynamicToken trailingToken = findTrailingDynamicToken(current, BRACE_TOKEN_PATTERN);
            if (trailingToken == null) {
                return current;
            }

            String suffix = visibleTextForSplitDecision(current.substring(trailingToken.end()));
            if (containsMeaningfulText(removeTemplatePlaceholders(suffix))) {
                return current;
            }

            int removalStart = findTrailingDynamicRemovalStart(current, trailingToken.start());
            String prefix = current.substring(0, removalStart);
            String visiblePrefix = visibleTextForSplitDecision(prefix);
            String searchablePrefix = removeTemplatePlaceholders(visiblePrefix);
            if (!containsMeaningfulText(searchablePrefix) || DYNAMIC_VALUE_CONNECTOR_END_PATTERN.matcher(searchablePrefix).matches()) {
                return current;
            }

            String trimmedPrefix = trimTrailingLegacySegment(prefix);
            if (trimmedPrefix.equals(current)) {
                return current;
            }
            current = trimmedPrefix;
        }
    }

    private static String removeStandaloneTrailingOriginalDynamicValue(String valueText) {
        String current = valueText;
        while (true) {
            TrailingDynamicToken trailingToken = findTrailingDynamicToken(current, ACTUAL_DYNAMIC_TOKEN_PATTERN);
            if (trailingToken == null) {
                return current;
            }

            String suffix = visibleTextForSplitDecision(current.substring(trailingToken.end()));
            if (containsMeaningfulText(removeTemplatePlaceholders(suffix))) {
                return current;
            }

            int removalStart = findTrailingDynamicRemovalStart(current, trailingToken.start());
            String prefix = current.substring(0, removalStart);
            String visiblePrefix = visibleTextForSplitDecision(prefix);
            String searchablePrefix = removeTemplatePlaceholders(visiblePrefix);
            if (!containsMeaningfulText(searchablePrefix) || DYNAMIC_VALUE_CONNECTOR_END_PATTERN.matcher(searchablePrefix).matches()) {
                return current;
            }

            String trimmedPrefix = trimTrailingLegacySegment(prefix);
            if (trimmedPrefix.equals(current)) {
                return current;
            }
            current = trimmedPrefix;
        }
    }

    private static TrailingDynamicToken findTrailingDynamicToken(String sourceText, Pattern pattern) {
        if (pattern == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(sourceText);
        TrailingDynamicToken trailingToken = null;
        while (matcher.find()) {
            trailingToken = new TrailingDynamicToken(matcher.start(), matcher.end());
        }
        return trailingToken;
    }

    private static int findTrailingDynamicRemovalStart(String sourceText, int placeholderStart) {
        int index = placeholderStart;
        while (index > 0) {
            if (index >= 2 && sourceText.charAt(index - 2) == '§') {
                index -= 2;
                continue;
            }

            int codePoint = sourceText.codePointBefore(index);
            if (Character.isWhitespace(codePoint) || isDynamicTailPrefixCodePoint(codePoint)) {
                index -= Character.charCount(codePoint);
                continue;
            }
            break;
        }
        return index;
    }

    private static boolean isDynamicTailPrefixCodePoint(int codePoint) {
        return codePoint == '+'
                || codePoint == '-'
                || codePoint == '%'
                || codePoint == '('
                || codePoint == '['
                || codePoint == '（'
                || codePoint == '【';
    }

    private static boolean containsMeaningfulText(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }

        int letterRun = 0;
        for (int offset = 0; offset < sourceText.length(); ) {
            int codePoint = sourceText.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                if (Character.isIdeographic(codePoint)) {
                    return true;
                }
                letterRun++;
                if (letterRun >= 2) {
                    return true;
                }
            } else {
                letterRun = 0;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static int firstVisibleCodePoint(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return -1;
        }

        for (int offset = 0; offset < sourceText.length(); ) {
            int codePoint = sourceText.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return codePoint;
            }
            offset += Character.charCount(codePoint);
        }
        return -1;
    }

    private static String trimLegacySegment(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }

        String cleaned = WHITESPACE_PATTERN.matcher(sourceText).replaceAll(" ").trim();
        boolean removedVisibleSpace;
        do {
            removedVisibleSpace = false;
            int offset = 0;
            while (offset + 1 < cleaned.length() && cleaned.charAt(offset) == '§') {
                offset += 2;
            }
            if (offset < cleaned.length()) {
                int codePoint = cleaned.codePointAt(offset);
                if (Character.isWhitespace(codePoint)) {
                    cleaned = cleaned.substring(0, offset) + cleaned.substring(offset + Character.charCount(codePoint));
                    removedVisibleSpace = true;
                }
            }
        } while (removedVisibleSpace);
        return cleaned;
    }

    private static String trimTrailingLegacySegment(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }

        String cleaned = WHITESPACE_PATTERN.matcher(sourceText).replaceAll(" ").trim();
        boolean removedTrailingCode;
        do {
            removedTrailingCode = false;
            while (cleaned.endsWith(" ")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            while (cleaned.length() >= 2 && cleaned.charAt(cleaned.length() - 2) == '§') {
                cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
                removedTrailingCode = true;
            }
        } while (removedTrailingCode);
        return cleaned;
    }

    private static String cleanDictionarySourceKey(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }

        String cleaned = normalizeDictionaryDebugText(sourceText);
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replace('/', ' ');
        cleaned = LEGACY_FORMATTING_CODE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("§", "");
        cleaned = FORMAT_PLACEHOLDER_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = SIMPLE_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        PlaceholderProtection placeholderProtection = protectTemplatePlaceholders(cleaned);
        cleaned = placeholderProtection.text();
        cleaned = DIGIT_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = EMPTY_DYNAMIC_GROUP_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
        cleaned = restoreTemplatePlaceholders(cleaned, placeholderProtection.placeholders());
        cleaned = LEADING_LIST_MARKER_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_UNIT_VALUE_PATTERN.matcher(cleaned).replaceAll("");
        return TRAILING_COLON_PATTERN.matcher(cleaned).replaceAll("");
    }

    private static String cleanDictionaryOriginalKey(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }
        return TooltipTemplateRuntime.normalizeLocalDictionaryLookupSourceText(sourceText);
    }

    private static PlaceholderProtection protectTemplatePlaceholders(String sourceText) {
        Matcher matcher = BRACE_TOKEN_PATTERN.matcher(sourceText);
        StringBuilder protectedText = new StringBuilder(sourceText.length());
        List<ProtectedPlaceholder> placeholders = new ArrayList<>();
        int lastEnd = 0;
        int index = 0;
        while (matcher.find()) {
            protectedText.append(sourceText, lastEnd, matcher.start());
            String token = "__TAIO_PLACEHOLDER_" + alphabeticToken(index++) + "__";
            protectedText.append(token);
            placeholders.add(new ProtectedPlaceholder(token, matcher.group()));
            lastEnd = matcher.end();
        }
        protectedText.append(sourceText, lastEnd, sourceText.length());
        return new PlaceholderProtection(protectedText.toString(), placeholders);
    }

    private static String restoreTemplatePlaceholders(String sourceText, List<ProtectedPlaceholder> placeholders) {
        String restored = sourceText;
        for (ProtectedPlaceholder placeholder : placeholders) {
            restored = restored.replace(placeholder.token(), placeholder.value());
        }
        return restored;
    }

    private static String alphabeticToken(int index) {
        StringBuilder builder = new StringBuilder();
        int current = index;
        do {
            builder.append((char) ('A' + current % 26));
            current /= 26;
        } while (current > 0);
        return builder.toString();
    }

    private static String cleanDictionaryValueText(String sourceText) {
        return normalizeDictionaryDebugText(sourceText);
    }

    private static String normalizeDictionaryDebugText(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }

        String cleaned = TooltipTemplateRuntime.stripDecorativeGlyphsForHeuristics(sourceText)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        return WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private static String buildDictionaryJsonTemplate(List<TextDebugEntry> entries) {
        List<TextDebugEntry> normalEntries = new ArrayList<>();
        List<TextDebugEntry> originalEntries = new ArrayList<>();
        for (TextDebugEntry entry : entries) {
            if (entry.linePrefix().isBlank()) {
                normalEntries.add(entry);
            } else {
                originalEntries.add(entry);
            }
        }

        StringBuilder builder = new StringBuilder();
        appendDictionaryJsonEntries(builder, normalEntries, !originalEntries.isEmpty());
        if (!normalEntries.isEmpty() && !originalEntries.isEmpty()) {
            builder.append('\n');
        }
        appendDictionaryJsonEntries(builder, originalEntries, false);

        return builder.toString();
    }

    private static void appendDictionaryJsonEntries(
            StringBuilder builder,
            List<TextDebugEntry> entries,
            boolean trailingComma
    ) {
        for (int i = 0; i < entries.size(); i++) {
            TextDebugEntry entry = entries.get(i);
            builder.append(entry.linePrefix())
                    .append(GSON.toJson(entry.key()))
                    .append(": ")
                    .append(GSON.toJson(entry.value()));
            if (i < entries.size() - 1 || trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }
    }

    public record TextDebugEntry(String key, String value, String linePrefix) {
        public TextDebugEntry(String key, String value) {
            this(key, value, "");
        }

        public TextDebugEntry {
            key = key == null ? "" : key;
            value = value == null ? "" : value;
            linePrefix = linePrefix == null ? "" : linePrefix;
        }
    }

    private record PlaceholderProtection(String text, List<ProtectedPlaceholder> placeholders) {
    }

    private record ProtectedPlaceholder(String token, String value) {
    }

    private record TrailingDynamicToken(int start, int end) {
    }

    private record DynamicPrefixRemoval(String text, boolean removed) {
    }

    private record CopyCandidate(Set<String> entryKeys, Set<String> normalKeys, int meaningfulTextScore) {
        private static CopyCandidate from(List<TextDebugEntry> entries) {
            LinkedHashSet<String> entryKeys = new LinkedHashSet<>();
            LinkedHashSet<String> normalKeys = new LinkedHashSet<>();
            int meaningfulTextScore = 0;
            if (entries != null) {
                for (TextDebugEntry entry : entries) {
                    if (entry == null || entry.key().isBlank()) {
                        continue;
                    }
                    entryKeys.add(entry.linePrefix() + entry.key());
                    if (entry.linePrefix().isBlank()) {
                        normalKeys.add(entry.key());
                        meaningfulTextScore += countMeaningfulCodePoints(entry.key());
                    }
                }
            }
            return new CopyCandidate(Set.copyOf(entryKeys), Set.copyOf(normalKeys), meaningfulTextScore);
        }

        private static int countMeaningfulCodePoints(String text) {
            if (text == null || text.isBlank()) {
                return 0;
            }

            int count = 0;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                if (Character.isLetterOrDigit(codePoint)) {
                    count++;
                }
                offset += Character.charCount(codePoint);
            }
            return count;
        }
    }
}
