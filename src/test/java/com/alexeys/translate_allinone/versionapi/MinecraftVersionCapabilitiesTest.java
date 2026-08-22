package com.alexeys.translate_allinone.versionapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecraftVersionCapabilitiesTest {
    @Test
    void doesNotSupportExternalScoreboardTranslation() {
        assertFalse(MinecraftVersionCapabilities.current().externalScoreboardTranslation());
    }
}
