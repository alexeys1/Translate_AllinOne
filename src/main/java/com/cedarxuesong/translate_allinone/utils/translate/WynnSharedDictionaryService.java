package com.cedarxuesong.translate_allinone.utils.translate;

public final class WynnSharedDictionaryService {
    public enum MatchType {
        EXACT,
        PATTERN,
        PREFIX
    }

    public record LookupResult(
            String translation,
            String dictionaryId,
            MatchType matchType
    ) {
        static LookupResult miss() {
            return new LookupResult("", "", null);
        }

        boolean hit() {
            return translation != null && !translation.isBlank();
        }
    }

    private final WynncraftItemLocalDictionary itemDictionary;
    private final WynncraftSkillLocalDictionary skillDictionary;
    private final WynntilsQuestLocalDictionary questDictionary;
    private final WynnDialogueLocalDictionary dialogueDictionary;

    private WynnSharedDictionaryService() {
        this(
                WynncraftItemLocalDictionary.getInstance(),
                WynncraftSkillLocalDictionary.getInstance()
        );
    }

    WynnSharedDictionaryService(
            WynncraftItemLocalDictionary itemDictionary,
            WynncraftSkillLocalDictionary skillDictionary
    ) {
        this.itemDictionary = itemDictionary;
        this.skillDictionary = skillDictionary;
        this.questDictionary = WynntilsQuestLocalDictionary.getInstance();
        this.dialogueDictionary = WynnDialogueLocalDictionary.getInstance();
    }

    public static WynnSharedDictionaryService getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void loadAll() {
        itemDictionary.load();
        skillDictionary.load();
        questDictionary.load();
        dialogueDictionary.load();
    }

    LookupResult lookupItemLine(String sourceText) {
        // Skills are more specific for Wynn ability tooltips and should win over generic item fragments.
        LookupResult skillLookup = toLookupResult(skillDictionary.lookupTranslation(sourceText), "skills");
        if (skillLookup.hit()) {
            return skillLookup;
        }
        return toLookupResult(itemDictionary.lookupTranslation(sourceText), "items");
    }

    boolean hasItemDictionaryEntries() {
        return itemDictionary.hasEntries() || skillDictionary.hasEntries();
    }

    LookupResult lookupQuestText(String sourceText) {
        return toLookupResult(questDictionary.lookupTranslation(sourceText), "quests");
    }

    boolean hasQuestDictionaryEntries() {
        return questDictionary.hasEntries();
    }

    boolean hasPreparedDialogueTranslation(String preparedDialogue) {
        return dialogueDictionary.hasDialogueTranslation(preparedDialogue);
    }

    LookupResult lookupPreparedDialogue(String preparedDialogue) {
        String translation = dialogueDictionary.findDialogueTranslation(preparedDialogue);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.EXACT);
    }

    LookupResult lookupPreparedDialogueByPrefix(String preparedDialogue) {
        String translation = dialogueDictionary.findDialogueByPrefix(preparedDialogue);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.PREFIX);
    }

    boolean hasPreparedNpcTranslation(String preparedNpcName) {
        return dialogueDictionary.hasNpcTranslation(preparedNpcName);
    }

    LookupResult lookupPreparedNpc(String preparedNpcName) {
        String translation = dialogueDictionary.findNpcTranslation(preparedNpcName);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.EXACT);
    }

    private static LookupResult toLookupResult(
            WynncraftPlaceholderDictionary.LookupResult lookupResult,
            String dictionaryId
    ) {
        if (lookupResult == null
                || lookupResult.translation() == null
                || lookupResult.translation().isBlank()) {
            return LookupResult.miss();
        }

        MatchType matchType = switch (lookupResult.matchType()) {
            case EXACT -> MatchType.EXACT;
            case PATTERN -> MatchType.PATTERN;
        };
        return new LookupResult(lookupResult.translation(), dictionaryId, matchType);
    }

    private static final class Holder {
        private static final WynnSharedDictionaryService INSTANCE = new WynnSharedDictionaryService();
    }
}
