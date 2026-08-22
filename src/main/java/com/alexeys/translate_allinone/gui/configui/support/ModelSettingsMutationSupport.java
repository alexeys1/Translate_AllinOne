package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.List;

public final class ModelSettingsMutationSupport {
    private ModelSettingsMutationSupport() {
    }

    public static boolean hasModelIdConflict(ApiProviderProfile profile, String nextModelId, String originalModelId) {
        profile.ensureModelSettings();
        boolean creating = originalModelId == null || originalModelId.isBlank();
        ApiProviderProfile.ModelSettings conflict = profile.getModelSettings(nextModelId);
        return conflict != null && (creating || !nextModelId.equals(originalModelId));
    }

    public static void upsertModelSettings(
            ApiProviderProfile profile,
            String originalModelId,
            String nextModelId,
            double chatTemperature,
            double itemTemperature,
            double scoreboardTemperature,
            double otherTranslationsTemperature,
            double wynntilsTaskTrackerTemperature,
            double wynnNpcDialogueTemperature,
            String keepAliveTime,
            boolean supportsSystemMessage,
            boolean injectPromptIntoUser,
            String systemPromptSuffix,
            List<CustomParameterEntry> customParameters,
            boolean setAsDefault
    ) {
        profile.ensureModelSettings();
        boolean creating = originalModelId == null || originalModelId.isBlank();

        ApiProviderProfile.ModelSettings settings = creating ? null : profile.getModelSettings(originalModelId);
        if (settings == null) {
            settings = new ApiProviderProfile.ModelSettings();
            profile.model_settings.add(settings);
        }

        settings.model_id = nextModelId;
        int originalModelIndex = creating ? -1 : profile.model_ids.indexOf(originalModelId);
        if (originalModelIndex >= 0) {
            profile.model_ids.set(originalModelIndex, nextModelId);
        } else if (!profile.model_ids.contains(nextModelId)) {
            profile.model_ids.add(nextModelId);
        }
        settings.temperature = chatTemperature;
        settings.chat_temperature = chatTemperature;
        settings.item_temperature = itemTemperature;
        settings.scoreboard_temperature = scoreboardTemperature;
        settings.other_translations_temperature = otherTranslationsTemperature;
        settings.wynntils_task_tracker_temperature = wynntilsTaskTrackerTemperature;
        settings.wynn_npc_dialogue_temperature = wynnNpcDialogueTemperature;
        settings.keep_alive_time = keepAliveTime;
        settings.supports_system_message = supportsSystemMessage;
        settings.inject_system_prompt_into_user_message = injectPromptIntoUser;
        settings.system_prompt_suffix = systemPromptSuffix;
        settings.custom_parameters = creating
                ? ModelCustomParameterDefaultsSupport.applyForNewModel(profile, customParameters)
                : CustomParameterEntry.deepCopyList(customParameters);

        if (creating || setAsDefault || profile.model_id == null || profile.model_id.isBlank() || profile.model_id.equals(originalModelId)) {
            profile.model_id = nextModelId;
        }

        profile.ensureModelSettings();
    }

    public static boolean removeModel(ApiProviderProfile profile, String modelId) {
        profile.ensureModelSettings();
        boolean removed = profile.model_settings.removeIf(settings -> settings != null && modelId.equals(settings.model_id));
        if (!removed) {
            return false;
        }

        profile.model_ids.removeIf(existing -> modelId.equals(existing));
        if (modelId.equals(profile.model_id)) {
            profile.model_id = "";
        }
        profile.ensureModelSettings();
        return true;
    }
}
