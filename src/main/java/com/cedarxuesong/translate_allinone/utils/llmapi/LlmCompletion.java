package com.cedarxuesong.translate_allinone.utils.llmapi;

import java.util.Locale;

public record LlmCompletion(String content, String finishReason) {
    public LlmCompletion {
        content = content == null ? "" : content;
        finishReason = finishReason == null ? "" : finishReason.trim();
    }

    public boolean wasTruncated() {
        String normalized = finishReason.toLowerCase(Locale.ROOT);
        return normalized.equals("length")
                || normalized.contains("max_tokens")
                || normalized.contains("max_output_tokens")
                || normalized.contains("token_limit")
                || normalized.contains("length_limit");
    }
}
