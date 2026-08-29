package com.alexeys.translate_allinone.registration;

import com.alexeys.translate_allinone.utils.config.ApiKeyCipher;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyConfigSerializationTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void configJsonContainsCiphertextInsteadOfPlaintextApiKey() {
        UUID uuid = UUID.randomUUID();
        byte[] key = new byte[32];
        String plaintext = "sk-version-layer-secret";

        ModConfig config = new ModConfig();
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.id = "provider-a";
        profile.name = "Provider A";
        profile.api_key = plaintext;
        String ciphertext = ApiKeyCipher.encrypt(plaintext, uuid, profile.id, key);
        profile.api_key = ciphertext;
        config.providerManager.providers.add(profile);

        String json = GSON.toJson(config);

        assertFalse(json.contains(plaintext));
        assertTrue(json.contains(ApiKeyCipher.PREFIX));

        ModConfig parsed = GSON.fromJson(json, ModConfig.class);
        ApiProviderProfile loadedProfile = parsed.providerManager.providers.get(0);
        assertEquals(ciphertext, loadedProfile.api_key);
        assertEquals(
                plaintext,
                ApiKeyCipher.decrypt(loadedProfile.api_key, uuid, loadedProfile.id, key).orElseThrow()
        );
    }

    @Test
    void prepareForSerializationAllowsPlaintextWhenNoUuidAndNoKeyFile() throws Exception {
        ModConfig config = new ModConfig();
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.id = "offline-provider";
        profile.api_key = "sk-offline-plaintext";
        config.providerManager.providers.add(profile);

        Method method = ConfigManager.class.getDeclaredMethod("prepareForSerialization", ModConfig.class);
        method.setAccessible(true);
        ModConfig prepared = (ModConfig) method.invoke(null, config);

        ApiProviderProfile preparedProfile = prepared.providerManager.providers.get(0);
        assertEquals("sk-offline-plaintext", preparedProfile.api_key);
    }
}