package com.alexeys.translate_allinone.utils.llmapi.openai;

import java.util.List;
import java.util.Map;

/**
 * 代表发送到OpenAI Chat Completions API的请求体。
 */
public class OpenAIRequest {
    public String model;
    public List<Message> messages;
    public double temperature;
    public boolean stream;
    public ResponseFormat response_format;

    public OpenAIRequest(String model, List<Message> messages, double temperature, boolean stream, ResponseFormat responseFormat) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.stream = stream;
        this.response_format = responseFormat;
    }

    public OpenAIRequest(String model, List<Message> messages, double temperature, boolean stream) {
        this(model, messages, temperature, stream, null);
    }

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class ResponseFormat {
        public String type;
        public JsonSchema json_schema;

        public ResponseFormat(String type) {
            this.type = type;
        }

        public ResponseFormat(String schemaName, Map<String, Object> schema) {
            this.type = "json_schema";
            this.json_schema = new JsonSchema(schemaName, schema, true);
        }
    }

    public static class JsonSchema {
        public String name;
        public Map<String, Object> schema;
        public boolean strict;

        public JsonSchema(String name, Map<String, Object> schema, boolean strict) {
            this.name = name;
            this.schema = schema;
            this.strict = strict;
        }
    }
}
