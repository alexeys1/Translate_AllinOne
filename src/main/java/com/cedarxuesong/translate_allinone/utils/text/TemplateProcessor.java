package com.cedarxuesong.translate_allinone.utils.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateProcessor {

    // This regex captures:
    // 1. Numbers with thousand separators (e.g., 1,000,000)
    // 2. Decimal numbers (e.g., 7.5, .5)
    // 3. Simple integers (e.g., 7, 121)
    // 4. Version numbers or dates (e.g., 1.2.3, 2025-07-05)
    // 5. Numbers immediately after legacy color prefix '§' are excluded.
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<!§)(\\d+([.,/-]\\d+)+|\\d{1,3}(,\\d{3})*(\\.\\d+)?|\\d+\\.\\d+|\\.\\d+|\\d+)");
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL);
    private static final Pattern STYLE_TAG_MARKER_PATTERN = Pattern.compile("</?s\\d+>");
    private static final Pattern GLYPH_PLACEHOLDER_PATTERN = Pattern.compile("\\{g(\\d+)}");
    private static final int WYNN_INLINE_SPACER_START = 0xCFF00;
    private static final int WYNN_INLINE_SPACER_END = 0xD0FFF;

    public record TemplateExtractionResult(String template, List<String> values) {
    }

    public record DecorativeGlyphExtractionResult(String template, List<String> values) {
    }

    /**
     * Extracts dynamic numerical values from a string and replaces them with placeholders.
     * This method assumes that style information is handled separately (e.g., by StylePreserver).
     *
     * @param text The input string, which may contain style markers but not raw color codes intended for this processor.
     * @return A result object containing the template and the list of extracted values.
     */
    public static TemplateExtractionResult extract(String text) {
        List<String> values = new ArrayList<>();
        Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        StringBuilder templateBuffer = new StringBuilder();

        while (numberMatcher.find()) {
            int matchStart = numberMatcher.start();
            int matchEnd = numberMatcher.end();

            if (isInsideNumericPlaceholder(text, matchStart, matchEnd)
                    || isInsideAngleBracketTag(text, matchStart, matchEnd)) {
                numberMatcher.appendReplacement(templateBuffer, Matcher.quoteReplacement(numberMatcher.group()));
                continue;
            }

            values.add(numberMatcher.group());
            String placeholder = "{d" + values.size() + "}";
            numberMatcher.appendReplacement(templateBuffer, Matcher.quoteReplacement(placeholder));
        }

        numberMatcher.appendTail(templateBuffer);
        return new TemplateExtractionResult(templateBuffer.toString(), values);
    }

    private static boolean isInsideNumericPlaceholder(String text, int start, int end) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int runStart = start;
        while (runStart > 0 && Character.isDigit(text.charAt(runStart - 1))) {
            runStart--;
        }

        int runEnd = end;
        while (runEnd < text.length() && Character.isDigit(text.charAt(runEnd))) {
            runEnd++;
        }

        if (runStart < 2 || runEnd >= text.length()) {
            return false;
        }

        return text.charAt(runStart - 2) == '{'
                && text.charAt(runStart - 1) == 'd'
                && text.charAt(runEnd) == '}';
    }

    private static boolean isInsideAngleBracketTag(String text, int start, int end) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int open = text.lastIndexOf('<', start);
        if (open < 0) {
            return false;
        }

        int close = text.indexOf('>', open);
        return close >= end;
    }

    public static String reassemble(String translatedTemplate, List<String> values) {
        // This method remains the same, as the AI should preserve the color codes.
        for (int i = 0; i < values.size(); i++) {
            String placeholder = "{d" + (i + 1) + "}";
            // Use quoteReplacement to handle any special characters in the replacement string.
            translatedTemplate = translatedTemplate.replaceFirst(Pattern.quote(placeholder), Matcher.quoteReplacement(values.get(i)));
        }
        return translatedTemplate;
    }

    public static DecorativeGlyphExtractionResult extractDecorativeGlyphTags(String text) {
        return extractDecorativeGlyphTags(text, styleId -> false);
    }

    public static DecorativeGlyphExtractionResult extractDecorativeGlyphTags(String text, IntPredicate preserveStyleId) {
        List<String> values = new ArrayList<>();
        Matcher matcher = STYLE_TAG_PATTERN.matcher(text);
        StringBuilder templateBuffer = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                templateBuffer.append(extractInlineSpacerGlyphRuns(
                        text.substring(lastEnd, matcher.start()),
                        null,
                        values
                ));
            }

            int styleId = Integer.parseInt(matcher.group(1));
            String taggedContent = matcher.group(2);
            if (!isDecorativeGlyphOnly(taggedContent)
                    && !(preserveStyleId != null
                    && preserveStyleId.test(styleId)
                    && ((isSymbolLikeOnly(taggedContent) && !isAsciiPlainPunctuationOnly(taggedContent))
                    || isLikelyDecorativeFontToken(taggedContent)))) {
                String normalizedContent = extractInlineSpacerGlyphRuns(taggedContent, styleId, values);
                if (!normalizedContent.isEmpty()) {
                    templateBuffer.append("<s")
                            .append(styleId)
                            .append(">")
                            .append(normalizedContent)
                            .append("</s")
                            .append(styleId)
                            .append(">");
                }
                lastEnd = matcher.end();
                continue;
            }

            String normalizedValue = normalizeDecorativeGlyphValue(matcher.group());
            if (normalizedValue == null || normalizedValue.isEmpty()) {
                lastEnd = matcher.end();
                continue;
            }

            values.add(normalizedValue);
            String placeholder = "{g" + values.size() + "}";
            templateBuffer.append(placeholder);
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            templateBuffer.append(extractInlineSpacerGlyphRuns(text.substring(lastEnd), null, values));
        }
        return new DecorativeGlyphExtractionResult(templateBuffer.toString(), values);
    }

    public static String reassembleDecorativeGlyphs(String translatedTemplate, List<String> values) {
        return reassembleDecorativeGlyphs(translatedTemplate, values, false);
    }

    public static String reassembleDecorativeGlyphs(
            String translatedTemplate,
            List<String> values,
            boolean collapsePureInlineSpacerDecorativeSegments
    ) {
        if (translatedTemplate == null || translatedTemplate.isEmpty() || values == null || values.isEmpty()) {
            return translatedTemplate;
        }

        String strippedTemplate = collapsePureInlineSpacerDecorativeSegments
                ? STYLE_TAG_MARKER_PATTERN.matcher(translatedTemplate).replaceAll("")
                : null;
        Matcher matcher = GLYPH_PLACEHOLDER_PATTERN.matcher(translatedTemplate);
        StringBuilder reassembled = new StringBuilder(translatedTemplate.length());
        int lastEnd = 0;

        while (matcher.find()) {
            reassembled.append(translatedTemplate, lastEnd, matcher.start());

            int placeholderIndex = Integer.parseInt(matcher.group(1)) - 1;
            String rawValue = placeholderIndex >= 0 && placeholderIndex < values.size()
                    ? values.get(placeholderIndex)
                    : matcher.group();
            String replacement = collapsePureInlineSpacerDecorativeSegments
                    ? normalizeDecorativeGlyphValueForTranslation(
                    rawValue,
                    strippedTemplate,
                    matcher.group(),
                    values
            )
                    : normalizeDecorativeGlyphValue(rawValue);
            reassembled.append(replacement);
            lastEnd = matcher.end();
        }

        reassembled.append(translatedTemplate, lastEnd, translatedTemplate.length());
        return reassembled.toString();
    }

    public static String normalizeWynnInlineSpacerGlyphsInTaggedText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = STYLE_TAG_PATTERN.matcher(text);
        StringBuilder normalized = new StringBuilder(text.length());
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                normalized.append(collapseWynnInlineSpacerGlyphs(text.substring(lastEnd, matcher.start())));
            }

            String content = collapseWynnInlineSpacerGlyphs(matcher.group(2));
            if (content != null && !content.isEmpty()) {
                normalized.append("<s")
                        .append(matcher.group(1))
                        .append(">")
                        .append(content)
                        .append("</s")
                        .append(matcher.group(1))
                        .append(">");
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            normalized.append(collapseWynnInlineSpacerGlyphs(text.substring(lastEnd)));
        }
        return normalized.toString();
    }

    private static String extractInlineSpacerGlyphRuns(String text, Integer styleId, List<String> values) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingSpacer = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isWynnInlineSpacerGlyphCodePoint(codePoint)) {
                pendingSpacer = true;
                continue;
            }

            if (pendingSpacer) {
                appendInlineSpacerPlaceholder(normalized, styleId, values);
                pendingSpacer = false;
            }
            normalized.appendCodePoint(codePoint);
        }

        if (pendingSpacer) {
            appendInlineSpacerPlaceholder(normalized, styleId, values);
        }
        return normalized.toString();
    }

    private static void appendInlineSpacerPlaceholder(StringBuilder templateBuffer, Integer styleId, List<String> values) {
        if (templateBuffer == null || values == null) {
            return;
        }

        values.add(styleId == null ? " " : "<s" + styleId + "> </s" + styleId + ">");
        templateBuffer.append("{g").append(values.size()).append("}");
    }

    public static String normalizeDecorativeGlyphValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Matcher matcher = STYLE_TAG_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return shouldPreserveDecorativeGlyphSegment(value) ? value : collapseWynnInlineSpacerGlyphs(value);
        }

        String content = matcher.group(2);
        String normalizedContent = shouldPreserveDecorativeGlyphSegment(content)
                ? content
                : collapseWynnInlineSpacerGlyphs(content);
        if (normalizedContent == null || normalizedContent.isEmpty()) {
            return "";
        }
        return "<s" + matcher.group(1) + ">" + normalizedContent + "</s" + matcher.group(1) + ">";
    }

    public static String collapseWynnInlineSpacerGlyphs(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingWhitespace = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isWynnInlineSpacerGlyphCodePoint(codePoint)) {
                pendingWhitespace = true;
                continue;
            }

            if (Character.isWhitespace(codePoint)) {
                pendingWhitespace = true;
                continue;
            }

            if (pendingWhitespace && (normalized.isEmpty() || normalized.charAt(normalized.length() - 1) != ' ')) {
                normalized.append(' ');
            }
            pendingWhitespace = false;
            normalized.appendCodePoint(codePoint);
        }

        if (pendingWhitespace && (normalized.isEmpty() || normalized.charAt(normalized.length() - 1) != ' ')) {
            normalized.append(' ');
        }
        return normalized.toString();
    }

    public static boolean shouldPreserveDecorativeGlyphSegment(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        boolean sawDecorativeGlyph = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (!isDecorativeGlyphCodePoint(codePoint)) {
                return false;
            }
            sawDecorativeGlyph = true;
        }

        return sawDecorativeGlyph;
    }

    private static String normalizeDecorativeGlyphValueForTranslation(
            String value,
            String strippedTemplate,
            String placeholder,
            List<String> values
    ) {
        if (!isPureInlineSpacerDecorativeValue(value)) {
            return normalizeDecorativeGlyphValue(value);
        }

        if (strippedTemplate != null
                && placeholder != null
                && isAttachedToVisibleDecorativePlaceholderCluster(strippedTemplate, placeholder, values)) {
            return normalizeDecorativeGlyphValue(value);
        }
        return collapseDecorativeValueToVisibleSpace(value);
    }

    private static boolean isAttachedToVisibleDecorativePlaceholderCluster(
            String strippedTemplate,
            String placeholder,
            List<String> values
    ) {
        if (strippedTemplate == null || strippedTemplate.isEmpty() || placeholder == null || placeholder.isEmpty()) {
            return false;
        }

        int start = strippedTemplate.indexOf(placeholder);
        if (start < 0) {
            return false;
        }
        int end = start + placeholder.length();
        return hasVisibleDecorativePlaceholderClusterToLeft(strippedTemplate, start, values)
                || hasVisibleDecorativePlaceholderClusterToRight(strippedTemplate, end, values);
    }

    private static boolean hasVisibleDecorativePlaceholderClusterToLeft(
            String template,
            int start,
            List<String> values
    ) {
        int cursor = start;
        while (cursor > 0 && Character.isWhitespace(template.charAt(cursor - 1))) {
            cursor--;
        }

        while (cursor > 0) {
            if (template.charAt(cursor - 1) != '}') {
                return false;
            }

            int braceStart = template.lastIndexOf('{', cursor - 1);
            if (braceStart < 0) {
                return false;
            }

            Integer placeholderIndex = parseGlyphPlaceholderIndex(template.substring(braceStart, cursor));
            if (placeholderIndex == null) {
                return false;
            }

            String neighborValue = values.get(placeholderIndex);
            if (hasVisibleDecorativeGlyphValue(neighborValue)) {
                return true;
            }
            if (!isPureInlineSpacerDecorativeValue(neighborValue)) {
                return false;
            }

            cursor = braceStart;
            while (cursor > 0 && Character.isWhitespace(template.charAt(cursor - 1))) {
                cursor--;
            }
        }

        return false;
    }

    private static boolean hasVisibleDecorativePlaceholderClusterToRight(
            String template,
            int end,
            List<String> values
    ) {
        int cursor = end;
        while (cursor < template.length() && Character.isWhitespace(template.charAt(cursor))) {
            cursor++;
        }

        while (cursor < template.length()) {
            if (template.charAt(cursor) != '{') {
                return false;
            }

            int braceEnd = template.indexOf('}', cursor);
            if (braceEnd < 0) {
                return false;
            }

            Integer placeholderIndex = parseGlyphPlaceholderIndex(template.substring(cursor, braceEnd + 1));
            if (placeholderIndex == null) {
                return false;
            }

            String neighborValue = values.get(placeholderIndex);
            if (hasVisibleDecorativeGlyphValue(neighborValue)) {
                return true;
            }
            if (!isPureInlineSpacerDecorativeValue(neighborValue)) {
                return false;
            }

            cursor = braceEnd + 1;
            while (cursor < template.length() && Character.isWhitespace(template.charAt(cursor))) {
                cursor++;
            }
        }

        return false;
    }

    private static Integer parseGlyphPlaceholderIndex(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        Matcher matcher = GLYPH_PLACEHOLDER_PATTERN.matcher(token);
        if (!matcher.matches()) {
            return null;
        }

        int parsedIndex = Integer.parseInt(matcher.group(1)) - 1;
        return parsedIndex >= 0 ? parsedIndex : null;
    }

    private static boolean hasVisibleDecorativeGlyphValue(String value) {
        String content = unwrapStyleTagContent(value);
        if (content == null || content.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (isDecorativeGlyphCodePoint(codePoint) && !isWynnInlineSpacerGlyphCodePoint(codePoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPureInlineSpacerDecorativeValue(String value) {
        String content = unwrapStyleTagContent(value);
        if (content == null || content.isEmpty()) {
            return false;
        }

        boolean sawSpacerGlyph = false;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (!isDecorativeGlyphCodePoint(codePoint) || !isWynnInlineSpacerGlyphCodePoint(codePoint)) {
                return false;
            }
            sawSpacerGlyph = true;
        }

        return sawSpacerGlyph;
    }

    private static String collapseDecorativeValueToVisibleSpace(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Matcher matcher = STYLE_TAG_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return collapseWynnInlineSpacerGlyphs(value);
        }

        String normalizedContent = collapseWynnInlineSpacerGlyphs(matcher.group(2));
        if (normalizedContent == null || normalizedContent.isEmpty()) {
            return "";
        }
        return "<s" + matcher.group(1) + ">" + normalizedContent + "</s" + matcher.group(1) + ">";
    }

    private static String unwrapStyleTagContent(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Matcher matcher = STYLE_TAG_PATTERN.matcher(value);
        return matcher.matches() ? matcher.group(2) : value;
    }

    private static boolean isDecorativeGlyphOnly(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (!isDecorativeGlyphCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }

        return true;
    }

    private static boolean isSymbolLikeOnly(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            sawNonWhitespace = true;
            if (Character.isLetterOrDigit(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }

        return sawNonWhitespace;
    }

    private static boolean isLikelyDecorativeFontToken(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (isAsciiPlainPunctuationOnly(text)) {
            return false;
        }

        int nonWhitespaceCodePoints = 0;
        boolean sawMeaningfulCodePoint = false;
        boolean sawLetter = false;
        boolean sawLowercaseLetter = false;
        boolean sawDecorativeGlyph = false;
        boolean sawSymbolicCodePoint = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }

            sawMeaningfulCodePoint = true;
            nonWhitespaceCodePoints++;
            if (nonWhitespaceCodePoints > 2 || Character.isDigit(codePoint)) {
                return false;
            }

            boolean decorativeTokenLetter = isDecorativeTokenLetterCodePoint(codePoint);
            if (Character.isLetter(codePoint) && !decorativeTokenLetter) {
                return false;
            }
            if (decorativeTokenLetter) {
                sawLetter = true;
                if (Character.isLowerCase(codePoint)) {
                    sawLowercaseLetter = true;
                }
            }
            if (isDecorativeGlyphCodePoint(codePoint)) {
                sawDecorativeGlyph = true;
            }
            if (isSymbolicCodePoint(codePoint)) {
                sawSymbolicCodePoint = true;
            }
            if (!(decorativeTokenLetter || isDecorativeGlyphCodePoint(codePoint) || isSymbolicCodePoint(codePoint))) {
                return false;
            }
        }

        if (!sawMeaningfulCodePoint) {
            return false;
        }
        if (sawDecorativeGlyph || sawSymbolicCodePoint) {
            return true;
        }
        if (sawLetter && sawLowercaseLetter && nonWhitespaceCodePoints > 1) {
            return false;
        }
        return sawMeaningfulCodePoint;
    }

    private static boolean isDecorativeTokenLetterCodePoint(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
    }

    private static boolean isAsciiPlainPunctuationOnly(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }

            if (!isAsciiPlainPunctuation(codePoint)) {
                return false;
            }
            sawNonWhitespace = true;
        }

        return sawNonWhitespace;
    }

    private static boolean isAsciiPlainPunctuation(int codePoint) {
        return codePoint <= 0x7F
                && !Character.isLetterOrDigit(codePoint)
                && !Character.isWhitespace(codePoint);
    }

    private static boolean isSymbolicCodePoint(int codePoint) {
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

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        if (unicodeType == Character.PRIVATE_USE || unicodeType == Character.UNASSIGNED) {
            return true;
        }

        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    private static boolean isWynnInlineSpacerGlyphCodePoint(int codePoint) {
        return codePoint >= WYNN_INLINE_SPACER_START && codePoint <= WYNN_INLINE_SPACER_END;
    }
} 
