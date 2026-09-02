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

    @Test
    void forcedProtectedDataContractCoversOtherModules() {
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("chat_output").contains("Chat output protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("chat_input_translate").contains("Chat input protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("sign_book").contains("Sign/book protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("entity_text").contains("Entity text protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("scoreboard").contains("Scoreboard protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("wynn_npc_dialogue").contains("Wynn NPC dialogue protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("wynntils_task_tracker").contains("Wynntils task tracker protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("screen_ui").contains("Screen UI protected data:"));
        assertTrue(PromptMessageBuilder.getForcedProtectedDataContract("other_translations").contains("Protected data:"));
    }

    @Test
    void appendForcedProtectedDataContractSurvivesPromptOverride() {
        String prompt = PromptMessageBuilder.appendForcedProtectedDataContract(
                "Custom chat output prompt.",
                "chat_output"
        );
        assertTrue(prompt.contains("Custom chat output prompt."));
        assertTrue(prompt.contains("Chat output protected data:"));
        assertTrue(prompt.contains("If an uncertain term is a normal natural-language word"));
    }

}
