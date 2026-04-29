package com.cedarxuesong.translate_allinone.utils.translate;

final class WynncraftItemLocalDictionary {
    private final WynncraftPlaceholderDictionary delegate;

    private WynncraftItemLocalDictionary() {
        this(new WynncraftPlaceholderDictionary(
                DictionaryFileSelectionSupport::resolveItemSkillDictionaryPaths,
                "wynncraft-items-dictionary"
        ));
    }

    WynncraftItemLocalDictionary(WynncraftPlaceholderDictionary delegate) {
        this.delegate = delegate;
    }

    static WynncraftItemLocalDictionary getInstance() {
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
        private static final WynncraftItemLocalDictionary INSTANCE = new WynncraftItemLocalDictionary();
    }
}
