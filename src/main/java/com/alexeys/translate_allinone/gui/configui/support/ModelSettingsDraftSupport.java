package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.ArrayList;
import java.util.List;

public final class ModelSettingsDraftSupport {
    private ModelSettingsDraftSupport() {
    }

    public static Draft fromProfile(ApiProviderProfile profile, String originalModelId) {
        profile.ensureModelSettings();
        String resolvedOriginalId = ProviderProfileSupport.sanitizeText(originalModelId).trim();

        ApiProviderProfile.ModelSettings sourceSettings = profile.getModelSettings(resolvedOriginalId);
        if (sourceSettings == null) {
            sourceSettings = profile.getActiveModelSettings();
        }
        if (sourceSettings == null) {
            sourceSettings = new ApiProviderProfile.ModelSettings();
        }

        boolean creating = resolvedOriginalId.isBlank();
        List<CustomParameterEntry> customParameters = CustomParameterEntry.deepCopyList(sourceSettings.custom_parameters);
        if (creating) {
            customParameters = ModelCustomParameterDefaultsSupport.applyForNewModel(profile, customParameters);
        }
        return new Draft(
                profile.id,
                resolvedOriginalId,
                creating ? "" : ProviderProfileSupport.sanitizeText(sourceSettings.model_id),
                ModelSettingsValueSupport.formatTemperature(sourceSettings.temperatureFor(ApiProviderProfile.TemperatureScene.CHAT)),
                ModelSettingsValueSupport.formatTemperature(sourceSettings.temperatureFor(ApiProviderProfile.TemperatureScene.ITEM)),
                ModelSettingsValueSupport.formatTemperature(sourceSettings.temperatureFor(ApiProviderProfile.TemperatureScene.SCOREBOARD)),
                ModelSettingsValueSupport.formatTemperature(sourceSettings.temperatureFor(ApiProviderProfile.TemperatureScene.WYNNTILS_TASK_TRACKER)),
                ModelSettingsValueSupport.formatTemperature(sourceSettings.temperatureFor(ApiProviderProfile.TemperatureScene.WYNN_NPC_DIALOGUE)),
                ProviderProfileSupport.sanitizeText(sourceSettings.keep_alive_time),
                sourceSettings.supports_system_message,
                sourceSettings.inject_system_prompt_into_user_message,
                ProviderProfileSupport.sanitizeText(sourceSettings.system_prompt_suffix),
                customParameters,
                CustomParameterEntry.deepCopyList(customParameters),
                resolvedOriginalId.isBlank() || resolvedOriginalId.equals(profile.model_id)
        );
    }

    public static Draft empty() {
        return new Draft(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                true,
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                false
        );
    }

    public record Draft(
            String providerId,
            String originalModelId,
            String modelIdDraft,
            String chatTemperatureDraft,
            String itemTemperatureDraft,
            String scoreboardTemperatureDraft,
            String wynntilsTaskTrackerTemperatureDraft,
            String wynnNpcDialogueTemperatureDraft,
            String keepAliveDraft,
            boolean supportsSystem,
            boolean injectPromptIntoUser,
            String systemPromptSuffixDraft,
            List<CustomParameterEntry> customParametersDraft,
            List<CustomParameterEntry> customParametersBackup,
            boolean setDefault
    ) {
    }
}
