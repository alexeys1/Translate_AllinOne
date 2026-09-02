package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;

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
            return List.of(new OpenAIRequest.Message("user", mergeSystemIntoUserPrompt(safeSystem, safeUser)));
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
            case "item" -> "Task:\n"
                    + "Translate the Minecraft tooltip text into " + targetLanguage + ".\n"
                    + "\n"
                    + "Output contract:\n"
                    + "Return only the JSON object required by the request.\n"
                    + "Do not return Markdown, explanations, extra fields, or arrays.\n"
                    + "Keep all requested ids and entry counts exactly.\n"
                    + "\n"
                    + "Protected data:\n"
                    + "Preserve every style tag, placeholder, URL, command, item id, number, unit, formatting code, \\n, and \\t exactly.\n"
                    + "\n"
                    + "Failure rule:\n"
                    + "Do not explain, do not echo the source, do not invent tokens.\n"
                    + "If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged.\n"
                    + "\n"
                    + "Wording:\n"
                    + "Use concise, natural game UI wording.\n"
                    + "\"take N damage from X\" means the subject receives damage from X, never deals damage to X.";
            case "scoreboard" -> "Translate Minecraft scoreboard labels and status values into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Keep every key, id, entry count, and entry order unchanged; translate text only.\n"
                    + "2) Use short, scannable game UI wording. Do not add labels, padding, or commentary.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, scores, URLs, numbers, \\n, and \\t.\n"
                    + "4) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged. No Markdown or extra fields.";
            case "sign_book" -> "Translate Minecraft sign and book text into " + targetLanguage + " for an in-game client.\n"
                    + "Return only the JSON response required by the request; never output Minecraft Component JSON.\n"
                    + "Rules:\n"
                    + "1) Translate literal text only. Preserve all ids, keys, formatting markers, placeholders, URLs, numbers, commands, item ids, and proper nouns.\n"
                    + "2) Keep sign faces short and keep their line breaks exactly. For books, preserve paragraph boundaries and explicit line breaks.\n"
                    + "3) Do not invent, remove, or alter interactive data represented by the request.\n"
                    + "4) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged. No Markdown, explanations, or extra fields.";
            case "entity_text" -> "Translate Minecraft entity names and text displays into " + targetLanguage + " for an in-game client.\n"
                    + "Return only the JSON response required by the request; never output Minecraft Component JSON.\n"
                    + "Rules:\n"
                    + "1) Translate literal text only. Preserve all ids, keys, formatting markers, placeholders, URLs, numbers, commands, item ids, and proper nouns.\n"
                    + "2) Name tags must stay short. Text displays may use natural sentence order, but do not add line breaks.\n"
                    + "3) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged. No Markdown, explanations, or extra fields.";
            case "chat_output" -> "Translate a received Minecraft chat message into " + targetLanguage + ".\n"
                    + "Rules:\n"
                    + "1) Speaker names and NPC names are already removed from the input; never add or guess them. Preserve server commands, item ids, URLs, numbers, and uncertain proper nouns.\n"
                    + "2) Preserve exactly: Minecraft formatting codes, every <sN> tag, {dN}/{gN}, %s/%d/%f, \\n, and \\t.\n"
                    + "3) Translate ordinary wording naturally while keeping protected tokens attached to their intended meaning.\n"
                    + "4) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged. No Markdown, explanations, or extra fields.";
            case "chat_input_translate" -> "Translate player-composed Minecraft chat input into " + targetLanguage + ".\n"
                    + "Output only the final translated message, with no Markdown, quotes, or explanation.\n"
                    + "Rules:\n"
                    + "1) Preserve commands beginning with /, player names, item ids, URLs, numbers, and uncertain proper nouns.\n"
                    + "2) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, \\n, and \\t.\n"
                    + "3) Use natural chat phrasing and change punctuation or spacing only when the translation requires it.\n"
                    + "4) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged.";
            case "wynn_npc_dialogue" -> "Translate WynnCraft NPC dialogue and quest narration into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Preserve story meaning, speaker tone, paragraph breaks, and established WynnCraft terminology.\n"
                    + "2) Keep character names, place names such as Ragni and Troms, and [bracketed] item names unchanged.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, URLs, numbers, \\n, \\t, and decorative text.\n"
                    + "4) Render poetic lines naturally in " + targetLanguage + " without adding commentary. If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged.";
            case "wynntils_task_tracker" -> "Translate Wynntils task-tracker objectives and progress text into " + targetLanguage + ".\n"
                    + "Return only the JSON response required by the request.\n"
                    + "Rules:\n"
                    + "1) Keep every key, id, entry count, and entry order unchanged; translate text only.\n"
                    + "2) Use concise objective wording. Preserve counts, coordinates, names, and progress indicators exactly.\n"
                    + "3) Preserve exactly: Minecraft formatting codes, <sN> tags, {dN}/{gN}, %s/%d/%f, URLs, numbers, \\n, and \\t.\n"
                    + "4) If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged. No Markdown or extra fields.";
            case "screen_ui" -> "Translate static third-party Minecraft configuration UI text into " + targetLanguage
                    + ". Preserve visible text only, formatting codes, URLs, paths, commands, key bindings, placeholders, numbers, units, decorative glyphs, module identities, configuration keys, and persistent values. Return only the required JSON response with no Markdown or extra fields.";
            default -> "";
        };
    }

    public static String getForcedProtectedDataContract(String routeKey) {
        String failureRule = "\nFailure rule: Do not explain, do not echo the source, do not invent tokens. Do not add Markdown or extra fields. "
                + "If an uncertain term is a normal natural-language word, translate or transliterate it; do not keep the whole sentence unchanged.";
        return switch (routeKey) {
            case "item" -> "\nTooltip protected data: "
                    + "Preserve exactly every <sN> and </sN> style tag. "
                    + "Preserve exactly every {dN}, {gN}, {valueN}, URL, command, item id, number, unit, %s/%d/%f, Minecraft formatting code, \\n, and \\t. "
                    + "Style tags may move with target-language word order only when the route allows it. "
                    + "Keep explicit line breaks; normal words may be reordered naturally around protected tokens."
                    + failureRule;
            case "scoreboard" -> "\nScoreboard protected data: "
                    + "Keep every key, id, entry count, and entry order unchanged; translate text only. "
                    + "Preserve exactly Minecraft formatting codes, <sN> tags, {dN}, {gN}, {valueN}, %s/%d/%f, scores, URLs, numbers, \\n, and \\t. "
                    + "Use short, scannable game UI wording; do not add labels, padding, or commentary."
                    + failureRule;
            case "sign_book" -> "\nSign/book protected data: "
                    + "Preserve all ids, keys, formatting markers, placeholders such as {dN}, {gN}, and {valueN}, URLs, numbers, commands, item ids, and proper nouns. "
                    + "Keep sign faces short and keep their line breaks exactly. For books, preserve paragraph boundaries and explicit line breaks. "
                    + "Do not invent, remove, or alter interactive data represented by the request."
                    + failureRule;
            case "entity_text" -> "\nEntity text protected data: "
                    + "Preserve all ids, keys, formatting markers, placeholders such as {dN}, {gN}, and {valueN}, URLs, numbers, commands, item ids, and proper nouns. "
                    + "Name tags must stay short. Text displays may use natural sentence order, but do not add line breaks."
                    + failureRule;
            case "chat_output" -> "\nChat output protected data: "
                    + "Preserve server commands, item ids, URLs, numbers, uncertain proper nouns, Minecraft formatting codes, every <sN> tag, {dN}, {gN}, {valueN}, %s/%d/%f, \\n, and \\t. "
                    + "Speaker names and NPC names are already removed from the input; never add or guess them. "
                    + "Translate ordinary wording naturally while keeping protected tokens attached to their intended meaning."
                    + failureRule;
            case "chat_input_translate" -> "\nChat input protected data: "
                    + "Preserve commands beginning with /, player names, item ids, URLs, numbers, and uncertain proper nouns. "
                    + "Preserve exactly Minecraft formatting codes, <sN> tags, {dN}, {gN}, {valueN}, %s/%d/%f, \\n, and \\t. "
                    + "Use natural chat phrasing and change punctuation or spacing only when the translation requires it."
                    + failureRule;
            case "wynn_npc_dialogue" -> "\nWynn NPC dialogue protected data: "
                    + "Preserve character names, place names such as Ragni and Troms, [bracketed] item names, paragraph breaks, and established WynnCraft terminology. "
                    + "Preserve exactly Minecraft formatting codes, <sN> tags, {dN}, {gN}, {valueN}, %s/%d/%f, URLs, numbers, \\n, \\t, and decorative text. "
                    + "Render poetic lines naturally without adding commentary."
                    + failureRule;
            case "wynntils_task_tracker" -> "\nWynntils task tracker protected data: "
                    + "Keep every key, id, entry count, and entry order unchanged; translate text only. "
                    + "Preserve counts, coordinates, names, and progress indicators exactly. "
                    + "Preserve exactly Minecraft formatting codes, <sN> tags, {dN}, {gN}, {valueN}, %s/%d/%f, URLs, numbers, \\n, and \\t. "
                    + "Use concise objective wording."
                    + failureRule;
            case "screen_ui" -> "\nScreen UI protected data: "
                    + "Preserve visible text only, formatting codes, URLs, paths, commands, key bindings, placeholders such as {dN}, {gN}, and {valueN}, numbers, units, decorative glyphs, module identities, configuration keys, and persistent values."
                    + failureRule;
            default -> "\nProtected data: "
                    + "Preserve exactly every <sN> and </sN> style tag, {dN}, {gN}, {valueN}, URL, command, item id, number, unit, %s/%d/%f, Minecraft formatting code, \\n, and \\t. "
                    + "Do not invent, remove, duplicate, or replace protected tokens."
                    + failureRule;
        };
    }

    public static String appendForcedProtectedDataContract(String prompt, String routeKey) {
        String contract = getForcedProtectedDataContract(routeKey);
        if (contract == null || contract.isBlank()) {
            return prompt;
        }
        String safePrompt = prompt == null ? "" : prompt;
        return safePrompt + contract;
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
