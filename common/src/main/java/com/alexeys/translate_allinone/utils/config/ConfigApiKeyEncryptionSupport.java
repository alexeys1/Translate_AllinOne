package com.alexeys.translate_allinone.utils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Optional;
import java.util.UUID;

public final class ConfigApiKeyEncryptionSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigApiKeyEncryptionSupport() {
    }

    public static Result encryptApiKeysForBackup(String rawJson, Optional<UUID> uuid, Optional<byte[]> key) {
        if (rawJson == null || rawJson.isBlank()) {
            return Result.unchanged(rawJson);
        }
        try {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject()) {
                return Result.unchanged(rawJson);
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject providerManager = root.getAsJsonObject("providerManager");
            if (providerManager == null) {
                return Result.unchanged(rawJson);
            }
            JsonElement providersElement = providerManager.get("providers");
            if (providersElement == null || !providersElement.isJsonArray()) {
                return Result.unchanged(rawJson);
            }
            boolean changed = false;
            for (JsonElement providerElement : providersElement.getAsJsonArray()) {
                if (!providerElement.isJsonObject()) {
                    continue;
                }
                JsonObject provider = providerElement.getAsJsonObject();
                JsonElement apiKeyElement = provider.get("api_key");
                if (!isPlaintextApiKey(apiKeyElement)) {
                    continue;
                }
                if (uuid.isEmpty() || key.isEmpty()) {
                    return Result.retry(rawJson);
                }
                String raw = apiKeyElement.getAsString();
                String providerId = provider.has("id") && provider.get("id").isJsonPrimitive()
                        ? provider.get("id").getAsString()
                        : "";
                String cipher = ApiKeyCipher.encrypt(raw, uuid.get(), providerId, key.get());
                provider.addProperty("api_key", cipher);
                JsonArray entries = ensureApiKeyEntries(provider);
                if (!contains(entries, cipher)) {
                    entries.add(cipher);
                }
                changed = true;
            }
            if (changed) {
                return Result.changed(GSON.toJson(root));
            }
            return Result.unchanged(rawJson);
        } catch (RuntimeException e) {
            return Result.retry(rawJson);
        }
    }

    private static boolean isPlaintextApiKey(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return false;
        }
        String value = element.getAsString();
        return value != null && !value.isBlank() && !ApiKeyCipher.isCiphertext(value);
    }

    private static JsonArray ensureApiKeyEntries(JsonObject provider) {
        JsonElement entriesElement = provider.get("api_key_entries");
        if (entriesElement != null && entriesElement.isJsonArray()) {
            return entriesElement.getAsJsonArray();
        }
        JsonArray entries = new JsonArray();
        provider.add("api_key_entries", entries);
        return entries;
    }

    private static boolean contains(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && value.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    public record Result(String json, boolean changed, boolean retryNeeded) {
        public static Result unchanged(String json) {
            return new Result(json, false, false);
        }

        public static Result changed(String json) {
            return new Result(json, true, false);
        }

        public static Result retry(String json) {
            return new Result(json, false, true);
        }
    }
}
