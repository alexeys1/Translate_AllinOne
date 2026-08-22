package com.alexeys.translate_allinone.utils.config;

import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardConfigCompatibilityTest {
    private static final Gson GSON = new Gson();

    @Test
    void preservesExternalScoreboardModeAcrossVersions() {
        ScoreboardConfig config = GSON.fromJson(
                "{\"enabled\":true,\"external_custom_scoreboard_mode\":\"FORCE\"}",
                ScoreboardConfig.class
        );

        JsonObject persisted = JsonParser.parseString(GSON.toJson(config)).getAsJsonObject();
        assertTrue(config.enabled);
        assertEquals(ScoreboardConfig.ExternalCustomScoreboardMode.FORCE, config.external_custom_scoreboard_mode);
        assertEquals("FORCE", persisted.get("external_custom_scoreboard_mode").getAsString());
    }
}
