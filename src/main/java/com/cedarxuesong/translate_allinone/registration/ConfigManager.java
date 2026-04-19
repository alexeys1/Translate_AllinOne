package com.cedarxuesong.translate_allinone.registration;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DebugConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Translate_AllinOne.MOD_ID)
            .resolve(Translate_AllinOne.MOD_ID + ".json");

    private static ModConfig config;
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }

        config = loadConfig();
        registered = true;
    }

    public static synchronized ModConfig getConfig() {
        ensureRegistered();
        return config;
    }

    public static synchronized void save() {
        ensureRegistered();
        writeConfig(config);
    }

    public static synchronized ModConfig copyCurrentConfig() {
        ensureRegistered();
        return normalizeConfig(deepCopy(config));
    }

    public static synchronized void replaceConfig(ModConfig replacement) {
        ensureRegistered();
        config = normalizeConfig(deepCopy(replacement));
    }

    public static synchronized void resetToDefaults() {
        ensureRegistered();
        config = normalizeConfig(new ModConfig());
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    private static ModConfig loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaultConfig = normalizeConfig(new ModConfig());
            writeConfig(defaultConfig);
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonElement rawConfig = JsonParser.parseReader(reader);
            ModConfig loadedConfig = normalizeConfig(GSON.fromJson(rawConfig, ModConfig.class));
            boolean migratedLegacyItemDebugConfig = migrateLegacyItemDebugConfig(rawConfig, loadedConfig);
            if (loadedConfig == null) {
                Translate_AllinOne.LOGGER.warn("Config file is empty or invalid, using defaults: {}", CONFIG_PATH);
                loadedConfig = normalizeConfig(new ModConfig());
                writeConfig(loadedConfig);
            } else if (migratedLegacyItemDebugConfig) {
                writeConfig(loadedConfig);
            }
            return loadedConfig;
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Failed to load config file, using defaults: {}", CONFIG_PATH, e);
            ModConfig fallback = normalizeConfig(new ModConfig());
            writeConfig(fallback);
            return fallback;
        }
    }

    private static void ensureRegistered() {
        if (!registered) {
            throw new IllegalStateException("Config not registered yet!");
        }
    }

    private static ModConfig deepCopy(ModConfig source) {
        if (source == null) {
            return new ModConfig();
        }
        ModConfig copied = GSON.fromJson(GSON.toJson(source), ModConfig.class);
        return copied == null ? new ModConfig() : copied;
    }

    private static ModConfig normalizeConfig(ModConfig loadedConfig) {
        ModConfig configToUse = loadedConfig;
        if (configToUse == null) {
            configToUse = new ModConfig();
        }

        if (configToUse.chatTranslate == null) {
            configToUse.chatTranslate = new ChatTranslateConfig();
        }
        if (configToUse.itemTranslate == null) {
            configToUse.itemTranslate = new ItemTranslateConfig();
        }
        if (configToUse.scoreboardTranslate == null) {
            configToUse.scoreboardTranslate = new ScoreboardConfig();
        }
        if (configToUse.cacheBackup == null) {
            configToUse.cacheBackup = new CacheBackupConfig();
        }
        if (configToUse.debug == null) {
            configToUse.debug = new DebugConfig();
        }
        if (configToUse.providerManager == null) {
            configToUse.providerManager = new ProviderManagerConfig();
        }

        if (configToUse.chatTranslate.input == null) {
            configToUse.chatTranslate.input = new ChatTranslateConfig.ChatInputTranslateConfig();
        }
        if (configToUse.chatTranslate.output == null) {
            configToUse.chatTranslate.output = new ChatTranslateConfig.ChatOutputTranslateConfig();
        }
        if (configToUse.chatTranslate.output.debug == null) {
            configToUse.chatTranslate.output.debug = new ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig();
        }
        configToUse.chatTranslate.output.interaction_offset_amount = clamp(configToUse.chatTranslate.output.interaction_offset_amount, 0, 5);
        if (configToUse.chatTranslate.input.keybinding == null) {
            configToUse.chatTranslate.input.keybinding = new InputBindingConfig();
        }
        if (configToUse.chatTranslate.input.assistant_panel_enabled == null) {
            configToUse.chatTranslate.input.assistant_panel_enabled = true;
        }
        if (configToUse.chatTranslate.input.panel == null) {
            configToUse.chatTranslate.input.panel = new ChatTranslateConfig.ChatInputPanelState();
        }

        if (configToUse.itemTranslate.keybinding == null) {
            configToUse.itemTranslate.keybinding = new ItemTranslateConfig.KeybindingConfig();
        }
        if (configToUse.itemTranslate.keybinding.binding == null) {
            configToUse.itemTranslate.keybinding.binding = new InputBindingConfig();
        }
        if (configToUse.itemTranslate.keybinding.refreshBinding == null) {
            configToUse.itemTranslate.keybinding.refreshBinding = new InputBindingConfig();
        }
        if (configToUse.itemTranslate.debug == null) {
            configToUse.itemTranslate.debug = new ItemTranslateConfig.DebugConfig();
        }

        if (configToUse.scoreboardTranslate.keybinding == null) {
            configToUse.scoreboardTranslate.keybinding = new ScoreboardConfig.KeybindingConfig();
        }
        if (configToUse.scoreboardTranslate.keybinding.binding == null) {
            configToUse.scoreboardTranslate.keybinding.binding = new InputBindingConfig();
        }

        configToUse.cacheBackup.backup_interval_minutes = clamp(
                configToUse.cacheBackup.backup_interval_minutes,
                CacheBackupConfig.MIN_BACKUP_INTERVAL_MINUTES,
                CacheBackupConfig.MAX_BACKUP_INTERVAL_MINUTES
        );
        configToUse.cacheBackup.max_backup_count = clamp(
                configToUse.cacheBackup.max_backup_count,
                CacheBackupConfig.MIN_MAX_BACKUP_COUNT,
                CacheBackupConfig.MAX_MAX_BACKUP_COUNT
        );

        configToUse.providerManager.ensureDefaults();
        return configToUse;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void writeConfig(ModConfig targetConfig) {
        Path parent = CONFIG_PATH.getParent();
        if (parent == null) {
            throw new IllegalStateException("Config path has no parent: " + CONFIG_PATH);
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory: " + parent, e);
        }

        Path tempPath = parent.resolve(CONFIG_PATH.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath)) {
            GSON.toJson(targetConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write temp config file: " + tempPath, e);
        }

        try {
            Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveException) {
                throw new RuntimeException("Failed to replace config file: " + CONFIG_PATH, moveException);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to replace config file: " + CONFIG_PATH, e);
        }
    }

    private static boolean migrateLegacyItemDebugConfig(JsonElement rawConfig, ModConfig loadedConfig) {
        if (loadedConfig == null || loadedConfig.itemTranslate == null || loadedConfig.itemTranslate.debug == null) {
            return false;
        }

        boolean migratedLegacyItemDevMode = false;
        if (!hasExplicitItemDebugEnabled(rawConfig) && isLegacyItemDevModeEnabled(rawConfig)) {
            loadedConfig.itemTranslate.debug.enabled = true;
            migratedLegacyItemDevMode = true;
        }

        return migratedLegacyItemDevMode || shouldRewriteLegacyItemDebugObject(rawConfig);
    }

    private static boolean hasExplicitItemDebugEnabled(JsonElement rawConfig) {
        JsonObject debugObject = getItemDebugObject(rawConfig);
        if (debugObject != null && debugObject.has("enabled")) {
            return true;
        }

        JsonObject legacyDevObject = getLegacyItemDevObject(rawConfig);
        return legacyDevObject != null && legacyDevObject.has("enabled");
    }

    private static boolean shouldRewriteLegacyItemDebugObject(JsonElement rawConfig) {
        return getLegacyItemDevObject(rawConfig) != null && getItemDebugObject(rawConfig) == null;
    }

    private static boolean isLegacyItemDevModeEnabled(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject == null || !itemTranslateObject.has("dev_mode")) {
            return false;
        }
        JsonElement legacyDevMode = itemTranslateObject.get("dev_mode");
        return legacyDevMode != null && legacyDevMode.isJsonPrimitive() && legacyDevMode.getAsBoolean();
    }

    private static JsonObject getItemTranslateObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonElement itemTranslateElement = rawConfig.getAsJsonObject().get("itemTranslate");
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            return null;
        }
        return itemTranslateElement.getAsJsonObject();
    }

    private static JsonObject getItemDebugObject(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject == null) {
            return null;
        }

        JsonElement debugElement = itemTranslateObject.get("debug");
        if (debugElement == null || !debugElement.isJsonObject()) {
            return null;
        }
        return debugElement.getAsJsonObject();
    }

    private static JsonObject getLegacyItemDevObject(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject == null) {
            return null;
        }

        JsonElement devElement = itemTranslateObject.get("dev");
        if (devElement == null || !devElement.isJsonObject()) {
            return null;
        }
        return devElement.getAsJsonObject();
    }
}
