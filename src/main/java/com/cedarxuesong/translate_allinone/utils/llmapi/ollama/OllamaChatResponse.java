package com.cedarxuesong.translate_allinone.utils.llmapi.ollama;

public class OllamaChatResponse {
    public String model;
    public String done_reason;
    public Message message;

    public static class Message {
        public String content;
    }
}
