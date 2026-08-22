package com.alexeys.translate_allinone.registration;

import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerLegacyAdvancementMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyAdvancementSettingsToOtherTranslations() throws Exception {
        Path configPath = temporaryDirectory.resolve("translate_allinone.json");
        Files.writeString(configPath, """
                {
                  "itemTranslate": {
                    "enabled_translate_vanilla_advancements": true,
                    "target_language": "Japanese",
                    "keybinding": {
                      "mode": "HOLD_TO_TRANSLATE",
                      "binding": {"type": "KEYSYM", "code": 65},
                      "refreshBinding": {"type": "KEYSYM", "code": 82}
                    }
                  }
                }
                """);

        ModConfig config = ConfigManager.loadConfig(configPath);
        String persisted = Files.readString(configPath);

        assertTrue(config.otherTranslations.enabled);
        assertTrue(config.otherTranslations.enabled_translate_vanilla_advancements);
        assertEquals("Japanese", config.otherTranslations.target_language);
        assertEquals(OtherTranslationsConfig.KeybindingMode.HOLD_TO_TRANSLATE, config.otherTranslations.keybinding.mode);
        assertFalse(JsonParser.parseString(persisted).getAsJsonObject().getAsJsonObject("itemTranslate").has("enabled_translate_vanilla_advancements"));
    }
}
