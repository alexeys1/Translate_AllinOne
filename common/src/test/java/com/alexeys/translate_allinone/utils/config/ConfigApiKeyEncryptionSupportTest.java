package com.alexeys.translate_allinone.utils.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigApiKeyEncryptionSupportTest {

    @Test
    void encryptsPlaintextApiKeyAndPreservesUnknownFields() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];
        String rawJson = """
                {
                  "providerManager": {
                    "providers": [
                      {
                        "id": "provider-a",
                        "api_key": "sk-plaintext-secret",
                        "futureProviderField": "keep"
                      }
                    ]
                  },
                  "topLevelFuture": 123
                }
                """;

        ConfigApiKeyEncryptionSupport.Result result = ConfigApiKeyEncryptionSupport.encryptApiKeysForBackup(
                rawJson,
                Optional.of(uuid),
                Optional.of(key)
        );

        assertTrue(result.changed());
        assertFalse(result.retryNeeded());
        assertFalse(result.json().contains("sk-plaintext-secret"));

        JsonObject root = JsonParser.parseString(result.json()).getAsJsonObject();
        JsonObject provider = root.getAsJsonObject("providerManager")
                .getAsJsonArray("providers")
                .get(0)
                .getAsJsonObject();
        String cipher = provider.get("api_key").getAsString();
        assertTrue(ApiKeyCipher.isCiphertext(cipher));
        assertEquals("sk-plaintext-secret", ApiKeyCipher.decrypt(cipher, uuid, "provider-a", key).orElseThrow());
        assertEquals("keep", provider.get("futureProviderField").getAsString());
        assertEquals(123, root.get("topLevelFuture").getAsInt());

        JsonArray entries = provider.getAsJsonArray("api_key_entries");
        boolean found = false;
        for (JsonElement entry : entries) {
            if (cipher.equals(entry.getAsString())) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    void leavesExistingCiphertextUnchanged() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];
        String cipher = ApiKeyCipher.encrypt("sk-secret", uuid, "provider-a", key);
        String rawJson = """
                {
                  "providerManager": {
                    "providers": [
                      {
                        "id": "provider-a",
                        "api_key": "%s",
                        "api_key_entries": ["%s"]
                      }
                    ]
                  }
                }
                """.formatted(cipher, cipher);

        ConfigApiKeyEncryptionSupport.Result result = ConfigApiKeyEncryptionSupport.encryptApiKeysForBackup(
                rawJson,
                Optional.of(uuid),
                Optional.of(key)
        );

        assertFalse(result.changed());
        assertFalse(result.retryNeeded());
        assertEquals(rawJson, result.json());
    }

    @Test
    void keepsPlaintextWhenUuidAndKeyAreUnavailable() {
        String rawJson = """
                {
                  "providerManager": {
                    "providers": [
                      {
                        "id": "provider-a",
                        "api_key": "sk-offline-plaintext"
                      }
                    ]
                  }
                }
                """;

        ConfigApiKeyEncryptionSupport.Result result = ConfigApiKeyEncryptionSupport.encryptApiKeysForBackup(
                rawJson,
                Optional.empty(),
                Optional.empty()
        );

        assertFalse(result.changed());
        assertTrue(result.retryNeeded());
        assertEquals(rawJson, result.json());
    }

    @Test
    void returnsUnchangedWhenNoPlaintextApiKeyExists() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];
        String rawJson = """
                {
                  "providerManager": {
                    "providers": [
                      {
                        "id": "provider-a",
                        "api_key": ""
                      }
                    ]
                  }
                }
                """;

        ConfigApiKeyEncryptionSupport.Result result = ConfigApiKeyEncryptionSupport.encryptApiKeysForBackup(
                rawJson,
                Optional.of(uuid),
                Optional.of(key)
        );

        assertFalse(result.changed());
        assertFalse(result.retryNeeded());
        assertEquals(rawJson, result.json());
    }

    @Test
    void returnsRetryForMalformedJson() {
        String rawJson = "{ not valid json";

        ConfigApiKeyEncryptionSupport.Result result = ConfigApiKeyEncryptionSupport.encryptApiKeysForBackup(
                rawJson,
                Optional.empty(),
                Optional.empty()
        );

        assertFalse(result.changed());
        assertTrue(result.retryNeeded());
        assertEquals(rawJson, result.json());
    }
}
