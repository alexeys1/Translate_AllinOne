package com.alexeys.translate_allinone.utils.translate;

import java.util.Map;

final class ItemTranslationPromptSupport {
    private static final String ROUTE_KEY = "item";

    private ItemTranslationPromptSupport() {
    }

    static String buildSystemPrompt(String targetLanguage, String suffix, Map<String, String> overrides) {
        String basePrompt = PromptMessageBuilder.getDefaultPrompt(ROUTE_KEY, targetLanguage);
        String resolved = PromptMessageBuilder.applyPromptOverride(ROUTE_KEY, basePrompt, overrides, targetLanguage);
        return PromptMessageBuilder.appendSystemPromptSuffix(resolved, suffix);
    }
}
