package com.cedarxuesong.translate_allinone.utils.llmapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

public final class LlmPayloadJsonSupport {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LlmPayloadJsonSupport() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static JsonElement toJsonTree(Object value) {
        return GSON.toJsonTree(value);
    }
}
