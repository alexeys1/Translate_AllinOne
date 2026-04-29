package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;

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
    private final WynncraftPlaceholderDictionary itemSkillDictionary;
    private final WynntilsQuestLocalDictionary questDictionary;
    private final WynnDialogueLocalDictionary dialogueDictionary;

    private WynnSharedDictionaryService() {
        this(new WynncraftPlaceholderDictionary(
                DictionaryFileSelectionSupport::resolveItemSkillDictionaryPathsByLookupPriority,
                "wynncraft-item-skill-dictionary"
        ));
    }

    WynnSharedDictionaryService(WynncraftPlaceholderDictionary itemSkillDictionary) {
        this(
                null,
                null,
                itemSkillDictionary
        );
    }

    WynnSharedDictionaryService(
            WynncraftItemLocalDictionary itemDictionary,
            WynncraftSkillLocalDictionary skillDictionary
    ) {
        this(
                itemDictionary,
                skillDictionary,
                null
        );
    }

    private WynnSharedDictionaryService(
            WynncraftItemLocalDictionary itemDictionary,
            WynncraftSkillLocalDictionary skillDictionary,
            WynncraftPlaceholderDictionary itemSkillDictionary
    ) {
        this.itemDictionary = itemDictionary;
        this.skillDictionary = skillDictionary;
        this.itemSkillDictionary = itemSkillDictionary;
        this.questDictionary = WynntilsQuestLocalDictionary.getInstance();
        this.dialogueDictionary = WynnDialogueLocalDictionary.getInstance();
    }

    public static WynnSharedDictionaryService getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void loadAll() {
        if (itemSkillDictionary != null) {
            itemSkillDictionary.load();
        } else {
            itemDictionary.load();
            skillDictionary.load();
        }
        questDictionary.load();
        dialogueDictionary.load();
    }

    LookupResult lookupItemLine(String sourceText) {
        if (!isDictionaryEnabled()) {
            return LookupResult.miss();
        }
        if (itemSkillDictionary != null) {
            return toLookupResult(itemSkillDictionary.lookupTranslation(sourceText), "item_skill");
        }
        // Skills are more specific for Wynn ability tooltips and should win over generic item fragments.
        LookupResult skillLookup = toLookupResult(skillDictionary.lookupTranslation(sourceText), "skills");
        if (skillLookup.hit()) {
            return skillLookup;
        }
        return toLookupResult(itemDictionary.lookupTranslation(sourceText), "items");
    }

    long getItemSkillVersion() {
        if (itemSkillDictionary != null) {
            return itemSkillDictionary.getVersion();
        }
        return 0L;
    }

    boolean hasItemDictionaryEntries() {
        if (!isDictionaryEnabled()) {
            return false;
        }
        if (itemSkillDictionary != null) {
            return itemSkillDictionary.hasEntries();
        }
        return itemDictionary.hasEntries() || skillDictionary.hasEntries();
    }

    LookupResult lookupQuestText(String sourceText) {
        if (!isDictionaryEnabled()) {
            return LookupResult.miss();
        }
        return toLookupResult(questDictionary.lookupTranslation(sourceText), "quests");
    }

    boolean hasQuestDictionaryEntries() {
        if (!isDictionaryEnabled()) {
            return false;
        }
        return questDictionary.hasEntries();
    }

    boolean hasPreparedDialogueTranslation(String preparedDialogue) {
        if (!isDictionaryEnabled()) {
            return false;
        }
        return dialogueDictionary.hasDialogueTranslation(preparedDialogue);
    }

    LookupResult lookupPreparedDialogue(String preparedDialogue) {
        if (!isDictionaryEnabled()) {
            return LookupResult.miss();
        }
        String translation = dialogueDictionary.findDialogueTranslation(preparedDialogue);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.EXACT);
    }

    LookupResult lookupPreparedDialogueByPrefix(String preparedDialogue) {
        if (!isDictionaryEnabled()) {
            return LookupResult.miss();
        }
        String translation = dialogueDictionary.findDialogueByPrefix(preparedDialogue);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.PREFIX);
    }

    boolean hasPreparedNpcTranslation(String preparedNpcName) {
        if (!isDictionaryEnabled()) {
            return false;
        }
        return dialogueDictionary.hasNpcTranslation(preparedNpcName);
    }

    LookupResult lookupPreparedNpc(String preparedNpcName) {
        if (!isDictionaryEnabled()) {
            return LookupResult.miss();
        }
        String translation = dialogueDictionary.findNpcTranslation(preparedNpcName);
        if (translation == null || translation.isBlank()) {
            return LookupResult.miss();
        }
        return new LookupResult(translation, "dialogues", MatchType.EXACT);
    }

    private static boolean isDictionaryEnabled() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            return config == null || config.dictionary == null || config.dictionary.isEnabled();
        } catch (IllegalStateException ignored) {
            return true;
        }
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
        String resolvedDictionaryId = lookupResult.sourceId() == null || lookupResult.sourceId().isBlank()
                ? dictionaryId
                : lookupResult.sourceId();
        return new LookupResult(lookupResult.translation(), resolvedDictionaryId, matchType);
    }

    private static final class Holder {
        private static final WynnSharedDictionaryService INSTANCE = new WynnSharedDictionaryService();
    }
}
