package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WynncraftPlaceholderDictionary {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final String SIGNED_DECIMAL_CAPTURE = "([+\\-\\u2212\\u00B1]?\\d+(?:[.,]\\d+)?)";
    private static final String UNSIGNED_DECIMAL_CAPTURE = "(\\d+(?:[.,]\\d+)?)";

    private final Path dictionaryPath;
    private final String dictionaryLabel;
    private volatile DictionarySnapshot snapshot = DictionarySnapshot.empty();
    private volatile boolean loadAttempted;

    WynncraftPlaceholderDictionary(Path dictionaryPath, String dictionaryLabel) {
        this.dictionaryPath = dictionaryPath;
        this.dictionaryLabel = dictionaryLabel == null || dictionaryLabel.isBlank()
                ? "wynncraft-dictionary"
                : dictionaryLabel;
    }

    public synchronized void load() {
        snapshot = loadSnapshot();
        loadAttempted = true;
    }

    LookupResult lookupTranslation(String sourceText) {
        ensureLoaded();
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }

        String normalizedSource = normalizeSourceText(sourceText);
        if (normalizedSource.isBlank()) {
            return null;
        }

        String exact = snapshot.exactTranslations().get(normalizedSource.toLowerCase(Locale.ROOT));
        if (exact != null && !exact.isBlank()) {
            return new LookupResult(exact, MatchType.EXACT);
        }

        for (PatternEntry entry : snapshot.patternEntries()) {
            Matcher matcher = entry.pattern().matcher(normalizedSource);
            if (!matcher.matches()) {
                continue;
            }

            String translated = applyTranslationTemplate(entry.translationTemplate(), matcher, entry.placeholders());
            if (translated != null && !translated.isBlank()) {
                return new LookupResult(translated, MatchType.PATTERN);
            }
        }
        return null;
    }

    public String findTranslation(String sourceText) {
        LookupResult lookupResult = lookupTranslation(sourceText);
        return lookupResult == null ? null : lookupResult.translation();
    }

    boolean hasEntries() {
        ensureLoaded();
        return !snapshot.exactTranslations().isEmpty() || !snapshot.patternEntries().isEmpty();
    }

    private synchronized void ensureLoaded() {
        if (!loadAttempted) {
            load();
        }
    }

    private DictionarySnapshot loadSnapshot() {
        if (dictionaryPath == null) {
            Translate_AllinOne.LOGGER.warn("{} path is unavailable.", dictionaryLabel);
            return DictionarySnapshot.empty();
        }
        if (!Files.exists(dictionaryPath)) {
            Translate_AllinOne.LOGGER.warn("{} file not found: {}", dictionaryLabel, dictionaryPath);
            return DictionarySnapshot.empty();
        }

        try (Reader reader = Files.newBufferedReader(dictionaryPath, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                Translate_AllinOne.LOGGER.warn("{} has an invalid root object: {}", dictionaryLabel, dictionaryPath);
                return DictionarySnapshot.empty();
            }

            LinkedHashMap<String, String> sourceToTranslation = new LinkedHashMap<>();
            collectEntries(rootElement.getAsJsonObject(), sourceToTranslation);

            LinkedHashMap<String, String> exactTranslations = new LinkedHashMap<>();
            List<PatternEntry> patternEntries = new ArrayList<>();
            for (Map.Entry<String, String> entry : sourceToTranslation.entrySet()) {
                String source = normalizeSourceText(entry.getKey());
                String translation = normalizeTranslationText(entry.getValue());
                if (source.isBlank() || translation.isBlank()) {
                    continue;
                }

                if (containsPlaceholder(source)) {
                    PatternEntry patternEntry = compilePatternEntry(source, translation);
                    if (patternEntry != null) {
                        patternEntries.add(patternEntry);
                    }
                } else {
                    exactTranslations.putIfAbsent(source.toLowerCase(Locale.ROOT), translation);
                }
            }

            Translate_AllinOne.LOGGER.info(
                    "Loaded {} from {}. exact={}, patterns={}",
                    dictionaryLabel,
                    dictionaryPath,
                    exactTranslations.size(),
                    patternEntries.size()
            );
            return new DictionarySnapshot(Map.copyOf(exactTranslations), List.copyOf(patternEntries));
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.error("Failed to load {} from {}", dictionaryLabel, dictionaryPath, e);
            return DictionarySnapshot.empty();
        }
    }

    private static void collectEntries(JsonObject object, Map<String, String> output) {
        if (object == null || output == null) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (key == null || key.isBlank() || key.startsWith("_") || value == null || value.isJsonNull()) {
                continue;
            }

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                output.putIfAbsent(key, value.getAsString());
                continue;
            }

            if (value.isJsonObject()) {
                collectEntries(value.getAsJsonObject(), output);
                continue;
            }

            if (value.isJsonArray()) {
                for (JsonElement arrayElement : value.getAsJsonArray()) {
                    if (arrayElement != null && arrayElement.isJsonObject()) {
                        collectEntries(arrayElement.getAsJsonObject(), output);
                    }
                }
            }
        }
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && PLACEHOLDER_PATTERN.matcher(value).find();
    }

    private static PatternEntry compilePatternEntry(String sourceTemplate, String translationTemplate) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sourceTemplate);
        StringBuilder regex = new StringBuilder("^");
        List<String> placeholders = new ArrayList<>();
        int lastEnd = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(sourceTemplate.substring(lastEnd, matcher.start())));
            String placeholder = matcher.group(1);
            regex.append(resolvePlaceholderCapturePattern(sourceTemplate, matcher.start(), placeholder));
            placeholders.add(placeholder);
            lastEnd = matcher.end();
        }
        regex.append(Pattern.quote(sourceTemplate.substring(lastEnd)));
        regex.append('$');
        return new PatternEntry(
                Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                translationTemplate,
                List.copyOf(placeholders)
        );
    }

    private static String resolvePlaceholderCapturePattern(
            String sourceTemplate,
            int placeholderStart,
            String placeholder
    ) {
        if (!isNumericPlaceholder(placeholder)) {
            return "(.+?)";
        }
        return hasExplicitNumericSignPrefix(sourceTemplate, placeholderStart)
                ? UNSIGNED_DECIMAL_CAPTURE
                : SIGNED_DECIMAL_CAPTURE;
    }

    private static boolean isNumericPlaceholder(String placeholder) {
        if (placeholder == null || placeholder.isBlank()) {
            return false;
        }

        String normalized = placeholder.trim().toLowerCase(Locale.ROOT);
        return "n".equals(normalized)
                || "m".equals(normalized)
                || "x".equals(normalized)
                || "y".equals(normalized);
    }

    private static boolean hasExplicitNumericSignPrefix(String sourceTemplate, int placeholderStart) {
        if (sourceTemplate == null || sourceTemplate.isEmpty() || placeholderStart <= 0) {
            return false;
        }

        int previousCodePoint = sourceTemplate.codePointBefore(placeholderStart);
        return previousCodePoint == '+'
                || previousCodePoint == '-'
                || previousCodePoint == '\u2212'
                || previousCodePoint == '\u00B1';
    }

    private static String applyTranslationTemplate(String template, Matcher matcher, List<String> placeholders) {
        if (template == null || template.isBlank()) {
            return null;
        }

        String translated = template;
        for (int i = 0; i < placeholders.size(); i++) {
            String placeholder = placeholders.get(i);
            String value = matcher.group(i + 1);
            if (placeholder == null || placeholder.isBlank() || value == null) {
                continue;
            }
            translated = translated.replace(
                    "{" + placeholder + "}",
                    sanitizeTranslationPlaceholderValue(placeholder, value)
            );
        }
        return translated;
    }

    private static String sanitizeTranslationPlaceholderValue(String placeholder, String value) {
        if (value == null || value.isEmpty() || placeholder == null || placeholder.isBlank()) {
            return value;
        }

        String normalizedPlaceholder = placeholder.trim().toLowerCase(Locale.ROOT);
        if (normalizedPlaceholder.contains("separator")) {
            return sanitizeSeparatorPlaceholderValue(value);
        }
        if (normalizedPlaceholder.contains("prefix")) {
            return sanitizePrefixPlaceholderValue(value);
        }
        if ("name".equals(normalizedPlaceholder)) {
            return sanitizeNamePlaceholderValue(value);
        }
        return value;
    }

    private static String sanitizePrefixPlaceholderValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        StringBuilder visible = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isVisibleBmpDecorativeCodePoint(codePoint)) {
                visible.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return visible.toString();
    }

    private static String sanitizeSeparatorPlaceholderValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                return value;
            }
            offset += Character.charCount(codePoint);
        }
        return " ";
    }

    private static String sanitizeNamePlaceholderValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (Character.isWhitespace(codePoint) || !isHumanReadableNameCodePoint(codePoint)) {
                start += Character.charCount(codePoint);
                continue;
            }
            break;
        }
        return start <= 0 ? value : value.substring(start);
    }

    private static boolean isHumanReadableNameCodePoint(int codePoint) {
        if (!Character.isLetterOrDigit(codePoint)) {
            return false;
        }

        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.LATIN
                || script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.COMMON;
    }

    private static boolean isVisibleBmpDecorativeCodePoint(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF;
    }

    private static String normalizeSourceText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeTranslationText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record PatternEntry(
            Pattern pattern,
            String translationTemplate,
            List<String> placeholders
    ) {
    }

    enum MatchType {
        EXACT,
        PATTERN
    }

    record LookupResult(
            String translation,
            MatchType matchType
    ) {
    }

    private record DictionarySnapshot(
            Map<String, String> exactTranslations,
            List<PatternEntry> patternEntries
    ) {
        private static DictionarySnapshot empty() {
            return new DictionarySnapshot(Map.of(), List.of());
        }
    }
}
