package com.cedarxuesong.translate_allinone.utils.config;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CustomParameterEntry;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;

import java.util.ArrayList;
import java.util.List;

public final class ProviderRouteResolver {
    private ProviderRouteResolver() {
    }

    public enum Route {
        ITEM,
        SCOREBOARD,
        WYNNCRAFT,
        WYNN_NPC_DIALOGUE,
        WYNNTILS_TASK_TRACKER,
        CHAT_INPUT,
        CHAT_OUTPUT
    }

    public static ApiProviderProfile resolve(ModConfig config, Route route) {
        if (config == null || config.providerManager == null) {
            return null;
        }

        ProviderManagerConfig manager = config.providerManager;
        manager.ensureDefaults();

        String routeKey = switch (route) {
            case ITEM -> manager.routes.item;
            case SCOREBOARD -> manager.routes.scoreboard;
            case WYNNCRAFT -> resolveWynncraftRouteKey(manager.routes.wynncraft, "", "");
            case WYNN_NPC_DIALOGUE -> resolveWynncraftRouteKey(manager.routes.wynncraft, manager.routes.wynn_npc_dialogue, "");
            case WYNNTILS_TASK_TRACKER -> resolveWynncraftRouteKey(manager.routes.wynncraft, manager.routes.wynntils_task_tracker, "");
            case CHAT_INPUT -> manager.routes.chat_input;
            case CHAT_OUTPUT -> manager.routes.chat_output;
        };

        if (routeKey == null || routeKey.isBlank()) {
            return null;
        }

        String providerId = ProviderManagerConfig.extractProviderId(routeKey);
        String modelId = ProviderManagerConfig.extractModelId(routeKey);
        if (providerId.isBlank() || modelId.isBlank()) {
            return null;
        }

        ApiProviderProfile provider = manager.findById(providerId);
        if (provider == null || !provider.enabled) {
            return null;
        }

        provider.ensureModelSettings();
        ApiProviderProfile.ModelSettings modelSettings = provider.getModelSettings(modelId);
        if (modelSettings == null) {
            return null;
        }

        return snapshotForModel(provider, modelSettings, temperatureSceneFor(route));
    }

    private static String resolveWynncraftRouteKey(String sharedRoute, String primaryRoute, String fallbackRoute) {
        if (sharedRoute != null && !sharedRoute.isBlank()) {
            return sharedRoute;
        }
        if (primaryRoute != null && !primaryRoute.isBlank()) {
            return primaryRoute;
        }
        return fallbackRoute == null ? "" : fallbackRoute;
    }

    private static ApiProviderProfile snapshotForModel(
            ApiProviderProfile source,
            ApiProviderProfile.ModelSettings modelSettings,
            ApiProviderProfile.TemperatureScene temperatureScene
    ) {
        ApiProviderProfile snapshot = new ApiProviderProfile();

        snapshot.id = source.id;
        snapshot.name = source.name;
        snapshot.enabled = source.enabled;
        snapshot.type = source.type;
        snapshot.base_url = source.base_url;
        snapshot.api_key = source.api_key;

        snapshot.model_id = modelSettings.model_id;
        snapshot.model_ids = new ArrayList<>(1);
        snapshot.model_ids.add(modelSettings.model_id);

        List<CustomParameterEntry> customParametersCopy = cloneCustomParameters(modelSettings.custom_parameters);

        ApiProviderProfile.ModelSettings modelCopy = new ApiProviderProfile.ModelSettings();
        modelCopy.model_id = modelSettings.model_id;
        double selectedTemperature = modelSettings.temperatureFor(temperatureScene);
        modelCopy.temperature = selectedTemperature;
        modelCopy.chat_temperature = selectedTemperature;
        modelCopy.item_temperature = modelSettings.temperatureFor(ApiProviderProfile.TemperatureScene.ITEM);
        modelCopy.scoreboard_temperature = modelSettings.temperatureFor(ApiProviderProfile.TemperatureScene.SCOREBOARD);
        modelCopy.wynntils_task_tracker_temperature = modelSettings.temperatureFor(ApiProviderProfile.TemperatureScene.WYNNTILS_TASK_TRACKER);
        modelCopy.wynn_npc_dialogue_temperature = modelSettings.temperatureFor(ApiProviderProfile.TemperatureScene.WYNN_NPC_DIALOGUE);
        modelCopy.keep_alive_time = modelSettings.keep_alive_time;
        modelCopy.enable_structured_output_if_available = modelSettings.enable_structured_output_if_available;
        modelCopy.supports_system_message = modelSettings.supports_system_message;
        modelCopy.inject_system_prompt_into_user_message = modelSettings.inject_system_prompt_into_user_message;
        modelCopy.system_prompt_suffix = modelSettings.system_prompt_suffix;
        modelCopy.custom_parameters = customParametersCopy;
        snapshot.model_settings = new ArrayList<>(1);
        snapshot.model_settings.add(modelCopy);

        snapshot.temperature = modelCopy.temperature;
        snapshot.chat_temperature = modelCopy.chat_temperature;
        snapshot.item_temperature = modelCopy.item_temperature;
        snapshot.scoreboard_temperature = modelCopy.scoreboard_temperature;
        snapshot.wynntils_task_tracker_temperature = modelCopy.wynntils_task_tracker_temperature;
        snapshot.wynn_npc_dialogue_temperature = modelCopy.wynn_npc_dialogue_temperature;
        snapshot.keep_alive_time = modelCopy.keep_alive_time;
        snapshot.enable_structured_output_if_available = modelCopy.enable_structured_output_if_available;
        snapshot.supports_system_message = modelCopy.supports_system_message;
        snapshot.inject_system_prompt_into_user_message = modelCopy.inject_system_prompt_into_user_message;
        snapshot.system_prompt_suffix = modelCopy.system_prompt_suffix;
        snapshot.custom_parameters = customParametersCopy;
        if (source.system_prompt_overrides != null) {
            snapshot.system_prompt_overrides = new java.util.LinkedHashMap<>(source.system_prompt_overrides);
        }
        return snapshot;
    }

    private static ApiProviderProfile.TemperatureScene temperatureSceneFor(Route route) {
        return switch (route) {
            case ITEM -> ApiProviderProfile.TemperatureScene.ITEM;
            case SCOREBOARD -> ApiProviderProfile.TemperatureScene.SCOREBOARD;
            case WYNNCRAFT, WYNN_NPC_DIALOGUE -> ApiProviderProfile.TemperatureScene.WYNN_NPC_DIALOGUE;
            case WYNNTILS_TASK_TRACKER -> ApiProviderProfile.TemperatureScene.WYNNTILS_TASK_TRACKER;
            case CHAT_INPUT, CHAT_OUTPUT -> ApiProviderProfile.TemperatureScene.CHAT;
        };
    }

    private static List<CustomParameterEntry> cloneCustomParameters(List<CustomParameterEntry> source) {
        return CustomParameterEntry.deepCopyList(source);
    }
}
