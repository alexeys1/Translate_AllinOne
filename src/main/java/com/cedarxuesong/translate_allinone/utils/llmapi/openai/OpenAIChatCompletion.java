package com.cedarxuesong.translate_allinone.utils.llmapi.openai;

import java.util.List;

public class OpenAIChatCompletion {
    public List<Choice> choices;
    public String model;
    public String object;

    public static class Choice {
        public String finish_reason;
        public Message delta;
        public Message message;
    }

    public static class Message {
        public String content;
    }
}
