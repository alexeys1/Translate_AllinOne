package com.cedarxuesong.translate_allinone.registration;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DebugConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DictionaryConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;

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

    private static ModConfig config;
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }

        config = loadConfig(resolveConfigPath());
        registered = true;
    }

    public static synchronized ModConfig getConfig() {
        ensureRegistered();
        return config;
    }

    public static synchronized void save() {
        ensureRegistered();
        writeConfig(resolveConfigPath(), config);
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
        return resolveConfigPath();
    }

    static ModConfig loadConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            ModConfig defaultConfig = normalizeConfig(new ModConfig());
            writeConfigBestEffort(configPath, defaultConfig, "Failed to persist default config file: {}");
            return defaultConfig;
        }

        JsonElement rawConfig;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            rawConfig = JsonParser.parseReader(reader);
        } catch (Exception e) {
            return loadFallbackConfig(configPath, e);
        }

        try {
            ModConfig parsedConfig = GSON.fromJson(rawConfig, ModConfig.class);
            boolean shouldRewriteConfig = parsedConfig == null;
            ModConfig loadedConfig = normalizeConfig(parsedConfig);
            boolean migratedLegacyItemDebugConfig = migrateLegacyItemDebugConfig(rawConfig, loadedConfig);
            boolean migratedLegacyItemWynnCompatibilityConfig = ConfigMigrationSupport.hasDeprecatedWynnItemCompatibilityConfig(rawConfig);
            boolean migratedLegacyWynnTargetLanguageConfig = migrateLegacyWynnTargetLanguageConfig(rawConfig, loadedConfig);
            loadedConfig = normalizeConfig(loadedConfig);

            if (shouldRewriteConfig) {
                Translate_AllinOne.LOGGER.warn("Config file is empty or invalid, using defaults: {}", configPath);
            }

            if (shouldRewriteConfig
                    || migratedLegacyItemDebugConfig
                    || migratedLegacyItemWynnCompatibilityConfig
                    || migratedLegacyWynnTargetLanguageConfig) {
                writeConfigBestEffort(
                        configPath,
                        loadedConfig,
                        "Failed to rewrite migrated config file, continuing with loaded values: {}"
                );
            }
            return loadedConfig;
        } catch (Exception e) {
            return loadFallbackConfig(configPath, e);
        }
    }

    private static Path resolveConfigPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(Translate_AllinOne.MOD_ID + ".json");
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
        if (configToUse.wynnCraft == null) {
            configToUse.wynnCraft = new WynnCraftConfig();
        }
        if (configToUse.dictionary == null) {
            configToUse.dictionary = new DictionaryConfig();
        }
        configToUse.dictionary.normalize();
        if (configToUse.wynnCraft.target_language == null || configToUse.wynnCraft.target_language.isBlank()) {
            configToUse.wynnCraft.target_language = WynnCraftConfig.DEFAULT_TARGET_LANGUAGE;
        } else {
            configToUse.wynnCraft.target_language = configToUse.wynnCraft.target_language.trim();
        }
        if (configToUse.wynnCraft.npc_dialogue == null) {
            configToUse.wynnCraft.npc_dialogue = new WynnCraftConfig.NpcDialogueConfig();
        }
        if (configToUse.wynnCraft.npc_dialogue.hud == null) {
            configToUse.wynnCraft.npc_dialogue.hud = new WynnCraftConfig.HudConfig();
        }
        if (configToUse.wynnCraft.npc_dialogue.debug == null) {
            configToUse.wynnCraft.npc_dialogue.debug = new WynnCraftConfig.DebugConfig();
        }
        if (!configToUse.wynnCraft.npc_dialogue.debug.log_dialogues_local_hits
                && (configToUse.wynnCraft.npc_dialogue.log_dialogues_local_hits
                || configToUse.wynnCraft.npc_dialogue.debug.log_local_dictionary_hits)) {
            configToUse.wynnCraft.npc_dialogue.debug.log_dialogues_local_hits = true;
        }
        if (configToUse.wynnCraft.wynntils_task_tracker == null) {
            configToUse.wynnCraft.wynntils_task_tracker = new WynnCraftConfig.WynntilsTaskTrackerConfig();
        }
        if (configToUse.wynnCraft.wynntils_task_tracker.debug == null) {
            configToUse.wynnCraft.wynntils_task_tracker.debug = new WynnCraftConfig.DebugConfig();
        }
        if (!configToUse.wynnCraft.wynntils_task_tracker.debug.log_quests_local_hits
                && configToUse.wynnCraft.wynntils_task_tracker.debug.log_local_dictionary_hits) {
            configToUse.wynnCraft.wynntils_task_tracker.debug.log_quests_local_hits = true;
        }
        if (configToUse.wynnCraft.wynntils_task_tracker.keybinding == null) {
            configToUse.wynnCraft.wynntils_task_tracker.keybinding = new WynnCraftConfig.KeybindingConfig();
        }
        if (configToUse.wynnCraft.wynntils_task_tracker.keybinding.binding == null) {
            configToUse.wynnCraft.wynntils_task_tracker.keybinding.binding = new InputBindingConfig();
        }
        if (configToUse.wynnCraft.wynntils_task_tracker.keybinding.refreshBinding == null) {
            configToUse.wynnCraft.wynntils_task_tracker.keybinding.refreshBinding = new InputBindingConfig();
        }
        if (configToUse.cacheBackup == null) {
            configToUse.cacheBackup = new CacheBackupConfig();
        }
        if (configToUse.cacheBackup.enabled == null) {
            configToUse.cacheBackup.enabled = CacheBackupConfig.DEFAULT_ENABLED;
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
            configToUse.chatTranslate.input.assistant_panel_enabled = false;
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
        if (!configToUse.itemTranslate.debug.log_items_local_hits
                && !configToUse.itemTranslate.debug.log_skills_local_hits
                && configToUse.itemTranslate.log_skills_local_hits) {
            configToUse.itemTranslate.debug.log_items_local_hits = true;
            configToUse.itemTranslate.debug.log_skills_local_hits = true;
        }
        if (configToUse.scoreboardTranslate.keybinding == null) {
            configToUse.scoreboardTranslate.keybinding = new ScoreboardConfig.KeybindingConfig();
        }
        if (configToUse.scoreboardTranslate.keybinding.binding == null) {
            configToUse.scoreboardTranslate.keybinding.binding = new InputBindingConfig();
        }
        if (configToUse.scoreboardTranslate.debug == null) {
            configToUse.scoreboardTranslate.debug = new ScoreboardConfig.DebugConfig();
        }
        configToUse.wynnCraft.npc_dialogue.hud.scale_percent = clamp(
                configToUse.wynnCraft.npc_dialogue.hud.scale_percent,
                WynnCraftConfig.HudConfig.MIN_SCALE_PERCENT,
                WynnCraftConfig.HudConfig.MAX_SCALE_PERCENT
        );
        configToUse.wynnCraft.npc_dialogue.hud.x_offset = clamp(
                configToUse.wynnCraft.npc_dialogue.hud.x_offset,
                WynnCraftConfig.HudConfig.MIN_X_OFFSET,
                WynnCraftConfig.HudConfig.MAX_X_OFFSET
        );
        configToUse.wynnCraft.npc_dialogue.hud.y_offset = clamp(
                configToUse.wynnCraft.npc_dialogue.hud.y_offset,
                WynnCraftConfig.HudConfig.MIN_Y_OFFSET,
                WynnCraftConfig.HudConfig.MAX_Y_OFFSET
        );

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

    private static ModConfig loadFallbackConfig(Path configPath, Exception cause) {
        Translate_AllinOne.LOGGER.error("Failed to load config file, using defaults: {}", configPath, cause);
        ModConfig fallback = normalizeConfig(new ModConfig());
        writeConfigBestEffort(configPath, fallback, "Failed to persist fallback config file: {}");
        return fallback;
    }

    private static void writeConfigBestEffort(Path configPath, ModConfig targetConfig, String message) {
        try {
            writeConfig(configPath, targetConfig);
        } catch (RuntimeException e) {
            Translate_AllinOne.LOGGER.error(message, configPath, e);
        }
    }

    private static void writeConfig(Path configPath, ModConfig targetConfig) {
        Path parent = configPath.getParent();
        if (parent == null) {
            throw new IllegalStateException("Config path has no parent: " + configPath);
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory: " + parent, e);
        }

        Path tempPath = parent.resolve(configPath.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath)) {
            GSON.toJson(targetConfig, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write temp config file: " + tempPath, e);
        }

        try {
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveException) {
                throw new RuntimeException("Failed to replace config file: " + configPath, moveException);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to replace config file: " + configPath, e);
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

        boolean migratedLegacyLocalHitLogging = migrateLegacyItemLocalHitLogging(rawConfig, loadedConfig);

        return migratedLegacyItemDevMode
                || migratedLegacyLocalHitLogging
                || shouldRewriteLegacyItemDebugObject(rawConfig);
    }

    private static boolean migrateLegacyWynnTargetLanguageConfig(JsonElement rawConfig, ModConfig loadedConfig) {
        if (loadedConfig == null) {
            return false;
        }
        if (loadedConfig.wynnCraft == null) {
            loadedConfig.wynnCraft = new WynnCraftConfig();
        }

        JsonObject wynnCraftObject = getWynnCraftObject(rawConfig);
        if (wynnCraftObject == null) {
            return false;
        }

        String explicitSharedTargetLanguage = getOptionalString(wynnCraftObject, "target_language");
        String legacyDialogueTargetLanguage = getOptionalString(getNestedObject(wynnCraftObject, "npc_dialogue"), "target_language");
        String legacyTrackerTargetLanguage = getOptionalString(getNestedObject(wynnCraftObject, "wynntils_task_tracker"), "target_language");
        loadedConfig.wynnCraft.target_language = resolveSharedWynnTargetLanguage(
                explicitSharedTargetLanguage,
                legacyDialogueTargetLanguage,
                legacyTrackerTargetLanguage
        );
        return legacyDialogueTargetLanguage != null || legacyTrackerTargetLanguage != null;
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
    private static boolean migrateLegacyItemLocalHitLogging(JsonElement rawConfig, ModConfig loadedConfig) {
        if (!hasLegacyItemLocalHitLogging(rawConfig)) {
            return false;
        }

        if (isLegacyItemLocalHitLoggingEnabled(rawConfig)
                && !loadedConfig.itemTranslate.debug.log_items_local_hits
                && !loadedConfig.itemTranslate.debug.log_skills_local_hits) {
            loadedConfig.itemTranslate.debug.log_items_local_hits = true;
            loadedConfig.itemTranslate.debug.log_skills_local_hits = true;
        }
        return true;
    }

    private static boolean hasLegacyItemLocalHitLogging(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        return hasBooleanField(itemTranslateObject, "log_local_dictionary_hits")
                || hasBooleanField(getItemDebugObject(rawConfig), "log_local_dictionary_hits")
                || hasBooleanField(getLegacyItemDevObject(rawConfig), "log_local_dictionary_hits");
    }

    private static boolean isLegacyItemLocalHitLoggingEnabled(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        return getBooleanField(itemTranslateObject, "log_local_dictionary_hits")
                || getBooleanField(getItemDebugObject(rawConfig), "log_local_dictionary_hits")
                || getBooleanField(getLegacyItemDevObject(rawConfig), "log_local_dictionary_hits");
    }


    private static JsonObject getItemTranslateObject(JsonElement rawConfig) {
        return ConfigMigrationSupport.getItemTranslateObject(rawConfig);
    }

    private static JsonObject getWynnCraftObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
    private static boolean hasBooleanField(JsonObject object, String fieldName) {
        JsonElement element = object == null || fieldName == null ? null : object.get(fieldName);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private static boolean getBooleanField(JsonObject object, String fieldName) {
        JsonElement element = object == null || fieldName == null ? null : object.get(fieldName);
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

        JsonObject root = rawConfig.getAsJsonObject();
        JsonElement wynnCraft = root.get("wynnCraft");
        if (wynnCraft == null || !wynnCraft.isJsonObject()) {
            return null;
        }
        return wynnCraft.getAsJsonObject();
    }

    private static JsonObject getNestedObject(JsonObject parent, String memberName) {
        if (parent == null || memberName == null || memberName.isBlank()) {
            return null;
        }
        JsonElement nested = parent.get(memberName);
        if (nested == null || !nested.isJsonObject()) {
            return null;
        }
        return nested.getAsJsonObject();
    }

    private static String getOptionalString(JsonObject object, String memberName) {
        if (object == null || memberName == null || memberName.isBlank() || !object.has(memberName)) {
            return null;
        }
        JsonElement value = object.get(memberName);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static String resolveSharedWynnTargetLanguage(
            String currentSharedTargetLanguage,
            String legacyDialogueTargetLanguage,
            String legacyTrackerTargetLanguage
    ) {
        String shared = sanitizeOptionalTargetLanguage(currentSharedTargetLanguage);
        if (shared != null) {
            return shared;
        }

        String dialogue = sanitizeOptionalTargetLanguage(legacyDialogueTargetLanguage);
        String tracker = sanitizeOptionalTargetLanguage(legacyTrackerTargetLanguage);
        if (dialogue == null && tracker == null) {
            return WynnCraftConfig.DEFAULT_TARGET_LANGUAGE;
        }
        if (dialogue == null) {
            return tracker;
        }
        if (tracker == null) {
            return dialogue;
        }
        if (dialogue.equalsIgnoreCase(tracker)) {
            return dialogue;
        }

        boolean dialogueUsesDefault = dialogue.equalsIgnoreCase(WynnCraftConfig.DEFAULT_TARGET_LANGUAGE);
        boolean trackerUsesDefault = tracker.equalsIgnoreCase(WynnCraftConfig.DEFAULT_TARGET_LANGUAGE);
        if (dialogueUsesDefault && !trackerUsesDefault) {
            return tracker;
        }
        if (trackerUsesDefault && !dialogueUsesDefault) {
            return dialogue;
        }
        return dialogue;
    }

    private static String sanitizeOptionalTargetLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
