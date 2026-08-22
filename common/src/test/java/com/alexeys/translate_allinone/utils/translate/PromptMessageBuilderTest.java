package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptMessageBuilderTest {
    @Test
    void resolvesRequestedLanguageInDefaultPrompt() {
        String prompt = PromptMessageBuilder.getDefaultPrompt("chat_output", "Japanese");

        assertTrue(prompt.contains("Japanese"));
        assertFalse(prompt.contains("{target_language}"));
    }

    @Test
    void fallsBackToChineseForBlankLanguage() {
        String prompt = PromptMessageBuilder.getDefaultPrompt("item", " ");

        assertTrue(prompt.contains("Chinese"));
    }

    @Test
    void resolvesRequestedLanguageInOverride() {
        String prompt = PromptMessageBuilder.applyPromptOverride(
                "scoreboard",
                "default",
                Map.of("scoreboard", "Translate into {target_language}"),
                "Korean"
        );

        assertTrue(prompt.contains("Korean"));
        assertFalse(prompt.contains("{target_language}"));
    }
}
