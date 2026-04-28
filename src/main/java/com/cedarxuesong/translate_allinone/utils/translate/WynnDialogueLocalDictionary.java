package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WynnDialogueLocalDictionary {
    private static final String CONFIG_FILE_NAME = "dialogues.json";
    private static final int MIN_PREFIX_MATCH_LENGTH = 5;
    private static final int MIN_VISIBLE_PREFIX_LENGTH = 20;
    private static final double MIN_PREFIX_PROGRESS_RATIO = 0.55D;

    private volatile DictionarySnapshot snapshot = DictionarySnapshot.empty();
    private volatile boolean loadAttempted;
    private static volatile Path testDictionaryFileOverride;

    private WynnDialogueLocalDictionary() {
    }

    public static WynnDialogueLocalDictionary getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void load() {
        DictionarySnapshot loadedSnapshot = loadSnapshot();
        snapshot = loadedSnapshot;
        loadAttempted = true;
    }

    public boolean hasNpcTranslation(String npcName) {
        return findNpcTranslation(npcName) != null;
    }

    public String findNpcTranslation(String npcName) {
        ensureLoaded();
        return lookupTranslation(snapshot.npcLookups(), buildLookupKeys(npcName));
    }

    public boolean hasDialogueTranslation(String dialogue) {
        return findDialogueTranslation(dialogue) != null;
    }

    public String findDialogueTranslation(String dialogue) {
        ensureLoaded();
        return lookupTranslation(snapshot.dialogueLookups(), buildDialogueLookupKeys(dialogue));
    }

    public String findDialogueByPrefix(String dialogue) {
        ensureLoaded();

        String rawInput = normalizeLookupText(dialogue);
        String searchInput = normalizeDialogueSearchText(dialogue);
        if (rawInput.length() < MIN_PREFIX_MATCH_LENGTH || searchInput.isBlank() || searchInput.length() < MIN_PREFIX_MATCH_LENGTH) {
            return null;
        }

        String lowerInput = searchInput.toLowerCase(Locale.ROOT);
        String bestCandidateSearch = null;
        String bestTranslation = null;
        int bestDistance = Integer.MAX_VALUE;
        int matchCount = 0;

        for (DialoguePrefixEntry entry : snapshot.prefixDialogues().values()) {
            String candidateSearch = entry.searchSource();
            String translation = entry.translation();
            if (candidateSearch == null || candidateSearch.isBlank() || translation == null || translation.isBlank()) {
                continue;
            }

            String lowerCandidate = candidateSearch.toLowerCase(Locale.ROOT);
            if (lowerCandidate.equals(lowerInput)) {
                return translation;
            }

            if (!lowerCandidate.startsWith(lowerInput)) {
                continue;
            }

            matchCount++;
            int distance = Math.abs(candidateSearch.length() - searchInput.length());
            if (distance < bestDistance) {
                bestCandidateSearch = candidateSearch;
                bestTranslation = translation;
                bestDistance = distance;
            }
        }

        if (bestCandidateSearch == null || bestTranslation == null || bestTranslation.isBlank()) {
            return null;
        }

        boolean partialMatch = searchInput.length() < bestCandidateSearch.length();
        if (partialMatch && rawInput.length() < MIN_VISIBLE_PREFIX_LENGTH) {
            return null;
        }

        double progressRatio = (double) searchInput.length() / (double) bestCandidateSearch.length();
        if (partialMatch && progressRatio < MIN_PREFIX_PROGRESS_RATIO) {
            return null;
        }
        if (matchCount > 1 && searchInput.length() < 20) {
            return null;
        }

        if (searchInput.length() >= bestCandidateSearch.length()) {
            return bestTranslation;
        }

        return cropTranslatedPrefix(bestCandidateSearch, searchInput, bestTranslation);
    }

    private synchronized void ensureLoaded() {
        if (!loadAttempted) {
            load();
        }
    }

    private DictionarySnapshot loadSnapshot() {
        Path configuredDictionaryPath = resolveDictionaryFilePath();
        if (configuredDictionaryPath != null && Files.exists(configuredDictionaryPath)) {
            try (var reader = Files.newBufferedReader(configuredDictionaryPath, StandardCharsets.UTF_8)) {
                return parseSnapshot(reader, configuredDictionaryPath.toString());
            } catch (IOException | RuntimeException e) {
                Translate_AllinOne.LOGGER.error(
                        "Failed to load Wynn dialogue local dictionary from config path: {}",
                        configuredDictionaryPath,
                        e
                );
            }
        }
        Translate_AllinOne.LOGGER.warn(
                "Wynn dialogue local dictionary config file not found: {}",
                configuredDictionaryPath
        );
        return DictionarySnapshot.empty();
    }

    private DictionarySnapshot parseSnapshot(java.io.Reader reader, String sourceDescription) {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (rootElement == null || !rootElement.isJsonObject()) {
            Translate_AllinOne.LOGGER.warn(
                    "Wynn dialogue local dictionary has an invalid root object. source={}",
                    sourceDescription
            );
            return DictionarySnapshot.empty();
        }

        JsonObject root = rootElement.getAsJsonObject();
        Map<String, String> npcLookups = new LinkedHashMap<>();
        Map<String, String> dialogueLookups = new LinkedHashMap<>();
        Map<String, DialoguePrefixEntry> prefixDialogues = new LinkedHashMap<>();

        JsonObject npcNames = getObject(root, "npc_names");
        if (npcNames != null) {
            for (Map.Entry<String, JsonElement> entry : npcNames.entrySet()) {
                addLookupVariants(npcLookups, buildLookupKeys(entry.getKey()), getString(entry.getValue()));
            }
        }

        JsonObject quests = getObject(root, "quests");
        if (quests != null) {
            for (Map.Entry<String, JsonElement> questEntry : quests.entrySet()) {
                if (questEntry.getValue() == null || !questEntry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject dialogues = questEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> dialogueEntry : dialogues.entrySet()) {
                    String original = dialogueEntry.getKey();
                    String translation = getString(dialogueEntry.getValue());
                    addLookupVariants(dialogueLookups, buildDialogueLookupKeys(original), translation);

                    String normalizedTranslation = normalizeTranslationText(translation);
                    if (normalizedTranslation.isBlank()) {
                        continue;
                    }

                    String normalizedOriginal = normalizeLookupText(original);
                    String canonicalKey = normalizeDialogueSearchText(normalizedOriginal);
                    if (!canonicalKey.isBlank()) {
                        prefixDialogues.putIfAbsent(
                                canonicalKey,
                                new DialoguePrefixEntry(normalizedOriginal, canonicalKey, normalizedTranslation)
                        );
                    }

                    String deduplicatedLeadingWord = collapseDuplicatedLeadingWord(normalizedOriginal);
                    String deduplicatedCanonicalKey = normalizeDialogueSearchText(deduplicatedLeadingWord);
                    if (!deduplicatedLeadingWord.isBlank() && !deduplicatedCanonicalKey.isBlank()) {
                        prefixDialogues.putIfAbsent(
                                deduplicatedCanonicalKey,
                                new DialoguePrefixEntry(deduplicatedLeadingWord, deduplicatedCanonicalKey, normalizedTranslation)
                        );
                    }
                }
            }
        }

        Translate_AllinOne.LOGGER.info(
                "Loaded Wynn dialogue local dictionary from {}. npcNames={}, dialogues={}",
                sourceDescription,
                npcLookups.size(),
                prefixDialogues.size()
        );
        return new DictionarySnapshot(
                Collections.unmodifiableMap(new LinkedHashMap<>(npcLookups)),
                Collections.unmodifiableMap(new LinkedHashMap<>(dialogueLookups)),
                Collections.unmodifiableMap(new LinkedHashMap<>(prefixDialogues))
        );
    }

    private Path resolveDictionaryFilePath() {
        Path override = testDictionaryFileOverride;
        if (override != null) {
            return override;
        }
        return WynncraftDictionaryInstaller.resolveConfigDictionaryFile(CONFIG_FILE_NAME);
    }

    static void setTestDictionaryFileOverride(Path path) {
        testDictionaryFileOverride = path;
    }

    static void clearTestDictionaryFileOverride() {
        testDictionaryFileOverride = null;
    }

    private static JsonObject getObject(JsonObject root, String memberName) {
        if (root == null || memberName == null || memberName.isBlank()) {
            return null;
        }

        JsonElement element = root.get(memberName);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String getString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    private static void addLookupVariants(Map<String, String> lookupMap, Set<String> keys, String translation) {
        String normalizedTranslation = normalizeTranslationText(translation);
        if (lookupMap == null || normalizedTranslation.isBlank()) {
            return;
        }

        for (String key : keys) {
            lookupMap.putIfAbsent(key, normalizedTranslation);
        }
    }

    private static String lookupTranslation(Map<String, String> lookupMap, Set<String> keys) {
        if (lookupMap == null || lookupMap.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            String translation = lookupMap.get(key);
            if (translation != null && !translation.isBlank()) {
                return translation;
            }
        }
        return null;
    }

    private static Set<String> buildLookupKeys(String value) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String rawTrimmed = value == null ? "" : value.replace('\u00A0', ' ').trim();
        if (!rawTrimmed.isBlank()) {
            String collapsedRaw = collapseWhitespace(rawTrimmed);
            keys.add(collapsedRaw);
            keys.add(collapsedRaw.toLowerCase(Locale.ROOT));
        }

        String normalized = normalizeLookupText(value);
        if (!normalized.isBlank()) {
            keys.add(normalized);
            keys.add(normalized.toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static Set<String> buildDialogueLookupKeys(String value) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(buildLookupKeys(value));
        String searchText = normalizeDialogueSearchText(value);
        if (!searchText.isBlank()) {
            keys.add(searchText);
            keys.add(searchText.toLowerCase(Locale.ROOT));
        }

        String deduplicatedLeadingWord = collapseDuplicatedLeadingWord(normalizeLookupText(value));
        if (!deduplicatedLeadingWord.isBlank()) {
            keys.add(deduplicatedLeadingWord);
            keys.add(deduplicatedLeadingWord.toLowerCase(Locale.ROOT));

            String deduplicatedSearchText = normalizeDialogueSearchText(deduplicatedLeadingWord);
            if (!deduplicatedSearchText.isBlank()) {
                keys.add(deduplicatedSearchText);
                keys.add(deduplicatedSearchText.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private static String normalizeLookupText(String value) {
        if (value == null) {
            return "";
        }

        return collapseWhitespace(
                value.replace('\u00A0', ' ')
                        .replace('\u2018', '\'')
                        .replace('\u2019', '\'')
                        .replace('\u201C', '"')
                        .replace('\u201D', '"')
                        .trim()
        );
    }

    private static String normalizeTranslationText(String value) {
        return normalizeLookupText(value);
    }

    private static String normalizeDialogueSearchText(String value) {
        String normalized = normalizeLookupText(value);
        if (normalized.isBlank()) {
            return "";
        }

        String expanded = normalized
                .replaceAll("(?i)\\b([A-Za-z]+)'ll\\b", "$1 will")
                .replaceAll("(?i)\\b([A-Za-z]+)n't\\b", "$1 not")
                .replaceAll("(?i)\\b([A-Za-z]+)'re\\b", "$1 are")
                .replaceAll("(?i)\\b([A-Za-z]+)'ve\\b", "$1 have")
                .replaceAll("(?i)\\b([A-Za-z]+)'m\\b", "$1 am")
                .replaceAll("(?i)\\b([A-Za-z]+)'d\\b", "$1 would");
        return expanded.replaceAll("\\s+", "").trim();
    }

    private static String collapseDuplicatedLeadingWord(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        int firstSpace = value.indexOf(' ');
        if (firstSpace <= 0 || firstSpace >= value.length() - 1) {
            return "";
        }

        String firstWord = value.substring(0, firstSpace);
        String remaining = value.substring(firstSpace + 1).trim();
        if (remaining.length() <= firstWord.length()) {
            return "";
        }

        if (!remaining.regionMatches(true, 0, firstWord, 0, firstWord.length())) {
            return "";
        }

        int secondWordEnd = remaining.indexOf(' ');
        if (secondWordEnd != firstWord.length()) {
            return "";
        }

        return firstWord + ' ' + remaining.substring(secondWordEnd + 1).trim();
    }

    private static String collapseWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String cropTranslatedPrefix(String fullSource, String partialSource, String translatedFull) {
        if (fullSource == null || partialSource == null || translatedFull == null || translatedFull.isBlank()) {
            return translatedFull;
        }
        if (partialSource.length() >= fullSource.length()) {
            return translatedFull;
        }

        double ratio = Math.max(0.05D, Math.min(1.0D, (double) partialSource.length() / (double) fullSource.length()));
        int endIndex = Math.max(1, Math.min(translatedFull.length(), (int) Math.ceil(translatedFull.length() * ratio)));

        if (translatedFull.indexOf(' ') >= 0) {
            while (endIndex < translatedFull.length()
                    && !Character.isWhitespace(translatedFull.charAt(endIndex))
                    && !isBoundaryPunctuation(translatedFull.charAt(endIndex))) {
                endIndex++;
            }
            while (endIndex > 0 && Character.isWhitespace(translatedFull.charAt(endIndex - 1))) {
                endIndex--;
            }
        }

        return translatedFull.substring(0, endIndex).trim();
    }

    private static boolean isBoundaryPunctuation(char value) {
        return switch (value) {
            case '.', ',', '!', '?', ';', ':', '"', '\'', '，', '。', '！', '？', '；', '：', '、' -> true;
            default -> false;
        };
    }

    private record DictionarySnapshot(
            Map<String, String> npcLookups,
            Map<String, String> dialogueLookups,
            Map<String, DialoguePrefixEntry> prefixDialogues
    ) {
        private static DictionarySnapshot empty() {
            return new DictionarySnapshot(Map.of(), Map.of(), Map.of());
        }
    }

    private record DialoguePrefixEntry(
            String rawSource,
            String searchSource,
            String translation
    ) {
    }

    private static final class Holder {
        private static final WynnDialogueLocalDictionary INSTANCE = new WynnDialogueLocalDictionary();
    }
}
