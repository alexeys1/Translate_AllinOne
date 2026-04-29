package com.cedarxuesong.translate_allinone.utils.translate;

final class WynncraftSkillLocalDictionary {
    private final WynncraftPlaceholderDictionary delegate;

    private WynncraftSkillLocalDictionary() {
        this(new WynncraftPlaceholderDictionary(
                DictionaryFileSelectionSupport::resolveItemSkillDictionaryPaths,
                "wynncraft-skills-dictionary"
        ));
    }

    WynncraftSkillLocalDictionary(WynncraftPlaceholderDictionary delegate) {
        this.delegate = delegate;
    }

    static WynncraftSkillLocalDictionary getInstance() {
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
        private static final WynncraftSkillLocalDictionary INSTANCE = new WynncraftSkillLocalDictionary();
    }
}
