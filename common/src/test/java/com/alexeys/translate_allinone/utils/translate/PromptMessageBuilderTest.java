package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void keepsSystemPromptWhenSystemRoleUnsupportedEvenIfInjectionDisabled() {
        List<OpenAIRequest.Message> messages = PromptMessageBuilder.buildMessages(
                "Translate player-composed Minecraft chat input into Chinese.",
                "Hello",
                false,
                null,
                false
        );

        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).role);
        assertTrue(messages.get(0).content.contains("Translate player-composed Minecraft chat input into Chinese."));
        assertTrue(messages.get(0).content.contains("Hello"));
    }
}
