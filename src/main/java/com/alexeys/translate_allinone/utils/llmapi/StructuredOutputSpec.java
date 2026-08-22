package com.alexeys.translate_allinone.utils.llmapi;

import java.util.LinkedHashMap;
import java.util.Map;

public record StructuredOutputSpec(String name, Map<String, Object> schema) {
    public StructuredOutputSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Structured output schema name is required.");
        }
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("Structured output schema is required.");
        }
        name = name.trim();
        schema = Map.copyOf(new LinkedHashMap<>(schema));
    }
}
