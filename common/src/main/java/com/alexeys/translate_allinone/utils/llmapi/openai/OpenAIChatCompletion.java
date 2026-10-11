package com.alexeys.translate_allinone.utils.llmapi.openai;

import java.util.List;

public class OpenAIChatCompletion {
    public List<Choice> choices;
    public String model;
    public String object;
    public Usage usage;

    public static class Choice {
        public String finish_reason;
        public Message delta;
        public Message message;
    }

    public static class Message {
        public String content;
    }

    public static class Usage {
        public Integer prompt_tokens;
        public Integer completion_tokens;
        public Integer input_tokens;
        public Integer output_tokens;
        public Integer total_tokens;

        public boolean hasCounts() {
            return inputTokens() > 0L || outputTokens() > 0L;
        }

        public long inputTokens() {
            Integer explicitInput = prompt_tokens != null ? prompt_tokens : input_tokens;
            if (explicitInput != null) {
                return Math.max(0, explicitInput);
            }
            Integer explicitOutput = completion_tokens != null ? completion_tokens : output_tokens;
            if (explicitOutput != null) {
                return total_tokens == null ? 0L : Math.max(0, total_tokens - explicitOutput);
            }
            return total_tokens == null ? 0L : Math.max(0, total_tokens);
        }

        public long outputTokens() {
            Integer explicitOutput = completion_tokens != null ? completion_tokens : output_tokens;
            if (explicitOutput != null) {
                return Math.max(0, explicitOutput);
            }
            Integer explicitInput = prompt_tokens != null ? prompt_tokens : input_tokens;
            if (explicitInput != null) {
                return total_tokens == null ? 0L : Math.max(0, total_tokens - explicitInput);
            }
            return 0L;
        }
    }
}
