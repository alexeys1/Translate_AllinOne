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

        if (supportsSystemMessage) {
            return List.of(
                    new OpenAIRequest.Message("system", safeSystem),
                    new OpenAIRequest.Message("user", safeUser)
            );
        }

        return List.of(new OpenAIRequest.Message("user", safeUser));
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
                String t = targetLanguage == null || targetLanguage.isBlank() ? "Chinese" : targetLanguage;
                return override.replace("{target_language}", t);
            }
        }
        return defaultBasePrompt;
    }

    public static String applyPromptOverride(String routeKey, String defaultBasePrompt, Map<String, String> overrides) {
        return applyPromptOverride(routeKey, defaultBasePrompt, overrides, "Chinese");
    }

    public static String getDefaultPromptTemplate(String routeKey) {
        String t = "{target_language}";
        return switch (routeKey) {
            case "item" -> "Translate JSON values into " + t
                    + ". Output valid JSON only, keys unchanged.\n"
                    + "\n"
                    + "Rules:\n"
                    + "1) Natural " + t + " game UI phrasing (e.g. 61格/3秒). Multiline: keep meaning and line order.\n"
                    + "2) Preserve exactly: § codes, <s0> </s0>, {d1}, %s %d %f, URLs, numbers, <...>, {...}, \\n, \\t.\n"
                    + "3) Never add, drop, or reorder tags, placeholders, or text fragments.\n"
                    + "4) \"take (N) damage from (X)\" means the subject RECEIVES/SUFFERS damage from X, NEVER translates as dealing damage to X.\n"
                    + "5) If unsure, keep original. Output JSON only.";
            case "scoreboard" -> "You are a deterministic JSON value translator.\n"
                    + "Target language: " + t + ".\n"
                    + "\n"
                    + "Input is a JSON object with string keys and string values.\n"
                    + "Output must be one valid JSON object only.\n"
                    + "\n"
                    + "Rules:\n"
                    + "1) Keep all keys unchanged.\n"
                    + "2) Keep key count unchanged.\n"
                    + "3) Translate values only.\n"
                    + "4) Preserve tokens exactly: §a §l §r %s %d %f {d1} URLs numbers <...> {...} \\n \\t.\n"
                    + "5) If unsure for a value, keep that value unchanged.\n"
                    + "6) No extra text outside JSON.";
            case "chat_output" -> "You are a deterministic translation engine.\n"
                    + "Target language: " + t + ".\n"
                    + "\n"
                    + "Rules (highest priority first):\n"
                    + "1) Output only the final translated text. No explanation, markdown, or quotes.\n"
                    + "2) Preserve style tags exactly: <s0>...</s0>, <s1>...</s1>, ... Keep the same tag ids, counts, and order.\n"
                    + "3) Preserve tokens exactly: § color/style codes, placeholders (%s %d %f {d1}), URLs, numbers, <...>, {...}, \\n, \\t.\n"
                    + "4) If a term is uncertain, keep only that term unchanged and still translate surrounding text.\n"
                    + "5) If any rule cannot be guaranteed, return the original input unchanged.";
            case "chat_input_translate" -> "You are a deterministic translation engine.\n"
                    + "Target language: " + t + ".\n"
                    + "\n"
                    + "Rules (highest priority first):\n"
                    + "1) Output only the final translated text. No explanation, markdown, or quotes.\n"
                    + "2) Preserve tokens exactly: § color/style codes, placeholders (%s %d %f {d1}), URLs, numbers, command prefix (/), <...>, {...}, \\n, \\t.\n"
                    + "3) If a term is uncertain, keep only that term unchanged and still translate surrounding text.\n"
                    + "4) Keep punctuation and spacing stable unless translation naturally requires changes.\n"
                    + "5) If any rule cannot be guaranteed, return the original input unchanged.";
            case "wynn_npc_dialogue" -> "Translate WynnCraft NPC dialogue into " + t
                    + ". Output valid JSON only, keys unchanged.\n"
                    + "\n"
                    + "Rules:\n"
                    + "1) Natural " + t + ", keep original meaning and paragraph breaks. No mixed-language.\n"
                    + "2) Do not translate: place names (Ragni, Troms), character names, [bracketed] items.\n"
                    + "3) Poetic lines: use rhythmic " + t + ".\n"
                    + "4) Preserve exactly: § codes, {tokens}, %s %d %f, URLs, numbers, <...>, <s0> </s0>, \\n, \\t, zalgo text.\n"
                    + "5) If unsure, keep original. No extra text.";
            case "wynntils_task_tracker" -> "You are a deterministic JSON value translator.\n"
                    + "Target language: " + t + ".\n"
                    + "\n"
                    + "Input is a JSON object with string keys and string values.\n"
                    + "Output must be one valid JSON object only.\n"
                    + "\n"
                    + "Rules:\n"
                    + "1) Keep all keys unchanged.\n"
                    + "2) Keep key count unchanged.\n"
                    + "3) Translate values only.\n"
                    + "4) Preserve tokens exactly: §a §l §r %s %d %f {d1} URLs numbers <...> {...} <s0> </s0> \\n \\t.\n"
                    + "5) If unsure for a value, keep that value unchanged.\n"
                    + "6) No extra text outside JSON.";
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
