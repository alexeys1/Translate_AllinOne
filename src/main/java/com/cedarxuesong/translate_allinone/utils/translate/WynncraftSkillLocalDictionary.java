package com.cedarxuesong.translate_allinone.utils.translate;

final class WynncraftSkillLocalDictionary {
    private final WynncraftPlaceholderDictionary delegate = new WynncraftPlaceholderDictionary(
            WynncraftDictionaryInstaller.resolveConfigDictionaryFile("skills.json"),
            "wynncraft-skills-dictionary"
    );

    private WynncraftSkillLocalDictionary() {
    }

    static WynncraftSkillLocalDictionary getInstance() {
        return Holder.INSTANCE;
    }

    String findTranslation(String sourceText) {
        return delegate.findTranslation(sourceText);
    }

    private static final class Holder {
        private static final WynncraftSkillLocalDictionary INSTANCE = new WynncraftSkillLocalDictionary();
    }
}
