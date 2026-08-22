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
    void preservesUnsupportedExternalScoreboardModeAcrossRoundTrip() {
        ScoreboardConfig config = GSON.fromJson(
                "{\"enabled\":true,\"external_custom_scoreboard_mode\":\"FORCE\"}",
                ScoreboardConfig.class
        );

        JsonObject persisted = JsonParser.parseString(GSON.toJson(config)).getAsJsonObject();
        assertTrue(config.enabled);
        ScoreboardConfig reloaded = GSON.fromJson(persisted, ScoreboardConfig.class);
        assertEquals(ScoreboardConfig.ExternalCustomScoreboardMode.FORCE, config.external_custom_scoreboard_mode);
        assertEquals("FORCE", persisted.get("external_custom_scoreboard_mode").getAsString());
        assertEquals(ScoreboardConfig.ExternalCustomScoreboardMode.FORCE, reloaded.external_custom_scoreboard_mode);
    }
}
