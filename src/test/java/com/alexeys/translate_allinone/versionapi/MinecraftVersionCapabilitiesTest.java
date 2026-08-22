package com.alexeys.translate_allinone.versionapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionCapabilitiesTest {
    @Test
    void supportsExternalScoreboardTranslation() {
        assertTrue(MinecraftVersionCapabilities.current().externalScoreboardTranslation());
    }
}
