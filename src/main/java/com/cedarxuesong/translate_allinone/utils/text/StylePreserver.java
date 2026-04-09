package com.cedarxuesong.translate_allinone.utils.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
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

        Style sanitizedStyle = sanitizeStyle(originalStyle, stripFont);
        if (!stripFont || originalStyle.equals(sanitizedStyle)) {
            target.append(Text.literal(content).setStyle(sanitizedStyle));
            return;
        }

        StringBuilder run = new StringBuilder();
        Boolean keepOriginalFontForRun = null;

        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
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
} 
