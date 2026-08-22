package com.alexeys.translate_allinone.registration;

import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigManagerStructuredOutputMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsLegacyConfigAndStripsStructuredOutputKeys() throws Exception {
        Path configPath = tempDir.resolve("translate_allinone.json");
        Files.writeString(configPath, legacyConfigJson(), StandardCharsets.UTF_8);

        ModConfig loaded = ConfigManager.loadConfig(configPath);

        assertNotNull(loaded);
        assertNotNull(loaded.providerManager);
        assertEquals(1, loaded.providerManager.providers.size());
        ApiProviderProfile provider = loaded.providerManager.providers.get(0);
        assertEquals("stale-model", provider.model_id);
        assertNotNull(provider.getModelSettings("stale-model"));
        assertEquals("stale::stale-model", loaded.providerManager.routes.chat_output);

        String rewritten = Files.readString(configPath, StandardCharsets.UTF_8);
        assertFalse(
                rewritten.contains("enable_structured_output_if_available"),
                "rewritten config must not retain the obsolete structured output key"
        );
    }

    @Test
    void preservesExternalScoreboardModeWhenRewritingConfig() throws Exception {
        Path configPath = tempDir.resolve("external-scoreboard.json");
        Files.writeString(configPath, externalScoreboardConfigJson(), StandardCharsets.UTF_8);

        ModConfig firstLoad = ConfigManager.loadConfig(configPath);
        ModConfig secondLoad = ConfigManager.loadConfig(configPath);

        assertEquals(
                ScoreboardConfig.ExternalCustomScoreboardMode.FORCE,
                firstLoad.scoreboardTranslate.external_custom_scoreboard_mode
        );
        assertEquals(
                ScoreboardConfig.ExternalCustomScoreboardMode.FORCE,
                secondLoad.scoreboardTranslate.external_custom_scoreboard_mode
        );
    }

    private static String externalScoreboardConfigJson() {
        return """
                {
                  "scoreboardTranslate": {
                    "external_custom_scoreboard_mode": "FORCE"
                  },
                  "otherTranslations": {}
                }
                """;
    }

    private static String legacyConfigJson() {
        return """
                {
                  "providerManager": {
                    "providers": [
                      {
                        "id": "stale",
                        "name": "Stale",
                        "type": "OPENAI_COMPAT",
                        "base_url": "https://example.com/v1",
                        "api_key": "",
                        "model_id": "stale-model",
                        "model_ids": ["stale-model"],
                        "model_settings": [
                          {
                            "model_id": "stale-model",
                            "enable_structured_output_if_available": true
                          }
                        ],
                        "enable_structured_output_if_available": true
                      }
                    ],
                    "routes": {
                      "chat_output": "stale::stale-model"
                    }
                  }
                }
                """;
    }
}
