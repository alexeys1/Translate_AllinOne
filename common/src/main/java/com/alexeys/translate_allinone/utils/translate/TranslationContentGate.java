package com.alexeys.translate_allinone.utils.translate;

public final class TranslationContentGate {

    private TranslationContentGate() {
    }

    public static boolean alreadyInTargetLanguage(String source, String targetLanguage) {
        if (source == null || source.isBlank()
                || !ProtectedTextNormalizer.isChineseTarget(targetLanguage)) {
            return false;
        }
        String stripped = ProtectedTextNormalizer.stripProtectedContent(source);
        int hanCount = 0;
        for (int offset = 0; offset < stripped.length(); ) {
            int codePoint = stripped.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return false;
            }
            if (script == Character.UnicodeScript.HAN) {
                hanCount++;
            }
            offset += Character.charCount(codePoint);
        }
        if (hanCount < 2) {
            return false;
        }
        return hanCount >= ProtectedTextNormalizer.countAsciiLetters(stripped);
    }
}
