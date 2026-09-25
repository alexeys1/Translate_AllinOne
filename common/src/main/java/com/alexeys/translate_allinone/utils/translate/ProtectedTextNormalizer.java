package com.alexeys.translate_allinone.utils.translate;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProtectedTextNormalizer {

    public static final int MIN_SOURCE_LETTERS = 8;
    public static final int MIN_SOURCE_LETTERS_FOR_TRUNCATION_CHECK = 24;
    public static final int TRUNCATION_SIGNAL_RATIO = 8;

    private static final Pattern PROTECTED_TOKEN_PATTERN = Pattern.compile(
            "__TAIO_PROTECTED_TOKEN_\\d+__"
                    + "|</?s\\d+>"
                    + "|(?:\\u00A7[0-9A-FK-ORa-fk-or])+"
                    + "|\\{(?:value|glyph)\\d+}"
                    + "|\\{accent\\d+\\.(?:begin|end)}"
                    + "|\\{(?:d|g)\\d+}"
                    + "|\\{c\\d+}"
                    + "|%(?:\\d+\\$)?[sdf]"
                    + "|\\\\(?:n|t|r)"
                    + "|\\[[^\\]\\r\\n]+]"
                    + "|https?://[^\\s<>\"']+"
                    + "|/[A-Za-z0-9_./:@-]+"
                    + "|[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+"
                    + "|\\d+(?:[.,]\\d+)*"
    );

    private ProtectedTextNormalizer() {
    }

    public static String stripProtectedContent(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return PROTECTED_TOKEN_PATTERN.matcher(text).replaceAll(" ");
    }

    public static String normalizeComparable(String text) {
        String stripped = stripProtectedContent(text);
        if (stripped.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(stripped, Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                result.appendCodePoint(Character.toLowerCase(codePoint));
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    public static int countAsciiLetters(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if ((current >= 'A' && current <= 'Z') || (current >= 'a' && current <= 'z')) {
                count++;
            }
        }
        return count;
    }

    public static boolean containsCjk(String text) {
        return countCjk(text) > 0;
    }

    public static int countCjk(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    public static int countTranslatedSignalUnits(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (isMeaningfulTranslatedSignalCodePoint(codePoint)) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    public static boolean looksTruncatedForChineseOutput(String source, String candidate) {
        int sourceLetters = countAsciiLetters(stripProtectedContent(source));
        int translatedSignal = countTranslatedSignalUnits(stripProtectedContent(candidate));
        return sourceLetters >= MIN_SOURCE_LETTERS_FOR_TRUNCATION_CHECK
                && translatedSignal > 0
                && translatedSignal * TRUNCATION_SIGNAL_RATIO < sourceLetters;
    }

    public static boolean isChineseTarget(String targetLanguage) {
        if (targetLanguage == null) {
            return false;
        }
        String language = targetLanguage.toLowerCase(Locale.ROOT);
        return language.contains("chinese") || language.contains("中文") || language.startsWith("zh");
    }

    static boolean hasAbnormalRepetition(String text) {
        String normalized = normalizeComparable(text);
        int maximumChunkLength = Math.min(12, normalized.length() / 4);
        for (int chunkLength = 4; chunkLength <= maximumChunkLength; chunkLength++) {
            for (int start = 0; start + chunkLength <= normalized.length(); start++) {
                String chunk = normalized.substring(start, start + chunkLength);
                int count = 0;
                int offset = 0;
                while ((offset = normalized.indexOf(chunk, offset)) >= 0) {
                    count++;
                    offset += chunkLength;
                }
                if (count >= 4) {
                    return true;
                }
            }
        }
        return false;
    }

    public static java.util.List<String> extractHardProtectedTokens(String text) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        Matcher matcher = PROTECTED_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (isComparableHardToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isComparableHardToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.startsWith("{c")) {
            return false;
        }
        char first = token.charAt(0);
        return first == '\u00A7'
                || token.startsWith("<s")
                || token.startsWith("</s")
                || token.startsWith("{")
                || token.startsWith("%")
                || token.startsWith("\\")
                || token.startsWith("__TAIO_");
    }

    private static boolean isMeaningfulTranslatedSignalCodePoint(int codePoint) {
        if (isCjkCodePoint(codePoint)
                || Character.isDigit(codePoint)
                || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.MODIFIER_SYMBOL;
    }

    private static boolean isCjkCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}