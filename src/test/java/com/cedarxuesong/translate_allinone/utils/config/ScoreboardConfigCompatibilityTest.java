package com.cedarxuesong.translate_allinone.utils.config;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardConfigCompatibilityTest {
    private static final Gson GSON = new Gson();

    @Test
    void ignoresLegacyExternalScoreboardModeAndDoesNotPersistIt() {
        ScoreboardConfig config = GSON.fromJson(
                "{\"enabled\":true,\"external_custom_scoreboard_mode\":\"FORCE\"}",
                ScoreboardConfig.class
        );

        JsonObject persisted = JsonParser.parseString(GSON.toJson(config)).getAsJsonObject();
        assertTrue(config.enabled);
        assertFalse(persisted.has("external_custom_scoreboard_mode"));
    }
}
