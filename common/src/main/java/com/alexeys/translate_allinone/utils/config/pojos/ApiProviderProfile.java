package com.alexeys.translate_allinone.utils.config.pojos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiProviderProfile {
    public static final double DEFAULT_CHAT_TEMPERATURE = 1.3;
    public static final double DEFAULT_ITEM_TEMPERATURE = 0.5;
    public static final double DEFAULT_SCOREBOARD_TEMPERATURE = 0.5;
    public static final double DEFAULT_OTHER_TRANSLATIONS_TEMPERATURE = 0.5;
    public static final double DEFAULT_WYNNTILS_TASK_TRACKER_TEMPERATURE = 0.5;
    public static final double DEFAULT_WYNN_NPC_DIALOGUE_TEMPERATURE = 1.3;

    public String id = "provider";
    public String name = "Provider";
    public boolean enabled = true;

    public ApiProviderType type = ApiProviderType.OPENAI_COMPAT;
    public String base_url = "https://api.openai.com/v1";
    public String api_key = "";
    public List<String> api_key_entries = new ArrayList<>();
    public transient boolean api_key_decrypt_failed = false;
    public String model_id = "";
    public List<String> model_ids = new ArrayList<>();
    public List<ModelSettings> model_settings = new ArrayList<>();

    public Double temperature = null;
    public Double chat_temperature = null;
    public Double item_temperature = null;
    public Double scoreboard_temperature = null;
    public Double other_translations_temperature = null;
    public Double wynntils_task_tracker_temperature = null;
    public Double wynn_npc_dialogue_temperature = null;
    public String keep_alive_time = "1m";
    public boolean supports_system_message = true;
    public boolean inject_system_prompt_into_user_message = true;
    public String system_prompt_suffix = "";
    public List<CustomParameterEntry> custom_parameters = new ArrayList<>();
    public Map<String, String> system_prompt_overrides = new LinkedHashMap<>();

    public static ApiProviderProfile createOpenAiDefault() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.id = "openai_default";
        profile.name = "OpenAI Default";
        profile.type = ApiProviderType.OPENAI_COMPAT;
        profile.base_url = "https://api.openai.com/v1";
        return profile;
    }

    public static ApiProviderProfile createOllamaDefault() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.id = "ollama_default";
        profile.name = "Ollama Default";
        profile.type = ApiProviderType.OLLAMA;
        profile.base_url = "http://localhost:11434";
        return profile;
    }

    public boolean hasApiKeyDecryptFailure() {
        return api_key_decrypt_failed;
    }

    public void normalizePromptOverrides() {
        if (system_prompt_overrides == null) {
            system_prompt_overrides = new LinkedHashMap<>();
        }
        system_prompt_overrides.entrySet().removeIf(e -> e == null || e.getKey() == null || e.getKey().isBlank());
    }

    public List<ModelSettings> ensureModelSettings() {
        normalizePromptOverrides();
        Map<String, ModelSettings> normalized = new LinkedHashMap<>();

        if (model_settings != null) {
            for (ModelSettings settings : model_settings) {
                if (settings == null) {
                    continue;
                }
                String key = normalizeModelId(settings.model_id);
                if (key.isEmpty()) {
                    continue;
                }
                normalized.putIfAbsent(key, normalizeModelSettings(settings, key));
            }
        }

        if (model_ids != null) {
            for (String model : model_ids) {
                String key = normalizeModelId(model);
                if (key.isEmpty()) {
                    continue;
                }
                normalized.putIfAbsent(key, createDefaultModelSettings(key));
            }
        }

        String activeModelId = normalizeModelId(model_id);
        if (!activeModelId.isEmpty()) {
            normalized.putIfAbsent(activeModelId, createDefaultModelSettings(activeModelId));
        }

        if (normalized.isEmpty()) {
            model_settings = new ArrayList<>();
            model_ids = new ArrayList<>();
            model_id = "";
            return model_settings;
        }

        if (activeModelId.isEmpty() || !normalized.containsKey(activeModelId)) {
            activeModelId = normalized.keySet().iterator().next();
        }

        model_settings = new ArrayList<>(normalized.values());
        model_ids = new ArrayList<>(normalized.keySet());
        model_id = activeModelId;

        ModelSettings active = normalized.get(activeModelId);
        syncLegacyFieldsFrom(active);
        return model_settings;
    }

    public ModelSettings getModelSettings(String id) {
        String normalizedId = normalizeModelId(id);
        if (normalizedId.isEmpty()) {
            return null;
        }

        for (ModelSettings settings : ensureModelSettings()) {
            if (normalizedId.equals(settings.model_id)) {
                return settings;
            }
        }
        return null;
    }

    public ModelSettings getActiveModelSettings() {
        String activeModelId = normalizeModelId(model_id);
        if (activeModelId.isEmpty()) {
            return null;
        }
        return getModelSettings(activeModelId);
    }

    public boolean activeSupportsSystemMessage() {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? supports_system_message : settings.supports_system_message;
    }

    public boolean activeInjectSystemPromptIntoUserMessage() {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? inject_system_prompt_into_user_message : settings.inject_system_prompt_into_user_message;
    }

    public String activeSystemPromptSuffix() {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? system_prompt_suffix : settings.system_prompt_suffix;
    }

    public double activeTemperature() {
        return activeTemperature(TemperatureScene.CHAT);
    }

    public double activeTemperature(TemperatureScene scene) {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? temperatureForScene(scene) : settings.temperatureFor(scene);
    }

    public String activeKeepAliveTime() {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? keep_alive_time : settings.keep_alive_time;
    }

    public List<CustomParameterEntry> activeCustomParameters() {
        ModelSettings settings = getActiveModelSettings();
        return settings == null ? copyCustomParameters(custom_parameters) : copyCustomParameters(settings.custom_parameters);
    }

    private void syncLegacyFieldsFrom(ModelSettings active) {
        if (active == null) {
            return;
        }
        temperature = active.temperatureFor(TemperatureScene.CHAT);
        chat_temperature = active.temperatureFor(TemperatureScene.CHAT);
        item_temperature = active.temperatureFor(TemperatureScene.ITEM);
        scoreboard_temperature = active.temperatureFor(TemperatureScene.SCOREBOARD);
        other_translations_temperature = active.temperatureFor(TemperatureScene.OTHER_TRANSLATIONS);
        wynntils_task_tracker_temperature = active.temperatureFor(TemperatureScene.WYNNTILS_TASK_TRACKER);
        wynn_npc_dialogue_temperature = active.temperatureFor(TemperatureScene.WYNN_NPC_DIALOGUE);
        keep_alive_time = normalizeKeepAlive(active.keep_alive_time);
        supports_system_message = active.supports_system_message;
        inject_system_prompt_into_user_message = active.inject_system_prompt_into_user_message;
        system_prompt_suffix = normalizeSuffix(active.system_prompt_suffix);
        custom_parameters = copyCustomParameters(active.custom_parameters);
    }

    private ModelSettings normalizeModelSettings(ModelSettings source, String normalizedId) {
        ModelSettings settings = new ModelSettings();
        settings.model_id = normalizedId;
        Double legacyTemperature = normalizeTemperatureOrNull(source.temperature);
        settings.chat_temperature = normalizeTemperature(source.chat_temperature, DEFAULT_CHAT_TEMPERATURE);
        settings.item_temperature = normalizeTemperature(source.item_temperature, legacyOrDefault(legacyTemperature, DEFAULT_ITEM_TEMPERATURE));
        settings.scoreboard_temperature = normalizeTemperature(source.scoreboard_temperature, legacyOrDefault(legacyTemperature, DEFAULT_SCOREBOARD_TEMPERATURE));
        settings.other_translations_temperature = normalizeTemperature(source.other_translations_temperature, legacyOrDefault(legacyTemperature, DEFAULT_OTHER_TRANSLATIONS_TEMPERATURE));
        settings.wynntils_task_tracker_temperature = normalizeTemperature(source.wynntils_task_tracker_temperature, legacyOrDefault(legacyTemperature, DEFAULT_WYNNTILS_TASK_TRACKER_TEMPERATURE));
        settings.wynn_npc_dialogue_temperature = normalizeTemperature(source.wynn_npc_dialogue_temperature, DEFAULT_WYNN_NPC_DIALOGUE_TEMPERATURE);
        settings.temperature = settings.temperatureFor(TemperatureScene.CHAT);
        settings.keep_alive_time = normalizeKeepAlive(source.keep_alive_time);
        settings.supports_system_message = source.supports_system_message;
        settings.inject_system_prompt_into_user_message = source.inject_system_prompt_into_user_message;
        settings.system_prompt_suffix = normalizeSuffix(source.system_prompt_suffix);
        settings.custom_parameters = copyCustomParameters(source.custom_parameters);
        return settings;
    }

    private ModelSettings createDefaultModelSettings(String modelId) {
        ModelSettings settings = new ModelSettings();
        settings.model_id = modelId;
        Double legacyTemperature = normalizeTemperatureOrNull(temperature);
        settings.chat_temperature = normalizeTemperature(chat_temperature, DEFAULT_CHAT_TEMPERATURE);
        settings.item_temperature = normalizeTemperature(item_temperature, legacyOrDefault(legacyTemperature, DEFAULT_ITEM_TEMPERATURE));
        settings.scoreboard_temperature = normalizeTemperature(scoreboard_temperature, legacyOrDefault(legacyTemperature, DEFAULT_SCOREBOARD_TEMPERATURE));
        settings.other_translations_temperature = normalizeTemperature(other_translations_temperature, legacyOrDefault(legacyTemperature, DEFAULT_OTHER_TRANSLATIONS_TEMPERATURE));
        settings.wynntils_task_tracker_temperature = normalizeTemperature(wynntils_task_tracker_temperature, legacyOrDefault(legacyTemperature, DEFAULT_WYNNTILS_TASK_TRACKER_TEMPERATURE));
        settings.wynn_npc_dialogue_temperature = normalizeTemperature(wynn_npc_dialogue_temperature, DEFAULT_WYNN_NPC_DIALOGUE_TEMPERATURE);
        settings.temperature = settings.temperatureFor(TemperatureScene.CHAT);
        settings.keep_alive_time = normalizeKeepAlive(keep_alive_time);
        settings.supports_system_message = supports_system_message;
        settings.inject_system_prompt_into_user_message = inject_system_prompt_into_user_message;
        settings.system_prompt_suffix = normalizeSuffix(system_prompt_suffix);
        settings.custom_parameters = copyCustomParameters(custom_parameters);
        return settings;
    }

    private String normalizeModelId(String modelIdRaw) {
        return modelIdRaw == null ? "" : modelIdRaw.trim();
    }

    private String normalizeKeepAlive(String keepAliveRaw) {
        String value = keepAliveRaw == null ? "" : keepAliveRaw.trim();
        return value.isEmpty() ? "1m" : value;
    }

    private String normalizeSuffix(String suffixRaw) {
        if (suffixRaw == null) {
            return "";
        }
        String trimmed = suffixRaw.trim();
        if (trimmed.isEmpty() || "\\no_think".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private double temperatureForScene(TemperatureScene scene) {
        return switch (scene == null ? TemperatureScene.CHAT : scene) {
            case CHAT -> normalizeTemperature(chat_temperature, DEFAULT_CHAT_TEMPERATURE);
            case ITEM -> normalizeTemperature(item_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_ITEM_TEMPERATURE));
            case SCOREBOARD -> normalizeTemperature(scoreboard_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_SCOREBOARD_TEMPERATURE));
            case OTHER_TRANSLATIONS -> normalizeTemperature(other_translations_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_OTHER_TRANSLATIONS_TEMPERATURE));
            case WYNNTILS_TASK_TRACKER -> normalizeTemperature(wynntils_task_tracker_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_WYNNTILS_TASK_TRACKER_TEMPERATURE));
            case WYNN_NPC_DIALOGUE -> normalizeTemperature(wynn_npc_dialogue_temperature, DEFAULT_WYNN_NPC_DIALOGUE_TEMPERATURE);
        };
    }

    private static double normalizeTemperature(Double value, double fallback) {
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    private static Double normalizeTemperatureOrNull(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    private static double legacyOrDefault(Double legacyTemperature, double fallback) {
        return legacyTemperature == null ? fallback : legacyTemperature;
    }

    private List<CustomParameterEntry> copyCustomParameters(List<CustomParameterEntry> source) {
        return CustomParameterEntry.deepCopyList(source);
    }

    public enum TemperatureScene {
        CHAT,
        ITEM,
        SCOREBOARD,
        OTHER_TRANSLATIONS,
        WYNNTILS_TASK_TRACKER,
        WYNN_NPC_DIALOGUE
    }

    public static class ModelSettings {
        public String model_id = "";
        public Double temperature = null;
        public Double chat_temperature = null;
        public Double item_temperature = null;
        public Double scoreboard_temperature = null;
        public Double other_translations_temperature = null;
        public Double wynntils_task_tracker_temperature = null;
        public Double wynn_npc_dialogue_temperature = null;
        public String keep_alive_time = "1m";
        public boolean supports_system_message = true;
        public boolean inject_system_prompt_into_user_message = true;
        public String system_prompt_suffix = "";
        public List<CustomParameterEntry> custom_parameters = new ArrayList<>();

        public double temperatureFor(TemperatureScene scene) {
            return switch (scene == null ? TemperatureScene.CHAT : scene) {
                case CHAT -> normalizeTemperature(chat_temperature, DEFAULT_CHAT_TEMPERATURE);
                case ITEM -> normalizeTemperature(item_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_ITEM_TEMPERATURE));
                case SCOREBOARD -> normalizeTemperature(scoreboard_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_SCOREBOARD_TEMPERATURE));
                case OTHER_TRANSLATIONS -> normalizeTemperature(other_translations_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_OTHER_TRANSLATIONS_TEMPERATURE));
                case WYNNTILS_TASK_TRACKER -> normalizeTemperature(wynntils_task_tracker_temperature, legacyOrDefault(normalizeTemperatureOrNull(temperature), DEFAULT_WYNNTILS_TASK_TRACKER_TEMPERATURE));
                case WYNN_NPC_DIALOGUE -> normalizeTemperature(wynn_npc_dialogue_temperature, DEFAULT_WYNN_NPC_DIALOGUE_TEMPERATURE);
            };
        }
    }
}
