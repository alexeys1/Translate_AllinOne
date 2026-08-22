package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCustomParameterDefaultsSupportTest {

    private static ApiProviderProfile providerWithBaseUrl(String baseUrl) {
        ApiProviderProfile profile = ApiProviderProfile.createOpenAiDefault();
        profile.base_url = baseUrl;
        return profile;
    }

    private static CustomParameterEntry findParam(List<CustomParameterEntry> params, String key) {
        for (CustomParameterEntry entry : params) {
            if (entry != null && entry.key != null && key.equalsIgnoreCase(entry.key.trim())) {
                return entry;
            }
        }
        return null;
    }

    @Test
    void addsThinkingDisabledForDeepSeekBaseUrl() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl("https://api.deepseek.com"),
                List.of()
        );
        CustomParameterEntry thinking = findParam(result, "thinking");
        assertNotNull(thinking);
        assertTrue(thinking.is_object);
        assertEquals(1, thinking.children.size());
        assertEquals("type", thinking.children.get(0).key);
        assertEquals("disabled", thinking.children.get(0).value);
    }

    @Test
    void addsThinkingDisabledForMimoBaseUrl() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl("https://api.xiaomimimo.com/v1"),
                List.of()
        );
        CustomParameterEntry thinking = findParam(result, "thinking");
        assertNotNull(thinking);
        assertTrue(thinking.is_object);
        assertEquals(1, thinking.children.size());
        assertEquals("type", thinking.children.get(0).key);
        assertEquals("disabled", thinking.children.get(0).value);
    }

    @Test
    void matchesBaseUrlCaseInsensitively() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl("https://API.XIAOMIMIMO.COM/v1"),
                List.of()
        );
        assertNotNull(findParam(result, "thinking"));
    }

    @Test
    void doesNotAddForUnrelatedBaseUrl() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl("https://api.openai.com/v1"),
                List.of()
        );
        assertNull(findParam(result, "thinking"));
    }

    @Test
    void doesNotDuplicateWhenThinkingParameterAlreadyPresent() {
        CustomParameterEntry existing = new CustomParameterEntry();
        existing.key = "thinking";
        existing.value = "auto";
        existing.is_object = false;

        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl("https://api.deepseek.com"),
                List.of(existing)
        );
        long thinkingCount = result.stream()
                .filter(entry -> entry != null && entry.key != null && entry.key.trim().equalsIgnoreCase("thinking"))
                .count();
        assertEquals(1, thinkingCount);
        CustomParameterEntry preserved = findParam(result, "thinking");
        assertFalse(preserved.is_object);
        assertEquals("auto", preserved.value);
    }
}
