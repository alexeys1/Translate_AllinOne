package com.cedarxuesong.translate_allinone.registration;

import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigManagerProviderFingerprintTest {
    @Test
    void ignoresApiKeyVisibility() {
        ModConfig config = new ModConfig();
        config.providerManager.api_key_visible = true;
        String visibleFingerprint = ConfigManager.providerConfigurationFingerprint(config);

        config.providerManager.api_key_visible = false;

        assertEquals(visibleFingerprint, ConfigManager.providerConfigurationFingerprint(config));
    }

    @Test
    void changesWhenProviderRoutingChanges() {
        ModConfig config = new ModConfig();
        config.providerManager.routes.item = "provider::model-a";
        String originalFingerprint = ConfigManager.providerConfigurationFingerprint(config);

        config.providerManager.routes.item = "provider::model-b";

        assertNotEquals(originalFingerprint, ConfigManager.providerConfigurationFingerprint(config));
    }
}
