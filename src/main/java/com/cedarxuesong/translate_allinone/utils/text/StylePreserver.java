package com.cedarxuesong.translate_allinone.utils.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StylePreserver {

    private static final char PLACEHOLDER_START_CHAR = '\uE000';
    private static final Pattern STYLE_TAG_MARKER_PATTERN = Pattern.compile("</?s(\\d+)>");
    // Regex to find a placeholder, its content, and the closing placeholder.
    // Placeholders are characters in the Private Use Area.
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "([\uE000-\uF8FF])(.*?)\\1",
            Pattern.DOTALL
    );

    public static class ExtractionResult {
        public final String markedText;
        public final Map<Integer, Style> styleMap;

        public ExtractionResult(String markedText, Map<Integer, Style> styleMap) {
            this.markedText = markedText;
            this.styleMap = styleMap;
        }
    }

    public static ExtractionResult extractAndMark(Text message) {
        StringBuilder markedText = new StringBuilder();
        Map<Integer, Style> styleMap = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0); // Start from 0 to align with char offset

        message.visit((style, string) -> {
            if (!string.isEmpty()) {
                if (!style.isEmpty()) {
                    int id = counter.getAndIncrement();
                    styleMap.put(id, style);
                    char placeholder = (char) (PLACEHOLDER_START_CHAR + id);
                    markedText.append(placeholder);
                    markedText.append(string);
                    markedText.append(placeholder);
                } else {
                    markedText.append(string);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        return new ExtractionResult(markedText.toString(), styleMap);
    }
    
    public static ExtractionResult extractAndMarkWithTags(Text message) {
        StringBuilder markedText = new StringBuilder();
        Map<Integer, Style> styleMap = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        message.visit((style, string) -> {
            if (!string.isEmpty()) {
                if (!style.isEmpty()) {
                    appendTaggedSegments(markedText, styleMap, counter, style, string);
                } else {
                    markedText.append(string);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        return new ExtractionResult(markedText.toString(), styleMap);
    }

    public static Text reapplyStyles(String translatedText, Map<Integer, Style> styleMap) {
        MutableText resultText = Text.empty();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(translatedText);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                resultText.append(Text.literal(translatedText.substring(lastEnd, matcher.start())));
            }
            
            char placeholder = matcher.group(1).charAt(0);
            int id = placeholder - PLACEHOLDER_START_CHAR;
            String content = matcher.group(2);
            Style style = styleMap.getOrDefault(id, Style.EMPTY);
            
            resultText.append(Text.literal(content).setStyle(style));
            
            lastEnd = matcher.end();
        }

        if (lastEnd < translatedText.length()) {
            resultText.append(Text.literal(translatedText.substring(lastEnd)));
        }

        return resultText;
    }

    public static Text reapplyStylesFromTags(String translatedText, Map<Integer, Style> styleMap) {
        return reapplyStylesFromTags(translatedText, styleMap, false);
    }

    public static Style sanitizeStyleForComparison(Style style, boolean stripFont) {
        return sanitizeStyle(style == null ? Style.EMPTY : style, stripFont);
    }

    public static Text reapplyStylesFromTags(String translatedText, Map<Integer, Style> styleMap, boolean stripFont) {
        MutableText resultText = Text.empty();
        Matcher matcher = STYLE_TAG_MARKER_PATTERN.matcher(translatedText);
        int lastEnd = 0;
        Integer activeStyleId = null;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String content = translatedText.substring(lastEnd, matcher.start());
                appendTaggedOrPlainContent(resultText, content, activeStyleId, styleMap, stripFont);
            }

            int id = Integer.parseInt(matcher.group(1));
            boolean closingTag = translatedText.charAt(matcher.start() + 1) == '/';

            if (closingTag) {
                activeStyleId = null;
            } else {
                activeStyleId = id;
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < translatedText.length()) {
            String content = translatedText.substring(lastEnd);
            appendTaggedOrPlainContent(resultText, content, activeStyleId, styleMap, stripFont);
        }

        return resultText;
    }

    private static void appendTaggedOrPlainContent(
            MutableText target,
            String content,
            Integer activeStyleId,
            Map<Integer, Style> styleMap,
            boolean stripFont
    ) {
        if (content == null || content.isEmpty()) {
            return;
        }

        if (activeStyleId == null) {
            target.append(Text.literal(content));
            return;
        }

        Style originalStyle = styleMap.getOrDefault(activeStyleId, Style.EMPTY);
        appendTaggedContent(target, content, originalStyle, stripFont);
    }

    private static void appendTaggedSegments(
            StringBuilder markedText,
            Map<Integer, Style> styleMap,
            AtomicInteger counter,
            Style style,
            String string
    ) {
        StringBuilder segment = new StringBuilder();
        Boolean privateUseSegment = null;

        for (int offset = 0; offset < string.length(); ) {
            int codePoint = string.codePointAt(offset);
            boolean isDecorativeGlyph = isDecorativeGlyphCodePoint(codePoint);

            if (privateUseSegment != null && privateUseSegment != isDecorativeGlyph && segment.length() > 0) {
                appendTaggedSegment(markedText, styleMap, counter, style, segment.toString());
                segment.setLength(0);
            }

            privateUseSegment = isDecorativeGlyph;
            segment.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }

        if (segment.length() > 0) {
            appendTaggedSegment(markedText, styleMap, counter, style, segment.toString());
        }
    }

    private static void appendTaggedSegment(
            StringBuilder markedText,
            Map<Integer, Style> styleMap,
            AtomicInteger counter,
            Style style,
            String segment
    ) {
        if (segment == null || segment.isEmpty()) {
            return;
        }

        int id = counter.getAndIncrement();
        styleMap.put(id, style);
        markedText.append("<s").append(id).append(">");
        markedText.append(segment);
        markedText.append("</s").append(id).append(">");
    }

    private static void appendTaggedContent(MutableText target, String content, Style originalStyle, boolean stripFont) {
        if (content == null || content.isEmpty()) {
            return;
        }

        String normalizedContent = content;
        if (stripFont && !TemplateProcessor.shouldPreserveDecorativeGlyphSegment(content)) {
            normalizedContent = TemplateProcessor.collapseWynnInlineSpacerGlyphs(content);
        }
        if (normalizedContent == null || normalizedContent.isEmpty()) {
            return;
        }

        if (stripFont && shouldKeepOriginalFontForSegment(normalizedContent, originalStyle)) {
            target.append(Text.literal(normalizedContent).setStyle(originalStyle));
            return;
        }

        Style sanitizedStyle = sanitizeStyle(originalStyle, stripFont);
        if (!stripFont || originalStyle.equals(sanitizedStyle)) {
            target.append(Text.literal(normalizedContent).setStyle(sanitizedStyle));
            return;
        }

        StringBuilder run = new StringBuilder();
        Boolean keepOriginalFontForRun = null;

        for (int offset = 0; offset < normalizedContent.length(); ) {
            int codePoint = normalizedContent.codePointAt(offset);
            boolean keepOriginalFont = isDecorativeGlyphCodePoint(codePoint);

            if (keepOriginalFontForRun != null && keepOriginalFontForRun != keepOriginalFont && run.length() > 0) {
                target.append(Text.literal(run.toString()).setStyle(keepOriginalFontForRun ? originalStyle : sanitizedStyle));
                run.setLength(0);
            }

            keepOriginalFontForRun = keepOriginalFont;
            run.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }

        if (run.length() > 0) {
            target.append(Text.literal(run.toString()).setStyle(Boolean.TRUE.equals(keepOriginalFontForRun) ? originalStyle : sanitizedStyle));
        }
    }

    private static boolean shouldKeepOriginalFontForSegment(String content, Style originalStyle) {
        if (originalStyle == null || originalStyle.getFont() == null || content == null || content.isEmpty()) {
            return false;
        }
        if (isAsciiPlainPunctuationOnly(content)) {
            return false;
        }

        if (isLikelyDecorativeFontToken(content)) {
            return true;
        }

        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
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

        return sawNonWhitespace && !isAsciiPlainPunctuationOnly(content);
    }

    private static boolean isLikelyDecorativeFontToken(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        if (isAsciiPlainPunctuationOnly(content)) {
            return false;
        }

        int nonWhitespaceCodePoints = 0;
        boolean sawMeaningfulCodePoint = false;
        boolean sawLetter = false;
        boolean sawLowercaseLetter = false;
        boolean sawDecorativeGlyph = false;
        boolean sawSymbolicCodePoint = false;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
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
        if (sawLetter && sawLowercaseLetter) {
            return false;
        }
        return sawMeaningfulCodePoint;
    }

    private static boolean isDecorativeTokenLetterCodePoint(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
    }

    private static boolean isAsciiPlainPunctuationOnly(String content) {
        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
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

    private static Style sanitizeStyle(Style style, boolean stripFont) {
        if (!stripFont || style.isEmpty()) {
            return style;
        }

        Style sanitized = Style.EMPTY;
        if (style.getColor() != null) {
            sanitized = sanitized.withColor(style.getColor());
        }

        sanitized = sanitized.withBold(style.isBold());
        sanitized = sanitized.withItalic(style.isItalic());
        sanitized = sanitized.withUnderline(style.isUnderlined());
        sanitized = sanitized.withStrikethrough(style.isStrikethrough());
        sanitized = sanitized.withObfuscated(style.isObfuscated());

        if (style.getClickEvent() != null) {
            sanitized = sanitized.withClickEvent(style.getClickEvent());
        }
        if (style.getHoverEvent() != null) {
            sanitized = sanitized.withHoverEvent(style.getHoverEvent());
        }
        if (style.getInsertion() != null) {
            sanitized = sanitized.withInsertion(style.getInsertion());
        }

        return sanitized;
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

    public static String toLegacyTemplate(String markedTemplate, Map<Integer, Style> styleMap) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(markedTemplate);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sb.append(markedTemplate.substring(lastEnd, matcher.start()));
            }

            char placeholder = matcher.group(1).charAt(0);
            int id = placeholder - PLACEHOLDER_START_CHAR;
            String content = matcher.group(2);
            Style style = styleMap.getOrDefault(id, Style.EMPTY);

            sb.append(styleToLegacyFormatting(style));
            sb.append(content);

            lastEnd = matcher.end();
        }

        if (lastEnd < markedTemplate.length()) {
            sb.append(markedTemplate.substring(lastEnd));
        }

        return sb.toString();
    }

    private static String styleToLegacyFormatting(Style style) {
        if (style.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (style.getColor() != null) {
            for (Formatting f : Formatting.values()) {
                if (f.isColor() && f.getColorValue() != null && f.getColorValue().equals(style.getColor().getRgb())) {
                    sb.append('§').append(f.getCode());
                    break;
                }
            }
        }
        if (style.isBold()) sb.append("§l");
        if (style.isItalic()) sb.append("§o");
        if (style.isUnderlined()) sb.append("§n");
        if (style.isStrikethrough()) sb.append("§m");
        if (style.isObfuscated()) sb.append("§k");
        return sb.toString();
    }

    public static Text fromLegacyText(String text) {
        if (text == null || text.isEmpty()) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        MutableText currentComponent = Text.literal("");
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                if (i + 1 >= text.length()) {
                    currentComponent.append("§");
                    break;
                }
                
                if (!currentComponent.getString().isEmpty()) {
                    result.append(currentComponent.setStyle(currentStyle));
                }
                currentComponent = Text.literal("");

                char formatChar = Character.toLowerCase(text.charAt(i + 1));
                Formatting formatting = Formatting.byCode(formatChar);

                if (formatting != null) {
                    if (formatting.isColor() || formatting == Formatting.RESET) {
                        currentStyle = Style.EMPTY.withFormatting(formatting);
                    } else {
                        currentStyle = currentStyle.withFormatting(formatting);
                    }
                }
                i++; 
            } else {
                currentComponent.append(String.valueOf(c));
            }
        }

        if (!currentComponent.getString().isEmpty()) {
            result.append(currentComponent.setStyle(currentStyle));
        }

        return result;
    }

    public static String convertLegacyTranslationToTaggedTemplate(String legacyTranslation, Map<Integer, Style> targetStyleMap) {
        if (legacyTranslation == null || legacyTranslation.isEmpty()) {
            return legacyTranslation;
        }
        if (targetStyleMap == null || targetStyleMap.isEmpty()) {
            return legacyTranslation;
        }

        List<Integer> orderedStyleIds = new ArrayList<>(targetStyleMap.keySet());
        orderedStyleIds.sort(Integer::compareTo);
        StringBuilder taggedTranslation = new StringBuilder();
        int[] preferredSearchIndex = {0};
        boolean[] success = {true};

        fromLegacyText(legacyTranslation).visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            if (style == null || style.isEmpty()) {
                taggedTranslation.append(string);
                return Optional.empty();
            }

            int matchedIndex = findBestMatchingStyleIndex(string, style, targetStyleMap, orderedStyleIds, preferredSearchIndex[0]);
            if (matchedIndex < 0) {
                success[0] = false;
                return Optional.of(Unit.INSTANCE);
            }

            int styleId = orderedStyleIds.get(matchedIndex);
            taggedTranslation.append("<s").append(styleId).append(">");
            taggedTranslation.append(string);
            taggedTranslation.append("</s").append(styleId).append(">");
            preferredSearchIndex[0] = matchedIndex;
            return Optional.empty();
        }, Style.EMPTY);

        return success[0] ? taggedTranslation.toString() : null;
    }

    private static int findBestMatchingStyleIndex(
            String content,
            Style legacyStyle,
            Map<Integer, Style> targetStyleMap,
            List<Integer> orderedStyleIds,
            int preferredSearchIndex
    ) {
        boolean decorativeToken = isLikelyDecorativeFontToken(content);
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < orderedStyleIds.size(); i++) {
            Style candidateStyle = targetStyleMap.get(orderedStyleIds.get(i));
            if (!matchesLegacyFormatting(candidateStyle, legacyStyle)) {
                continue;
            }

            int score = 0;
            if (i >= preferredSearchIndex) {
                score += 1000;
                score -= (i - preferredSearchIndex);
            } else {
                score -= (preferredSearchIndex - i) * 4;
            }

            boolean candidateHasFont = candidateStyle != null && candidateStyle.getFont() != null;
            if (decorativeToken == candidateHasFont) {
                score += 200;
            } else if (!candidateHasFont) {
                score += 25;
            }

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private static boolean matchesLegacyFormatting(Style candidateStyle, Style legacyStyle) {
        Style candidate = sanitizeStyle(candidateStyle == null ? Style.EMPTY : candidateStyle, true);
        Style legacy = sanitizeStyle(legacyStyle == null ? Style.EMPTY : legacyStyle, false);

        int candidateColor = candidate.getColor() == null ? Integer.MIN_VALUE : candidate.getColor().getRgb();
        int legacyColor = legacy.getColor() == null ? Integer.MIN_VALUE : legacy.getColor().getRgb();
        return candidateColor == legacyColor
                && candidate.isBold() == legacy.isBold()
                && candidate.isItalic() == legacy.isItalic()
                && candidate.isUnderlined() == legacy.isUnderlined()
                && candidate.isStrikethrough() == legacy.isStrikethrough()
                && candidate.isObfuscated() == legacy.isObfuscated();
    }

    private enum Unit {
        INSTANCE
    }
}
