package com.alexeys.translate_allinone.utils.llmapi.ollama;

public class OllamaChatResponse {
    public String model;
    public String done_reason;
    public Boolean done;
    public Integer prompt_eval_count;
    public Integer eval_count;
    public Message message;

    public static class Message {
        public String content;
    }
}
