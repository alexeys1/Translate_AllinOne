package com.cedarxuesong.translate_allinone.utils.translate;

import java.util.Map;

final class ItemTranslationPromptSupport {
    private static final String ROUTE_KEY = "item";

    private ItemTranslationPromptSupport() {
    }

    static String buildSystemPrompt(String targetLanguage, String suffix, Map<String, String> overrides) {
        String basePrompt = "Translate JSON values into " + targetLanguage
                + ". Output valid JSON only, keys unchanged.\n"
                + "\n"
                + "Rules:\n"
                + "1) Natural " + targetLanguage + " game UI phrasing (e.g. 61格/3秒). Multiline: keep meaning and line order.\n"
                + "2) Preserve exactly: § codes, <s0> </s0>, {d1}, %s %d %f, URLs, numbers, <...>, {...}, \\n, \\t.\n"
                + "3) Never add, drop, or reorder tags, placeholders, or text fragments.\n"
                + "4) \"take (N) damage from (X)\" means the subject RECEIVES/SUFFERS damage from X, NEVER translates as dealing damage to X.\n"
                + "5) If unsure, keep original. Output JSON only.";
        String resolved = PromptMessageBuilder.applyPromptOverride(ROUTE_KEY, basePrompt, overrides, targetLanguage);
        return PromptMessageBuilder.appendSystemPromptSuffix(resolved, suffix);
    }
}
