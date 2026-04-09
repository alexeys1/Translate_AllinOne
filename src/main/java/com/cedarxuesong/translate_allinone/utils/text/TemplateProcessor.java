package com.cedarxuesong.translate_allinone.utils.text;

import java.util.ArrayList;
import java.util.List;
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
        List<String> values = new ArrayList<>();
        Matcher matcher = STYLE_TAG_PATTERN.matcher(text);
        StringBuilder templateBuffer = new StringBuilder();

        while (matcher.find()) {
            String taggedContent = matcher.group(2);
            if (!isDecorativeGlyphOnly(taggedContent)) {
                matcher.appendReplacement(templateBuffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            values.add(matcher.group());
            String placeholder = "{g" + values.size() + "}";
            matcher.appendReplacement(templateBuffer, Matcher.quoteReplacement(placeholder));
        }

        matcher.appendTail(templateBuffer);
        return new DecorativeGlyphExtractionResult(templateBuffer.toString(), values);
    }

    public static String reassembleDecorativeGlyphs(String translatedTemplate, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            String placeholder = "{g" + (i + 1) + "}";
            translatedTemplate = translatedTemplate.replaceFirst(Pattern.quote(placeholder), Matcher.quoteReplacement(values.get(i)));
        }
        return translatedTemplate;
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

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        if (unicodeType == Character.PRIVATE_USE || unicodeType == Character.UNASSIGNED) {
            return true;
        }

        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }
} 
