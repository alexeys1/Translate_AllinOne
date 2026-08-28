package com.alexeys.translate_allinone.utils.config;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCipherTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path tempDir;

    @Test
    void encryptsAndDecryptsForSameUuidAndProvider() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }

        String ciphertext = ApiKeyCipher.encrypt("sk-test-secret", uuid, "provider-a", key);

        assertTrue(ApiKeyCipher.isCiphertext(ciphertext));
        assertEquals(Optional.of("sk-test-secret"), ApiKeyCipher.decrypt(ciphertext, uuid, "provider-a", key));
    }

    @Test
    void rejectsDifferentUuid() {
        UUID originalUuid = UUID.randomUUID();
        UUID otherUuid = UUID.randomUUID();
        byte[] key = new byte[32];

        String ciphertext = ApiKeyCipher.encrypt("sk-test-secret", originalUuid, "provider-a", key);

        assertFalse(ApiKeyCipher.decrypt(ciphertext, otherUuid, "provider-a", key).isPresent());
    }

    @Test
    void rejectsDifferentProviderId() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];

        String ciphertext = ApiKeyCipher.encrypt("sk-test-secret", uuid, "provider-a", key);

        assertFalse(ApiKeyCipher.decrypt(ciphertext, uuid, "provider-b", key).isPresent());
    }

    @Test
    void createsAndReusesKeyFile() throws Exception {
        Path keyPath = tempDir.resolve(".taio").resolve("key_aes.txt");

        byte[] first = ApiKeyCipher.loadOrCreateKey(keyPath);
        byte[] second = ApiKeyCipher.loadOrCreateKey(keyPath);

        assertEquals(32, first.length);
        assertEquals(32, second.length);
        assertTrue(Files.exists(keyPath));
        assertNotEquals(0, first.length);
        assertEquals(first.length, second.length);

        String fileText = Files.readString(keyPath).trim();
        assertEquals(44, fileText.length());
        assertEquals(32, Base64.getDecoder().decode(fileText).length);
    }

    @Test
    void recreatesCorruptKeyFile() throws Exception {
        Path keyPath = tempDir.resolve("key_aes.txt");
        Files.createDirectories(keyPath.getParent());
        Files.writeString(keyPath, "not-a-valid-key");

        byte[] key = ApiKeyCipher.loadOrCreateKey(keyPath);

        assertEquals(32, key.length);
        String fileText = Files.readString(keyPath).trim();
        assertEquals(44, fileText.length());
        assertEquals(32, Base64.getDecoder().decode(fileText).length);
    }

    @Test
    void loadsBase64KeyFile() throws Exception {
        Path keyPath = tempDir.resolve("key_aes.txt");
        Files.createDirectories(keyPath.getParent());
        byte[] rawKey = new byte[32];
        for (int i = 0; i < rawKey.length; i++) {
            rawKey[i] = (byte) (i + 1);
        }
        Files.writeString(keyPath, Base64.getEncoder().encodeToString(rawKey));

        byte[] loaded = ApiKeyCipher.loadOrCreateKey(keyPath);

        assertEquals(32, loaded.length);
        for (int i = 0; i < rawKey.length; i++) {
            assertEquals(rawKey[i], loaded[i]);
        }
    }

    @Test
    void serializedConfigDoesNotContainPlaintextApiKey() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];

        ApiProviderProfile profile = new ApiProviderProfile();
        profile.id = "provider-a";
        profile.api_key = "sk-plaintext-secret";
        String ciphertext = ApiKeyCipher.encrypt(profile.api_key, uuid, profile.id, key);
        profile.api_key = ciphertext;

        String json = GSON.toJson(profile);

        assertFalse(json.contains("sk-plaintext-secret"));
        assertTrue(json.contains(ApiKeyCipher.PREFIX));
    }

    @Test
    void transientApiKeyStateIsNotSerialized() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.api_key = "enc:v1:abc";
        profile.api_key_cipher = "enc:v1:keep-me-in-memory-only";
        profile.api_key_decrypted = true;

        String json = GSON.toJson(profile);

        assertFalse(json.contains("api_key_cipher"));
        assertFalse(json.contains("api_key_decrypted"));
        assertFalse(json.contains("keep-me-in-memory-only"));
    }
}