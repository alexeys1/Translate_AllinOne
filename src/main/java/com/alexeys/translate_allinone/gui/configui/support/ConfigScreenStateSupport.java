package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.registration.ConfigManager;
import com.alexeys.translate_allinone.utils.config.ui.ConfigSection;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigScreenStateSupport {
    private static final Gson STATE_GSON = new Gson();
    private static final String SCREEN_STATE_FILE_NAME = "config_screen_state.json";
    private static final ConfigSection DEFAULT_SECTION = ConfigSection.PROVIDERS;

    private ConfigScreenStateSupport() {
    }

    public static ConfigSection loadPersistedSelectedSection() {
        try {
            Path statePath = resolveScreenStatePath();
            if (!Files.exists(statePath)) {
                return DEFAULT_SECTION;
            }

            PersistedScreenState state = STATE_GSON.fromJson(Files.readString(statePath), PersistedScreenState.class);
            if (state == null) {
                return DEFAULT_SECTION;
            }
            return resolveSectionByKey(state.selectedSectionKey());
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to load config screen state", e);
            return DEFAULT_SECTION;
        }
    }

    public static boolean loadSetupCompleted() {
        try {
            Path statePath = resolveScreenStatePath();
            if (!Files.exists(statePath)) {
                return false;
            }

            PersistedScreenState state = STATE_GSON.fromJson(Files.readString(statePath), PersistedScreenState.class);
            return state != null && Boolean.TRUE.equals(state.setupCompleted());
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to load config screen state", e);
            return false;
        }
    }

    public static void persistScreenState(ConfigSection selectedSection, boolean setupCompleted) {
        try {
            Path statePath = resolveScreenStatePath();
            Path parent = statePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ConfigSection section = selectedSection == null ? DEFAULT_SECTION : selectedSection;
            Files.writeString(statePath, STATE_GSON.toJson(new PersistedScreenState(section.key(), setupCompleted)));
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to persist config screen state", e);
        }
    }

    private static Path resolveScreenStatePath() {
        return ConfigManager.getConfigPath().resolveSibling(SCREEN_STATE_FILE_NAME);
    }

    private static ConfigSection resolveSectionByKey(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_SECTION;
        }

        for (ConfigSection section : ConfigSection.values()) {
            if (section.key().equalsIgnoreCase(key)) {
                return section;
            }
        }
        return DEFAULT_SECTION;
    }

    private record PersistedScreenState(String selectedSectionKey, Boolean setupCompleted) {
    }
}
