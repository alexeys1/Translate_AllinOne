package com.alexeys.translate_allinone.utils.config.pojos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiProviderProfileTest {
    @Test
    void defaultsToEmptySystemPromptSuffix() {
        ApiProviderProfile profile = new ApiProviderProfile();
        ApiProviderProfile.ModelSettings settings = new ApiProviderProfile.ModelSettings();

        assertEquals("", profile.system_prompt_suffix);
        assertEquals("", settings.system_prompt_suffix);
    }

    @Test
    void clearsLegacyNoThinkSuffixOnNormalization() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.system_prompt_suffix = "\\no_think";
        profile.model_id = "test-model";
        profile.model_ids = List.of("test-model");

        profile.ensureModelSettings();

        assertEquals("", profile.system_prompt_suffix);
        assertEquals("", profile.activeSystemPromptSuffix());
    }

    @Test
    void clearsNoThinkSuffixInsideModelSettings() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.model_id = "test-model";
        ApiProviderProfile.ModelSettings settings = new ApiProviderProfile.ModelSettings();
        settings.model_id = "test-model";
        settings.system_prompt_suffix = "\\no_think";
        profile.model_settings = new ArrayList<>();
        profile.model_settings.add(settings);

        profile.ensureModelSettings();

        assertEquals("", profile.activeSystemPromptSuffix());
    }

    @Test
    void preservesCustomSystemPromptSuffix() {
        ApiProviderProfile profile = new ApiProviderProfile();
        profile.system_prompt_suffix = "custom";
        profile.model_id = "test-model";
        profile.model_ids = List.of("test-model");

        profile.ensureModelSettings();

        assertEquals("custom", profile.activeSystemPromptSuffix());
    }
}
