package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;

import java.util.List;
import java.util.Map;

public final class PromptMessageBuilder {
    private PromptMessageBuilder() {
    }

    public static List<OpenAIRequest.Message> buildMessages(String systemPrompt, String userPrompt, boolean supportsSystemMessage) {
        return buildMessages(systemPrompt, userPrompt, supportsSystemMessage, null, true);
    }

    public static List<OpenAIRequest.Message> buildMessages(
            String systemPrompt,
            String userPrompt,
            boolean supportsSystemMessage,
            String modelId
    ) {
        return buildMessages(systemPrompt, userPrompt, supportsSystemMessage, modelId, true);
    }

    public static List<OpenAIRequest.Message> buildMessages(
            String systemPrompt,
            String userPrompt,
            boolean supportsSystemMessage,
            String modelId,
            boolean injectSystemPromptIntoUserMessage
    ) {
        String safeSystem = systemPrompt == null ? "" : systemPrompt;
        String safeUser = userPrompt == null ? "" : userPrompt;

        if (!supportsSystemMessage) {
            if (injectSystemPromptIntoUserMessage) {
                String mergedPrompt = mergeSystemIntoUserPrompt(safeSystem, safeUser);
                return List.of(new OpenAIRequest.Message("user", mergedPrompt));
            }
            return List.of(new OpenAIRequest.Message("user", safeUser));
        }

        return List.of(
                new OpenAIRequest.Message("system", safeSystem),
                new OpenAIRequest.Message("user", safeUser)
        );
    }

    public static String appendSystemPromptSuffix(String basePrompt, String suffix) {
        String safeBase = basePrompt == null ? "" : basePrompt;
        if (suffix == null || suffix.isBlank()) {
            return safeBase;
        }
        if (suffix.startsWith("\n")) {
            return safeBase + suffix;
        }
        return safeBase + "\n" + suffix;
    }

    public static String applyPromptOverride(String routeKey, String defaultBasePrompt, Map<String, String> overrides, String targetLanguage) {
        if (overrides != null && !overrides.isEmpty()) {
            String override = overrides.get(routeKey);
            if (override != null && !override.isBlank()) {
                String language = targetLanguage == null || targetLanguage.isBlank() ? "Chinese" : targetLanguage;
                return override.replace("{target_language}", language);
            }
        }
        return defaultBasePrompt;
    }

    public static String applyPromptOverride(String routeKey, String defaultBasePrompt, Map<String, String> overrides) {
        return applyPromptOverride(routeKey, defaultBasePrompt, overrides, "Chinese");
    }

    public static String getDefaultPrompt(String routeKey, String targetLanguage) {
        String language = targetLanguage == null || targetLanguage.isBlank() ? "Chinese" : targetLanguage;
        return getDefaultPromptTemplate(routeKey).replace("{target_language}", language);
    }

    public static String getDefaultPromptTemplate(String routeKey) {
        String targetLanguage = "{target_language}";
        return switch (routeKey) {
            case "item" -> "Translate Minecraft item tooltip text for an in-game client into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Keep every key, id, entry count, and entry order unchanged; translate text only.\n"
                    + "2) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN} placeholders, %s/%d/%f, URLs, numbers, item ids, commands, \\n, and \\t.\n"
                    + "3) Use concise, natural game UI wording. Preserve explicit line breaks; normal words may be reordered naturally around protected tokens.\n"
                    + "4) \"take N damage from X\" means the subject receives damage from X, never deals damage to X.\n"
                    + "5) Keep an uncertain term unchanged. No Markdown, explanations, or extra fields.";
            case "scoreboard" -> "Translate Minecraft scoreboard labels and status values into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Keep every key, id, entry count, and entry order unchanged; translate text only.\n"
                    + "2) Use short, scannable game UI wording. Do not add labels, padding, or commentary.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, scores, URLs, numbers, \\n, and \\t.\n"
                    + "4) Keep an uncertain term unchanged. No Markdown or extra fields.";
            case "sign_book" -> "Translate Minecraft sign and book text into " + targetLanguage + " for an in-game client.\n"
                    + "Return only the JSON response required by the request; never output Minecraft Component JSON.\n"
                    + "Rules:\n"
                    + "1) Translate literal text only. Preserve all ids, keys, formatting markers, placeholders, URLs, numbers, commands, item ids, and proper nouns.\n"
                    + "2) Keep sign faces short and keep their line breaks exactly. For books, preserve paragraph boundaries and explicit line breaks.\n"
                    + "3) Do not invent, remove, or alter interactive data represented by the request.\n"
                    + "4) Keep an uncertain span unchanged. No Markdown, explanations, or extra fields.";
            case "entity_text" -> "Translate Minecraft entity names and text displays into " + targetLanguage + " for an in-game client.\n"
                    + "Return only the JSON response required by the request; never output Minecraft Component JSON.\n"
                    + "Rules:\n"
                    + "1) Translate literal text only. Preserve all ids, keys, formatting markers, placeholders, URLs, numbers, commands, item ids, and proper nouns.\n"
                    + "2) Name tags must stay short. Text displays may use natural sentence order, but do not add line breaks.\n"
                    + "3) Keep an uncertain span unchanged. No Markdown, explanations, or extra fields.";
            case "chat_output" -> "Translate a received Minecraft chat message into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Preserve speaker names, server commands, item ids, URLs, numbers, and uncertain proper nouns.\n"
                    + "2) Preserve exactly: Minecraft formatting codes, every <sN> tag, {dN}/{gN}, %s/%d/%f, \\n, and \\t.\n"
                    + "3) Translate ordinary wording naturally while keeping protected tokens attached to their intended meaning.\n"
                    + "4) Keep an uncertain span unchanged. No Markdown, explanations, or extra fields.";
            case "chat_input_translate" -> "Translate player-composed Minecraft chat input into " + targetLanguage + ".\n"
                    + "Output only the final translated message, with no Markdown, quotes, or explanation.\n"
                    + "Rules:\n"
                    + "1) Preserve commands beginning with /, player names, item ids, URLs, numbers, and uncertain proper nouns.\n"
                    + "2) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, \\n, and \\t.\n"
                    + "3) Use natural chat phrasing and change punctuation or spacing only when the translation requires it.\n"
                    + "4) Keep an uncertain span unchanged.";
            case "wynn_npc_dialogue" -> "Translate WynnCraft NPC dialogue and quest narration into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Preserve story meaning, speaker tone, paragraph breaks, and established WynnCraft terminology.\n"
                    + "2) Keep character names, place names such as Ragni and Troms, and [bracketed] item names unchanged.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, URLs, numbers, \\n, \\t, and decorative text.\n"
                    + "4) Render poetic lines naturally in " + targetLanguage + " without adding commentary. Keep an uncertain term unchanged.";
            case "wynntils_task_tracker" -> "Translate Wynntils task-tracker objectives and progress text into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Keep every key, id, entry count, and entry order unchanged; translate text only.\n"
                    + "2) Use concise objective wording. Preserve counts, coordinates, names, and progress indicators exactly.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, URLs, numbers, \\n, and \\t.\n"
                    + "4) Keep an uncertain term unchanged. No Markdown or extra fields.";
            default -> "";
        };
    }

    private static String mergeSystemIntoUserPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return userPrompt == null ? "" : userPrompt;
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\nInput:\n" + userPrompt;
    }
}
