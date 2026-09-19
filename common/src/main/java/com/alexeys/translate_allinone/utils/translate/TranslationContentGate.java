package com.alexeys.translate_allinone.utils.translate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class TranslationContentGate {

    private static final Pattern EXPLANATION_PREFIX_PATTERN = Pattern.compile(
            "(?i)^(?:here(?:'s| is) (?:the |a )?translation|translated text|the translation|translation)\\s*[:：]"
    );

    private TranslationContentGate() {
    }

    public static TranslationContentVerdict evaluate(
            TranslationMode mode,
            String source,
            String candidate,
            String targetLanguage
    ) {
        return evaluate(mode, source, candidate, targetLanguage, false, true);
    }

    public static TranslationContentVerdict evaluate(
            TranslationMode mode,
            String source,
            String candidate,
            String targetLanguage,
            boolean truncated
    ) {
        return evaluate(mode, source, candidate, targetLanguage, truncated, true);
    }

    public static TranslationContentVerdict evaluate(
            TranslationMode mode,
            String source,
            String candidate,
            String targetLanguage,
            boolean truncated,
            boolean checkProtectedTokens
    ) {
        TranslationMode resolvedMode = mode == null ? TranslationMode.TRANSLATE : mode;
        if (candidate == null || candidate.isBlank()) {
            return TranslationContentVerdict.reject(TranslationContentVerdict.EMPTY);
        }
        if (truncated) {
            return TranslationContentVerdict.reject(TranslationContentVerdict.TRUNCATED);
        }
        if (looksLikeStructuredArtifact(candidate)) {
            return TranslationContentVerdict.reject(TranslationContentVerdict.STRUCTURED_ARTIFACT);
        }
        if (checkProtectedTokens) {
            TranslationContentVerdict tokenVerdict = evaluateProtectedTokens(source, candidate);
            if (!tokenVerdict.accepted()) {
                return tokenVerdict;
            }
        }
        if (resolvedMode == TranslationMode.TRANSLATE && !sourceLooksLikeTargetLanguage(source, targetLanguage)) {
            String strippedSource = ProtectedTextNormalizer.stripProtectedContent(source);
            int sourceLetters = ProtectedTextNormalizer.countAsciiLetters(strippedSource);
            String candidateComparable = ProtectedTextNormalizer.normalizeComparable(candidate);
            boolean candidateHasComparableText = !candidateComparable.isEmpty();
            if (sourceLetters >= ProtectedTextNormalizer.MIN_SOURCE_LETTERS
                    && candidateHasComparableText
                    && ProtectedTextNormalizer.normalizeComparable(source).equals(candidateComparable)) {
                return TranslationContentVerdict.reject(TranslationContentVerdict.SOURCE_COPY);
            }
            if (ProtectedTextNormalizer.isChineseTarget(targetLanguage)
                    && sourceLetters >= ProtectedTextNormalizer.MIN_SOURCE_LETTERS
                    && candidateComparable.length() >= ProtectedTextNormalizer.MIN_SOURCE_LETTERS
                    && !ProtectedTextNormalizer.containsCjk(candidate)) {
                return TranslationContentVerdict.reject(TranslationContentVerdict.MISSING_TARGET_LANGUAGE_SIGNAL);
            }
            if (ProtectedTextNormalizer.isChineseTarget(targetLanguage)
                    && ProtectedTextNormalizer.looksTruncatedForChineseOutput(source, candidate)) {
                return TranslationContentVerdict.reject(TranslationContentVerdict.TRUNCATED);
            }
        }
        if (ProtectedTextNormalizer.hasAbnormalRepetition(candidate)) {
            return TranslationContentVerdict.reject(TranslationContentVerdict.ABNORMAL_REPETITION);
        }
        return TranslationContentVerdict.accept();
    }

    private static TranslationContentVerdict evaluateProtectedTokens(String source, String candidate) {
        List<String> expected = ProtectedTextNormalizer.extractHardProtectedTokens(source);
        if (expected.isEmpty()) {
            return TranslationContentVerdict.accept();
        }
        List<String> actual = ProtectedTextNormalizer.extractHardProtectedTokens(candidate);
        List<String> expectedSorted = new ArrayList<>(expected);
        List<String> actualSorted = new ArrayList<>(actual);
        Collections.sort(expectedSorted);
        Collections.sort(actualSorted);
        return expectedSorted.equals(actualSorted)
                ? TranslationContentVerdict.accept()
                : TranslationContentVerdict.reject(TranslationContentVerdict.PROTECTED_TOKEN_MISMATCH);
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

    private static boolean looksLikeStructuredArtifact(String candidate) {
        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.startsWith("```")
                || trimmed.startsWith("\"translation\"")
                || trimmed.startsWith("\"text\"")) {
            return true;
        }
        if (trimmed.startsWith("{") && trimmed.contains("\":")) {
            return true;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.contains("\"")) {
            return true;
        }
        if (trimmed.contains("\"translation\":") || trimmed.contains("\"text\":")) {
            return true;
        }
        return EXPLANATION_PREFIX_PATTERN.matcher(trimmed).find();
    }

    private static boolean sourceLooksLikeTargetLanguage(String source, String targetLanguage) {
        return ProtectedTextNormalizer.isChineseTarget(targetLanguage)
                && ProtectedTextNormalizer.containsCjk(source);
    }
}
