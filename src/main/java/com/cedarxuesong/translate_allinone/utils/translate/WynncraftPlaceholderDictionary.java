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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

final class WynncraftPlaceholderDictionary {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final String SIGNED_DECIMAL_CAPTURE = "([+\\-\\u2212\\u00B1]?\\d+(?:[.,]\\d+)?)";
    private static final String UNSIGNED_DECIMAL_CAPTURE = "(\\d+(?:[.,]\\d+)?)";
    private static final int LOOKUP_CACHE_LIMIT = 4096;
    private static final LookupResult CACHE_MISS = new LookupResult("", null, "");

    private final Supplier<List<Path>> dictionaryPathsSupplier;
    private final String dictionaryLabel;
    private volatile SnapshotState snapshotState = new SnapshotState(DictionarySnapshot.empty(), 0L);
    private volatile boolean loadAttempted;
    private final LruLookupCache lookupCache = new LruLookupCache(LOOKUP_CACHE_LIMIT);
    private final LruLookupCache rawLookupCache = new LruLookupCache(LOOKUP_CACHE_LIMIT);

    WynncraftPlaceholderDictionary(Path dictionaryPath, String dictionaryLabel) {
        this(() -> dictionaryPath == null ? List.of() : List.of(dictionaryPath), dictionaryLabel);
    }

    WynncraftPlaceholderDictionary(Supplier<List<Path>> dictionaryPathsSupplier, String dictionaryLabel) {
        this.dictionaryPathsSupplier = dictionaryPathsSupplier;
        this.dictionaryLabel = dictionaryLabel == null || dictionaryLabel.isBlank()
                ? "wynncraft-dictionary"
                : dictionaryLabel;
    }

    public synchronized void load() {
        DictionarySnapshot loadedSnapshot = loadSnapshot();
        snapshotState = new SnapshotState(loadedSnapshot, snapshotState.version() + 1L);
        lookupCache.clear();
        rawLookupCache.clear();
        loadAttempted = true;
    }

    LookupResult lookupTranslation(String sourceText) {
        ensureLoaded();
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }

        SnapshotState currentSnapshotState = snapshotState;
        CachedLookupResult rawCached = rawLookupCache.get(sourceText);
        if (rawCached != null && rawCached.snapshotVersion() == currentSnapshotState.version()) {
            LookupResult cachedLookupResult = rawCached.lookupResult();
            return cachedLookupResult == CACHE_MISS ? null : cachedLookupResult;
        }

        String normalizedSource = normalizeSourceText(sourceText);
        if (normalizedSource.isBlank()) {
            return null;
        }

        CachedLookupResult cached = lookupCache.get(normalizedSource);
        if (cached != null && cached.snapshotVersion() == currentSnapshotState.version()) {
            LookupResult cachedLookupResult = cached.lookupResult();
            rememberRawLookupResult(sourceText, cachedLookupResult, currentSnapshotState.version());
            return cachedLookupResult == CACHE_MISS ? null : cachedLookupResult;
        }

        LookupResult lookupResult = lookupTranslationUncached(
                normalizedSource,
                currentSnapshotState.snapshot()
        );
        rememberLookupResult(normalizedSource, lookupResult, currentSnapshotState.version());
        rememberRawLookupResult(sourceText, lookupResult, currentSnapshotState.version());
        return lookupResult;
    }

    private LookupResult lookupTranslationUncached(String normalizedSource, DictionarySnapshot snapshot) {
        DictionaryEntry exact = snapshot.exactTranslations().get(normalizedSource.toLowerCase(Locale.ROOT));
        if (exact != null && exact.translation() != null && !exact.translation().isBlank()) {
            return new LookupResult(exact.translation(), MatchType.EXACT, exact.sourceId());
        }

        for (PatternEntry entry : snapshot.candidatePatternEntries(normalizedSource)) {
            Matcher matcher = entry.pattern().matcher(normalizedSource);
            if (!matcher.matches()) {
                continue;
            }

            String translated = applyTranslationTemplate(entry.translationTemplate(), matcher, entry.placeholders());
            if (translated != null && !translated.isBlank()) {
                return new LookupResult(translated, MatchType.PATTERN, entry.sourceId());
            }
        }
        return null;
    }

    private void rememberRawLookupResult(String rawSource, LookupResult lookupResult, long snapshotVersion) {
        if (rawSource == null || rawSource.isBlank()) {
            return;
        }
        rawLookupCache.put(
                rawSource,
                new CachedLookupResult(snapshotVersion, lookupResult == null ? CACHE_MISS : lookupResult)
        );
    }

    private void rememberLookupResult(String normalizedSource, LookupResult lookupResult, long snapshotVersion) {
        if (normalizedSource == null || normalizedSource.isBlank()) {
            return;
        }
        lookupCache.put(
                normalizedSource,
                new CachedLookupResult(snapshotVersion, lookupResult == null ? CACHE_MISS : lookupResult)
        );
    }

    public String findTranslation(String sourceText) {
        LookupResult lookupResult = lookupTranslation(sourceText);
        return lookupResult == null ? null : lookupResult.translation();
    }

    boolean hasEntries() {
        ensureLoaded();
        DictionarySnapshot snapshot = snapshotState.snapshot();
        return !snapshot.exactTranslations().isEmpty() || !snapshot.patternEntries().isEmpty();
    }

    private void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        synchronized (this) {
            if (!loadAttempted) {
                load();
            }
        }
    }

    private DictionarySnapshot loadSnapshot() {
        List<Path> dictionaryPaths = dictionaryPathsSupplier == null ? List.of() : dictionaryPathsSupplier.get();
        if (dictionaryPaths == null || dictionaryPaths.isEmpty()) {
            Translate_AllinOne.LOGGER.warn("{} paths are unavailable.", dictionaryLabel);
            return DictionarySnapshot.empty();
        }
        LinkedHashMap<String, DictionaryEntry> exactTranslations = new LinkedHashMap<>();
        List<PatternEntry> patternEntries = new ArrayList<>();
        int loadedFileCount = 0;

        for (Path dictionaryPath : dictionaryPaths) {
            if (dictionaryPath == null) {
                continue;
            }
            if (!Files.exists(dictionaryPath)) {
                Translate_AllinOne.LOGGER.warn("{} file not found: {}", dictionaryLabel, dictionaryPath);
                continue;
            }
            try (Reader reader = Files.newBufferedReader(dictionaryPath, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (rootElement == null || !rootElement.isJsonObject()) {
                    Translate_AllinOne.LOGGER.warn("{} has an invalid root object: {}", dictionaryLabel, dictionaryPath);
                    continue;
                }

                LinkedHashMap<String, String> sourceToTranslation = new LinkedHashMap<>();
                collectEntries(rootElement.getAsJsonObject(), sourceToTranslation);
                mergeEntries(
                        sourceToTranslation,
                        exactTranslations,
                        patternEntries,
                        resolveDictionarySourceId(dictionaryPath)
                );
                loadedFileCount++;
            } catch (IOException | RuntimeException e) {
                Translate_AllinOne.LOGGER.error("Failed to load {} from {}", dictionaryLabel, dictionaryPath, e);
            }
        }

        if (loadedFileCount <= 0) {
            return DictionarySnapshot.empty();
        }
        Translate_AllinOne.LOGGER.info(
                "Loaded {}. files={}, exact={}, patterns={}",
                dictionaryLabel,
                loadedFileCount,
                exactTranslations.size(),
                patternEntries.size()
        );
        return DictionarySnapshot.of(exactTranslations, patternEntries);
    }

    private static void mergeEntries(
            Map<String, String> sourceToTranslation,
            Map<String, DictionaryEntry> exactTranslations,
            List<PatternEntry> patternEntries,
            String sourceId
    ) {
        if (sourceToTranslation == null || exactTranslations == null || patternEntries == null) {
            return;
        }

        for (Map.Entry<String, String> entry : sourceToTranslation.entrySet()) {
            String source = normalizeSourceText(entry.getKey());
            String translation = normalizeTranslationText(entry.getValue());
            if (source.isBlank() || translation.isBlank()) {
                continue;
            }

            if (containsPlaceholder(source)) {
                PatternEntry patternEntry = compilePatternEntry(source, translation, sourceId, patternEntries.size());
                if (patternEntry != null) {
                    patternEntries.add(patternEntry);
                }
            } else {
                exactTranslations.putIfAbsent(
                        source.toLowerCase(Locale.ROOT),
                        new DictionaryEntry(translation, sourceId)
                );
            }
        }
    }

    private static String resolveDictionarySourceId(Path dictionaryPath) {
        if (dictionaryPath == null || dictionaryPath.getFileName() == null) {
            return "";
        }

        String fileName = dictionaryPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.contains("skill")) {
            return "skills";
        }
        if (fileName.contains("item") || fileName.startsWith("skb_")) {
            return "items";
        }
        return "";
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

    private static PatternEntry compilePatternEntry(
            String sourceTemplate,
            String translationTemplate,
            String sourceId,
            int ordinal
    ) {
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
                List.copyOf(placeholders),
                selectRequiredIndexToken(sourceTemplate),
                sourceId == null ? "" : sourceId,
                ordinal
        );
    }

    private static String selectRequiredIndexToken(String sourceTemplate) {
        List<String> literalTokens = extractLiteralIndexTokens(sourceTemplate);
        String selected = "";
        for (String token : literalTokens) {
            if (token.length() > selected.length()) {
                selected = token;
            }
        }
        return selected;
    }

    private static List<String> extractLiteralIndexTokens(String sourceTemplate) {
        if (sourceTemplate == null || sourceTemplate.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sourceTemplate);
        int lastEnd = 0;
        while (matcher.find()) {
            collectIndexTokens(sourceTemplate.substring(lastEnd, matcher.start()), tokens);
            lastEnd = matcher.end();
        }
        collectIndexTokens(sourceTemplate.substring(lastEnd), tokens);
        return tokens;
    }

    private static Set<String> extractIndexTokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        Set<String> tokens = new HashSet<>();
        collectIndexTokens(value, tokens);
        return tokens;
    }

    private static void collectIndexTokens(String value, java.util.Collection<String> output) {
        if (value == null || value.isBlank() || output == null) {
            return;
        }

        StringBuilder token = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                token.appendCodePoint(Character.toLowerCase(codePoint));
            } else {
                flushIndexToken(token, output);
            }
            offset += Character.charCount(codePoint);
        }
        flushIndexToken(token, output);
    }

    private static void flushIndexToken(StringBuilder token, java.util.Collection<String> output) {
        if (token.length() >= 2) {
            output.add(token.toString());
        }
        token.setLength(0);
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
                || previousCodePoint == '−'
                || previousCodePoint == '±';
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
        return value.replace(' ', ' ')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeTranslationText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (!normalized.isEmpty() && normalized.replaceAll("§[0-9a-fk-or]", "").trim().isEmpty()) {
            return "";
        }
        return normalized;
    }

    private static final class LruLookupCache {
        private final LinkedHashMap<String, CachedLookupResult> map;
        private final int maxSize;

        LruLookupCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedLookupResult> eldest) {
                    return size() > LruLookupCache.this.maxSize;
                }
            };
        }

        synchronized CachedLookupResult get(String key) {
            return map.get(key);
        }

        synchronized void put(String key, CachedLookupResult value) {
            map.put(key, value);
        }

        synchronized void clear() {
            map.clear();
        }
    }

    private record PatternEntry(
            Pattern pattern,
            String translationTemplate,
            List<String> placeholders,
            String requiredToken,
            String sourceId,
            int ordinal
    ) {
    }

    enum MatchType {
        EXACT,
        PATTERN
    }

    record LookupResult(
            String translation,
            MatchType matchType,
            String sourceId
    ) {
        LookupResult(String translation, MatchType matchType) {
            this(translation, matchType, "");
        }
    }

    private record DictionaryEntry(
            String translation,
            String sourceId
    ) {
    }

    private record CachedLookupResult(
            long snapshotVersion,
            LookupResult lookupResult
    ) {
    }

    private record SnapshotState(
            DictionarySnapshot snapshot,
            long version
    ) {
    }

    private record DictionarySnapshot(
            Map<String, DictionaryEntry> exactTranslations,
            List<PatternEntry> patternEntries,
            Map<String, List<PatternEntry>> patternEntriesByRequiredToken,
            List<PatternEntry> fallbackPatternEntries
    ) {
        private List<PatternEntry> candidatePatternEntries(String normalizedSource) {
            if (patternEntries.isEmpty()) {
                return List.of();
            }

            Set<String> sourceTokens = extractIndexTokens(normalizedSource);
            if (sourceTokens.isEmpty() || patternEntriesByRequiredToken.isEmpty()) {
                return fallbackPatternEntries.isEmpty() ? List.of() : fallbackPatternEntries;
            }

            List<PatternEntry> candidates = new ArrayList<>();
            Set<PatternEntry> seen = new HashSet<>();
            for (String sourceToken : sourceTokens) {
                List<PatternEntry> tokenEntries = patternEntriesByRequiredToken.get(sourceToken);
                if (tokenEntries == null || tokenEntries.isEmpty()) {
                    continue;
                }
                for (PatternEntry entry : tokenEntries) {
                    if (seen.add(entry)) {
                        candidates.add(entry);
                    }
                }
            }
            for (PatternEntry entry : fallbackPatternEntries) {
                if (seen.add(entry)) {
                    candidates.add(entry);
                }
            }
            if (candidates.isEmpty()) {
                return List.of();
            }
            candidates.sort(Comparator.comparingInt(PatternEntry::ordinal));
            return candidates;
        }

        private static DictionarySnapshot empty() {
            return new DictionarySnapshot(Map.of(), List.of(), Map.of(), List.of());
        }

        private static DictionarySnapshot of(
                Map<String, DictionaryEntry> exactTranslations,
                List<PatternEntry> patternEntries
        ) {
            PatternIndex patternIndex = buildPatternIndex(patternEntries);
            return new DictionarySnapshot(
                    Map.copyOf(exactTranslations),
                    List.copyOf(patternEntries),
                    patternIndex.patternEntriesByRequiredToken(),
                    patternIndex.fallbackPatternEntries()
            );
        }

        private static PatternIndex buildPatternIndex(List<PatternEntry> patternEntries) {
            if (patternEntries == null || patternEntries.isEmpty()) {
                return new PatternIndex(Map.of(), List.of());
            }

            Map<String, List<PatternEntry>> mutableIndex = new LinkedHashMap<>();
            List<PatternEntry> fallbackEntries = new ArrayList<>();
            for (PatternEntry entry : patternEntries) {
                if (entry == null || entry.requiredToken() == null || entry.requiredToken().isBlank()) {
                    fallbackEntries.add(entry);
                    continue;
                }
                mutableIndex.computeIfAbsent(entry.requiredToken(), ignored -> new ArrayList<>()).add(entry);
            }

            Map<String, List<PatternEntry>> immutableIndex = new LinkedHashMap<>();
            for (Map.Entry<String, List<PatternEntry>> entry : mutableIndex.entrySet()) {
                immutableIndex.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new PatternIndex(Map.copyOf(immutableIndex), List.copyOf(fallbackEntries));
        }
    }

    private record PatternIndex(
            Map<String, List<PatternEntry>> patternEntriesByRequiredToken,
            List<PatternEntry> fallbackPatternEntries
    ) {
    }
}
