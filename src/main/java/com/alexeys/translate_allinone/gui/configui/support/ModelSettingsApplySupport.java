package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.List;

public final class ModelSettingsApplySupport {
    private ModelSettingsApplySupport() {
    }

    public static ApplyResult apply(
            ApiProviderProfile profile,
            String originalModelId,
            String modelIdDraft,
            String chatTemperatureDraft,
            String itemTemperatureDraft,
            String scoreboardTemperatureDraft,
            String otherTranslationsTemperatureDraft,
            String wynntilsTaskTrackerTemperatureDraft,
            String wynnNpcDialogueTemperatureDraft,
            String keepAliveDraft,
            boolean supportsSystem,
            boolean injectPromptIntoUser,
            String systemPromptSuffixDraft,
            List<CustomParameterEntry> customParametersDraft,
            boolean setDefault
    ) {
        String nextModelId = ProviderProfileSupport.sanitizeText(modelIdDraft).trim();
        if (nextModelId.isEmpty()) {
            return ApplyResult.error("error.model_id_required", null);
        }

        Double parsedChatTemperature = ModelSettingsValueSupport.parseTemperatureInput(chatTemperatureDraft);
        Double parsedItemTemperature = ModelSettingsValueSupport.parseTemperatureInput(itemTemperatureDraft);
        Double parsedScoreboardTemperature = ModelSettingsValueSupport.parseTemperatureInput(scoreboardTemperatureDraft);
        Double parsedOtherTranslationsTemperature = ModelSettingsValueSupport.parseTemperatureInput(otherTranslationsTemperatureDraft);
        Double parsedWynntilsTaskTrackerTemperature = ModelSettingsValueSupport.parseTemperatureInput(wynntilsTaskTrackerTemperatureDraft);
        Double parsedWynnNpcDialogueTemperature = ModelSettingsValueSupport.parseTemperatureInput(wynnNpcDialogueTemperatureDraft);
        if (parsedChatTemperature == null
                || parsedItemTemperature == null
                || parsedScoreboardTemperature == null
                || parsedOtherTranslationsTemperature == null
                || parsedWynntilsTaskTrackerTemperature == null
                || parsedWynnNpcDialogueTemperature == null) {
            return ApplyResult.error("error.temperature_invalid", null);
        }

        if (ModelSettingsMutationSupport.hasModelIdConflict(profile, nextModelId, originalModelId)) {
            return ApplyResult.error("error.model_id_exists", nextModelId);
        }

        boolean creating = originalModelId == null || originalModelId.isBlank();
        ModelSettingsMutationSupport.upsertModelSettings(
                profile,
                originalModelId,
                nextModelId,
                parsedChatTemperature,
                parsedItemTemperature,
                parsedScoreboardTemperature,
                parsedOtherTranslationsTemperature,
                parsedWynntilsTaskTrackerTemperature,
                parsedWynnNpcDialogueTemperature,
                ModelSettingsValueSupport.normalizeKeepAliveInput(keepAliveDraft),
                supportsSystem,
                injectPromptIntoUser,
                ProviderProfileSupport.sanitizeText(systemPromptSuffixDraft),
                customParametersDraft,
                setDefault
        );
        return ApplyResult.success(creating, nextModelId);
    }

    public record ApplyResult(boolean success, boolean creating, String modelId, String errorKey, String errorArg) {
        public static ApplyResult success(boolean creating, String modelId) {
            return new ApplyResult(true, creating, modelId, null, null);
        }

        public static ApplyResult error(String errorKey, String errorArg) {
            return new ApplyResult(false, false, "", errorKey, errorArg);
        }
    }
}
