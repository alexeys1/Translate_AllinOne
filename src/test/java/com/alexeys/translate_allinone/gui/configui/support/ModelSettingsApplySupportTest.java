package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelSettingsApplySupportTest {
    @Test
    void renamesExistingModelWithoutCreatingAnotherModel() {
        ApiProviderProfile profile = ApiProviderProfile.createOpenAiDefault();
        profile.model_ids = new ArrayList<>(List.of("deepseek-v4-flash"));
        profile.ensureModelSettings();

        ModelSettingsApplySupport.ApplyResult result = ModelSettingsApplySupport.apply(
                profile,
                "deepseek-v4-flash",
                "deepseek-v4-pro",
                "0.7",
                "0.4",
                "0.3",
                "0.5",
                "0.2",
                "1.1",
                "1m",
                true,
                true,
                "suffix",
                List.of(),
                true
        );

        assertFalse(result.creating());
        assertEquals(List.of("deepseek-v4-pro"), profile.model_ids);
        assertEquals(1, profile.model_settings.size());
        assertNotNull(profile.getModelSettings("deepseek-v4-pro"));
        assertNull(profile.getModelSettings("deepseek-v4-flash"));
    }
}
