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
    void mergesSystemPromptIntoUserWhenSystemRoleUnsupported() {
        List<OpenAIRequest.Message> messages = PromptMessageBuilder.buildMessages(
                "Translate player-composed Minecraft chat input into Chinese.",
                "Hello",
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
    void forcedOutputContractShapesPlainTextRoutes() {
        for (String routeKey : List.of("chat_input_translate", "chat_output")) {
            String contract = PromptMessageBuilder.getForcedOutputContract(routeKey);
            assertTrue(contract.contains("Output shape:"), routeKey);
            assertTrue(contract.contains("plain-text translation only"), routeKey);
            assertFalse(contract.contains("JSON object"), routeKey);
        }
    }

    @Test
    void forcedOutputContractShapesIndexedMapRoutes() {
        for (String routeKey : List.of("item", "wynn_npc_dialogue", "wynntils_task_tracker")) {
            String contract = PromptMessageBuilder.getForcedOutputContract(routeKey);
            assertTrue(contract.contains("Output shape:"), routeKey);
            assertTrue(contract.contains("Return exactly one JSON object"), routeKey);
            assertTrue(contract.contains("{\"1\":\"translated text\"}"), routeKey);
        }
    }

    @Test
    void forcedOutputContractIsBlankForComponentProtocolRoutes() {
        for (String routeKey : List.of("scoreboard", "sign_book", "entity_text", "screen_ui", "other_translations", "unknown_route")) {
            assertTrue(PromptMessageBuilder.getForcedOutputContract(routeKey).isBlank(), routeKey);
            assertEquals("base", PromptMessageBuilder.appendForcedOutputContract("base", routeKey), routeKey);
        }
    }

    @Test
    void defaultPromptsDropDuplicatedForcedSections() {
        assertDefaultStructure("item", true);
        assertDefaultStructure("scoreboard", true);
        assertDefaultStructure("sign_book", true);
        assertDefaultStructure("entity_text", true);
        assertDefaultStructure("chat_output", true);
        assertDefaultStructure("chat_input_translate", false);
        assertDefaultStructure("wynn_npc_dialogue", true);
        assertDefaultStructure("wynntils_task_tracker", true);
        assertTrue(PromptMessageBuilder.getDefaultPromptTemplate("wynn_npc_dialogue").contains("Story & Wording:"));
        assertTrue(PromptMessageBuilder.getDefaultPromptTemplate("item").contains("\"take N damage from X\" means"));
        assertFalse(PromptMessageBuilder.getDefaultPromptTemplate("screen_ui").contains("Protected data:"));
        assertFalse(PromptMessageBuilder.getDefaultPromptTemplate("other_translations").contains("Protected data:"));
        assertFalse(PromptMessageBuilder.getDefaultPromptTemplate("chat_output").contains("JSON"));
        assertFalse(PromptMessageBuilder.getDefaultPromptTemplate("chat_input_translate").contains("JSON"));
    }

    private static void assertDefaultStructure(String routeKey, boolean expectWording) {
        String prompt = PromptMessageBuilder.getDefaultPromptTemplate(routeKey);
        assertTrue(prompt.contains("Task:"), routeKey + " should contain Task:");
        assertTrue(prompt.contains("Output contract:"), routeKey + " should contain Output contract:");
        assertFalse(prompt.contains("Protected data:"), routeKey + " must not duplicate the forced protected data contract");
        assertFalse(prompt.contains("Failure rule:"), routeKey + " must not duplicate the forced failure rule");
        assertEquals(expectWording, prompt.contains("Wording:"), routeKey + " Wording presence mismatch");
    }

    @Test
    void forcedContractsSurvivePromptOverrideInOrder() {
        String prompt = PromptMessageBuilder.appendForcedContracts("Custom chat output prompt.", "chat_output");

        assertTrue(prompt.contains("Custom chat output prompt."));
        int shape = prompt.indexOf("Output shape:");
        int protectedData = prompt.indexOf("Chat output protected data:");
        assertTrue(shape >= 0);
        assertTrue(protectedData > shape);
        assertTrue(prompt.indexOf("If an uncertain term is a normal natural-language word") > protectedData);
    }

    @Test
    void forcedContractsSurviveSystemRoleUnsupportedMerge() {
        String systemPrompt = PromptMessageBuilder.appendForcedContracts("Custom Wynn prompt.", "wynn_npc_dialogue");
        List<OpenAIRequest.Message> messages = PromptMessageBuilder.buildMessages(
                systemPrompt,
                "{\"1\":\"Hello\"}",
                false
        );

        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).role);
        assertTrue(messages.get(0).content.contains("Output shape:"));
        assertTrue(messages.get(0).content.contains("Wynn NPC dialogue protected data:"));
    }
}
