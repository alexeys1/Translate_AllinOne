package com.cedarxuesong.translate_allinone.utils.translate;

final class WynntilsQuestLocalDictionary {
    private final WynncraftPlaceholderDictionary delegate = new WynncraftPlaceholderDictionary(
            WynncraftDictionaryInstaller.resolveConfigDictionaryFile("quests.json"),
            "wynncraft-quests-dictionary"
    );

    private WynntilsQuestLocalDictionary() {
    }

    static WynntilsQuestLocalDictionary getInstance() {
        return Holder.INSTANCE;
    }

    void load() {
        delegate.load();
    }

    WynncraftPlaceholderDictionary.LookupResult lookupTranslation(String sourceText) {
        return delegate.lookupTranslation(sourceText);
    }

    String findTranslation(String sourceText) {
        return delegate.findTranslation(sourceText);
    }

    boolean hasEntries() {
        return delegate.hasEntries();
    }

    private static final class Holder {
        private static final WynntilsQuestLocalDictionary INSTANCE = new WynntilsQuestLocalDictionary();
    }
}
