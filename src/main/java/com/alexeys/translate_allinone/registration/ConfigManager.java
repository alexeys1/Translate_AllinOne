package com.alexeys.translate_allinone.registration;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.TranslationFeatureGate;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DebugConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DictionaryConfig;
import com.alexeys.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.config.pojos.WynnCraftConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ModConfig config;
    private static boolean registered;
    private static String providerConfigurationFingerprint = "";
    private static boolean providerConfigurationFingerprintInitialized;
    private static boolean globalTranslationEnabled = true;
    private static boolean globalTranslationEnabledInitialized;

    public static synchronized void register() {
        if (registered) {
            return;
        }

        config = loadConfig(resolveConfigPath());
        ComponentTranslationDebugLogger.refresh(config);
        updateGlobalTranslationEnabled(false);
        updateProviderConfigurationFingerprint(false);
        registered = true;
    }

    public static synchronized ModConfig getConfig() {
        ensureRegistered();
        return config;
    }

    public static synchronized void save() {
        ensureRegistered();
        ComponentTranslationDebugLogger.refresh(config);
        updateGlobalTranslationEnabled(true);
        updateProviderConfigurationFingerprint(true);
        writeConfig(resolveConfigPath(), config);
    }

    public static synchronized ModConfig copyCurrentConfig() {
        ensureRegistered();
        return normalizeConfig(deepCopy(config));
    }

    public static synchronized void replaceConfig(ModConfig replacement) {
        ensureRegistered();
        config = normalizeConfig(deepCopy(replacement));
        ComponentTranslationDebugLogger.refresh(config);
        updateGlobalTranslationEnabled(true);
        updateProviderConfigurationFingerprint(true);
    }

    public static synchronized void resetToDefaults() {
        ensureRegistered();
        config = normalizeConfig(new ModConfig());
        ComponentTranslationDebugLogger.refresh(config);
        updateGlobalTranslationEnabled(true);
        updateProviderConfigurationFingerprint(true);
    }

    public static synchronized void setGlobalTranslationEnabled(boolean enabled) {
        ensureRegistered();
        config.providerManager.ensureDefaults();
        config.providerManager.translation_enabled = enabled;
        updateGlobalTranslationEnabled(true);
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
            boolean shouldRewriteConfig = parsedConfig == null || hasLegacyConfigAliases(rawConfig);
            boolean missingOtherTranslationsMasterSwitch = shouldRewriteOtherTranslationsMasterSwitch(rawConfig);
            ModConfig loadedConfig = normalizeConfig(parsedConfig);
            boolean migratedLegacyItemDebugConfig = migrateLegacyItemDebugConfig(rawConfig, loadedConfig);
            boolean migratedLegacyItemWynnCompatibilityConfig = ConfigMigrationSupport.hasDeprecatedWynnItemCompatibilityConfig(rawConfig);
            boolean migratedLegacyWynnTargetLanguageConfig = migrateLegacyWynnTargetLanguageConfig(rawConfig, loadedConfig);
            boolean migratedLegacyVanillaAdvancementConfig = migrateLegacyVanillaAdvancementConfig(rawConfig, loadedConfig);
            boolean migratedLegacyComponentRoutingConfig = migrateLegacyComponentRoutingConfig(rawConfig, loadedConfig);
            boolean removedOtherTranslationsRequestsPerMinute = removeOtherTranslationsRequestsPerMinute(rawConfig);
            boolean removedStructuredOutputConfig = removeStructuredOutputConfig(rawConfig);
            loadedConfig = normalizeConfig(loadedConfig);

            if (shouldRewriteConfig) {
                Translate_AllinOne.LOGGER.warn("Config file is empty or invalid, using defaults: {}", configPath);
            }

            if (shouldRewriteConfig
                    || migratedLegacyItemDebugConfig
                    || migratedLegacyItemWynnCompatibilityConfig
                    || migratedLegacyWynnTargetLanguageConfig
                    || migratedLegacyVanillaAdvancementConfig
                    || migratedLegacyComponentRoutingConfig
                    || removedOtherTranslationsRequestsPerMinute
                    || removedStructuredOutputConfig
                    || missingOtherTranslationsMasterSwitch) {
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

    private static void updateProviderConfigurationFingerprint(boolean notifyRuntime) {
        String currentFingerprint = providerConfigurationFingerprint(config);
        boolean changed = providerConfigurationFingerprintInitialized
                && !providerConfigurationFingerprint.equals(currentFingerprint);
        providerConfigurationFingerprint = currentFingerprint;
        providerConfigurationFingerprintInitialized = true;
        if (notifyRuntime && changed) {
            ComponentTranslationRuntime.providerConfigurationChanged();
        }
    }

    private static void updateGlobalTranslationEnabled(boolean notifyRuntime) {
        boolean currentEnabled = config != null
                && config.providerManager != null
                && config.providerManager.isTranslationEnabled();
        boolean changed = globalTranslationEnabledInitialized && globalTranslationEnabled != currentEnabled;
        globalTranslationEnabled = currentEnabled;
        globalTranslationEnabledInitialized = true;
        boolean gateChanged = TranslationFeatureGate.update(currentEnabled);
        if (notifyRuntime && (changed || gateChanged)) {
            LifecycleEventManager.globalTranslationFeatureChanged(currentEnabled);
        }
    }

    static String providerConfigurationFingerprint(ModConfig source) {
        JsonElement providerConfiguration = GSON.toJsonTree(source == null ? null : source.providerManager);
        if (providerConfiguration.isJsonObject()) {
            providerConfiguration.getAsJsonObject().remove("api_key_visible");
            providerConfiguration.getAsJsonObject().remove("translation_enabled");
        }
        String serialized = GSON.toJson(providerConfiguration);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(serialized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
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
        if (configToUse.otherTranslations == null) {
            configToUse.otherTranslations = new OtherTranslationsConfig();
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
        if (configToUse.wynnCraft.npc_dialogue.options_hud == null) {
            configToUse.wynnCraft.npc_dialogue.options_hud = WynnCraftConfig.HudConfig.optionsDefaults();
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
        if (configToUse.chatTranslate.output.target_language == null
                || configToUse.chatTranslate.output.target_language.isBlank()) {
            configToUse.chatTranslate.output.target_language = "Chinese";
        } else {
            configToUse.chatTranslate.output.target_language = configToUse.chatTranslate.output.target_language.trim();
        }
        configToUse.chatTranslate.output.max_concurrent_requests = Math.max(
                1,
                configToUse.chatTranslate.output.max_concurrent_requests
        );
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
        if (configToUse.otherTranslations.target_language == null || configToUse.otherTranslations.target_language.isBlank()) {
            configToUse.otherTranslations.target_language = OtherTranslationsConfig.DEFAULT_TARGET_LANGUAGE;
        } else {
            configToUse.otherTranslations.target_language = configToUse.otherTranslations.target_language.trim();
        }
        if (configToUse.otherTranslations.keybinding == null) {
            configToUse.otherTranslations.keybinding = new OtherTranslationsConfig.KeybindingConfig();
        }
        if (configToUse.otherTranslations.keybinding.binding == null) {
            configToUse.otherTranslations.keybinding.binding = new InputBindingConfig();
        }
        if (configToUse.otherTranslations.keybinding.refreshBinding == null) {
            configToUse.otherTranslations.keybinding.refreshBinding = new InputBindingConfig();
        }
        if (configToUse.otherTranslations.debug == null) {
            configToUse.otherTranslations.debug = new OtherTranslationsConfig.DebugConfig();
        }
        configToUse.otherTranslations.max_concurrent_requests = Math.max(1, configToUse.otherTranslations.max_concurrent_requests);
        configToUse.otherTranslations.max_batch_size = Math.max(1, configToUse.otherTranslations.max_batch_size);
        configToUse.otherTranslations.sign_translation_radius = clamp(
                configToUse.otherTranslations.sign_translation_radius,
                1,
                16
        );
        configToUse.otherTranslations.entity_translation_radius = clamp(
                configToUse.otherTranslations.entity_translation_radius,
                1,
                16
        );
        configToUse.otherTranslations.book_max_page_characters = clamp(
                configToUse.otherTranslations.book_max_page_characters,
                256,
                16_384
        );
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
        if (configToUse.scoreboardTranslate.keybinding.refreshBinding == null) {
            configToUse.scoreboardTranslate.keybinding.refreshBinding = new InputBindingConfig();
        }
        if (configToUse.scoreboardTranslate.external_custom_scoreboard_mode == null) {
            configToUse.scoreboardTranslate.external_custom_scoreboard_mode =
                    ScoreboardConfig.ExternalCustomScoreboardMode.DISABLED;
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
        configToUse.wynnCraft.npc_dialogue.options_hud.scale_percent = clamp(
                configToUse.wynnCraft.npc_dialogue.options_hud.scale_percent,
                WynnCraftConfig.HudConfig.MIN_SCALE_PERCENT,
                WynnCraftConfig.HudConfig.MAX_SCALE_PERCENT
        );
        configToUse.wynnCraft.npc_dialogue.options_hud.x_offset = clamp(
                configToUse.wynnCraft.npc_dialogue.options_hud.x_offset,
                WynnCraftConfig.HudConfig.MIN_X_OFFSET,
                WynnCraftConfig.HudConfig.MAX_X_OFFSET
        );
        configToUse.wynnCraft.npc_dialogue.options_hud.y_offset = clamp(
                configToUse.wynnCraft.npc_dialogue.options_hud.y_offset,
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
        } catch (IOException atomicMoveException) {
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveException) {
                moveException.addSuppressed(atomicMoveException);
                throw new RuntimeException("Failed to replace config file: " + configPath, moveException);
            }
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

    private static boolean migrateLegacyComponentRoutingConfig(JsonElement rawConfig, ModConfig loadedConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject() || loadedConfig == null) {
            return false;
        }
        JsonObject root = rawConfig.getAsJsonObject();
        JsonObject item = getItemTranslateObject(rawConfig);
        JsonObject scoreboard = getNestedObject(root, "scoreboardTranslate");
        JsonObject other = getOtherTranslationsObject(rawConfig);
        boolean migrated = hasAnyField(item,
                "component_json_v1_tooltip_lines",
                "component_json_v1_tooltip_structured",
                "component_json_v1_tooltip_paragraph",
                "component_json_v1_tooltip_custom_fonts")
                || hasAnyField(scoreboard, "component_json_v1_scoreboard")
                || hasAnyField(other,
                "component_json_v1_advancements",
                "component_json_v1_signs",
                "component_json_v1_entity_text",
                "component_json_v1_written_books");
        if (loadedConfig.itemTranslate != null && loadedConfig.itemTranslate.debug != null) {
            JsonObject debug = getNestedObject(item, "debug");
            migrated |= migrateBoolean(debug, "log_component_v1_flow", loadedConfig.itemTranslate.debug, "log_component_flow");
            migrated |= migrateBoolean(debug, "log_component_v1_text_content", loadedConfig.itemTranslate.debug, "log_component_text_content");
            migrated |= migrateBoolean(debug, "log_component_v1_timing", loadedConfig.itemTranslate.debug, "log_component_timing");
        }
        if (loadedConfig.otherTranslations != null && loadedConfig.otherTranslations.debug != null) {
            JsonObject debug = getNestedObject(other, "debug");
            migrated |= migrateBoolean(debug, "log_component_v1_entity_identity", loadedConfig.otherTranslations.debug, "log_component_entity_identity");
        }
        return migrated;
    }

    private static boolean removeOtherTranslationsRequestsPerMinute(JsonElement rawConfig) {
        return hasAnyField(getOtherTranslationsObject(rawConfig), "requests_per_minute");
    }

    private static boolean removeStructuredOutputConfig(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return false;
        }
        boolean removed = false;
        JsonObject providerManager = getNestedObject(rawConfig.getAsJsonObject(), "providerManager");
        JsonElement providers = providerManager == null ? null : providerManager.get("providers");
        if (providers != null && providers.isJsonArray()) {
            for (JsonElement providerElement : providers.getAsJsonArray()) {
                if (providerElement == null || !providerElement.isJsonObject()) {
                    continue;
                }
                JsonObject provider = providerElement.getAsJsonObject();
                removed |= provider.remove("enable_structured_output_if_available") != null;
                JsonElement modelSettings = provider.get("model_settings");
                if (modelSettings != null && modelSettings.isJsonArray()) {
                    for (JsonElement settingsElement : modelSettings.getAsJsonArray()) {
                        if (settingsElement != null && settingsElement.isJsonObject()) {
                            removed |= settingsElement.getAsJsonObject().remove("enable_structured_output_if_available") != null;
                        }
                    }
                }
            }
        }
        return removed;
    }

    private static boolean shouldRewriteOtherTranslationsMasterSwitch(JsonElement rawConfig) {
        JsonObject otherTranslations = getOtherTranslationsObject(rawConfig);
        return otherTranslations != null && !hasBooleanField(otherTranslations, "enabled");
    }

    private static boolean hasAnyField(JsonObject object, String... names) {
        if (object == null) {
            return false;
        }
        for (String name : names) {
            if (object.has(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLegacyConfigAliases(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return false;
        }
        JsonObject root = rawConfig.getAsJsonObject();
        return root.has("ItemTranslateConfig")
                || root.has("itemTranslateConfig")
                || root.has("ScoreboardConfig")
                || root.has("scoreboardConfig")
                || root.has("chatTranslateConfig")
                || root.has("ChatTranslateConfig");
    }

    private static boolean migrateBoolean(JsonObject source, String oldName, Object target, String newName) {
        if (source == null || !source.has(oldName) || !source.get(oldName).isJsonPrimitive()
                || !source.get(oldName).getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        if (source.has(newName)) {
            return true;
        }
        boolean value = source.get(oldName).getAsBoolean();
        if (target instanceof ItemTranslateConfig.DebugConfig itemDebug) {
            switch (newName) {
                case "log_component_flow" -> itemDebug.log_component_flow = value;
                case "log_component_text_content" -> itemDebug.log_component_text_content = value;
                case "log_component_timing" -> itemDebug.log_component_timing = value;
                default -> throw new IllegalArgumentException("Unknown item Component debug field: " + newName);
            }
        } else if (target instanceof OtherTranslationsConfig.DebugConfig otherDebug) {
            if (!"log_component_entity_identity".equals(newName)) {
                throw new IllegalArgumentException("Unknown other Component debug field: " + newName);
            }
            otherDebug.log_component_entity_identity = value;
        }
        return true;
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

    private static boolean migrateLegacyVanillaAdvancementConfig(JsonElement rawConfig, ModConfig loadedConfig) {
        if (loadedConfig == null) {
            return false;
        }

        JsonObject explicitOtherTranslationsConfig = getOtherTranslationsObject(rawConfig);
        if (explicitOtherTranslationsConfig != null) {
            if (!hasBooleanField(explicitOtherTranslationsConfig, "enabled")
                    && hasBooleanField(explicitOtherTranslationsConfig, "enabled_translate_vanilla_advancements")) {
                boolean legacyAdvancementEnabled = getBooleanField(
                        explicitOtherTranslationsConfig,
                        "enabled_translate_vanilla_advancements"
                );
                loadedConfig.otherTranslations.enabled = legacyAdvancementEnabled;
                loadedConfig.otherTranslations.enabled_translate_vanilla_advancements = legacyAdvancementEnabled;
                return true;
            }
            return false;
        }

        JsonObject legacyItemConfig = getItemTranslateObject(rawConfig);
        if (!hasBooleanField(legacyItemConfig, "enabled_translate_vanilla_advancements")) {
            return false;
        }

        if (loadedConfig.otherTranslations == null) {
            loadedConfig.otherTranslations = new OtherTranslationsConfig();
        }
        boolean legacyAdvancementEnabled = getBooleanField(
                legacyItemConfig,
                "enabled_translate_vanilla_advancements"
        );
        loadedConfig.otherTranslations.enabled = legacyAdvancementEnabled;
        loadedConfig.otherTranslations.enabled_translate_vanilla_advancements = legacyAdvancementEnabled;

        String legacyTargetLanguage = getOptionalString(legacyItemConfig, "target_language");
        if (legacyTargetLanguage != null) {
            loadedConfig.otherTranslations.target_language = legacyTargetLanguage;
        }
        copyLegacyItemKeybinding(loadedConfig.itemTranslate, loadedConfig.otherTranslations);

        if (loadedConfig.providerManager != null
                && loadedConfig.providerManager.routes != null
                && (loadedConfig.providerManager.routes.other_translations == null
                || loadedConfig.providerManager.routes.other_translations.isBlank())) {
            loadedConfig.providerManager.routes.other_translations = loadedConfig.providerManager.routes.item;
        }
        return true;
    }

    private static JsonObject getOtherTranslationsObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonElement otherTranslations = rawConfig.getAsJsonObject().get("otherTranslations");
        return otherTranslations != null && otherTranslations.isJsonObject()
                ? otherTranslations.getAsJsonObject()
                : null;
    }

    private static void copyLegacyItemKeybinding(
            ItemTranslateConfig legacyItemConfig,
            OtherTranslationsConfig targetConfig
    ) {
        if (legacyItemConfig == null || legacyItemConfig.keybinding == null || targetConfig == null) {
            return;
        }

        if (targetConfig.keybinding == null) {
            targetConfig.keybinding = new OtherTranslationsConfig.KeybindingConfig();
        }
        ItemTranslateConfig.KeybindingMode legacyMode = legacyItemConfig.keybinding.mode;
        targetConfig.keybinding.mode = legacyMode == null
                ? OtherTranslationsConfig.KeybindingMode.DISABLED
                : OtherTranslationsConfig.KeybindingMode.valueOf(legacyMode.name());
        targetConfig.keybinding.binding = copyBinding(legacyItemConfig.keybinding.binding);
        targetConfig.keybinding.refreshBinding = copyBinding(legacyItemConfig.keybinding.refreshBinding);
    }

    private static InputBindingConfig copyBinding(InputBindingConfig source) {
        InputBindingConfig copy = new InputBindingConfig();
        if (source == null) {
            return copy;
        }
        copy.type = source.type == null ? InputBindingConfig.InputType.KEYSYM : source.type;
        copy.code = source.code;
        return copy;
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

    private static boolean isLegacyItemDevModeEnabled(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject == null || !itemTranslateObject.has("dev_mode")) {
            return false;
        }
        JsonElement legacyDevMode = itemTranslateObject.get("dev_mode");
        return legacyDevMode != null && legacyDevMode.isJsonPrimitive() && legacyDevMode.getAsBoolean();
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

    private static JsonObject getItemTranslateObject(JsonElement rawConfig) {
        return ConfigMigrationSupport.getItemTranslateObject(rawConfig);
    }

    private static JsonObject getWynnCraftObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
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
