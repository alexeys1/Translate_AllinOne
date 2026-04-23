package com.cedarxuesong.translate_allinone.utils.translate;

final class ItemTranslationPromptSupport {
    private ItemTranslationPromptSupport() {
    }

    static String buildSystemPrompt(String targetLanguage, String suffix) {
        String basePrompt = "Translate JSON string values into " + targetLanguage + ".\n"
                + "Return one valid JSON object only.\n"
                + "\n"
                + "Rules:\n"
                + "1) Keep every key unchanged, keep the same key count, and translate values only.\n"
                + "2) Preserve tags/placeholders exactly: <s0>...</s0>, § codes, %s %d %f, {d1}, URLs, numbers, <...>, {...}, \\n, \\t.\n"
                + "3) Never add, drop, rename, or reorder tags, placeholder ids, or text fragments.\n"
                + "4) Multiline values may reflow, but must keep full meaning and order.\n"
                + "5) For Chinese, prefer natural game UI phrasing such as 61格/3秒.\n"
                + "6) If unsure, leave that value unchanged.\n"
                + "7) Output JSON only; no extra text.";
        return PromptMessageBuilder.appendSystemPromptSuffix(basePrompt, suffix);
    }
}
