package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCustomParameterDefaultsSupportTest {

    private static final String DEEPSEEK_URL = "https://api.deepseek.com";
    private static final String MIMO_URL = "https://api.xiaomimimo.com/v1";
    private static final String UNRELATED_URL = "https://api.openai.com/v1";

    private static ApiProviderProfile providerWithBaseUrl(String baseUrl) {
        ApiProviderProfile profile = ApiProviderProfile.createOpenAiDefault();
        profile.base_url = baseUrl;
        return profile;
    }

    private static ApiProviderProfile providerWithModel(String baseUrl, String modelId, List<CustomParameterEntry> parameters) {
        ApiProviderProfile profile = providerWithBaseUrl(baseUrl);
        profile.model_id = modelId;
        profile.model_ids = new ArrayList<>(List.of(modelId));
        profile.model_settings = new ArrayList<>();
        profile.ensureModelSettings();
        profile.getModelSettings(modelId).custom_parameters = CustomParameterEntry.deepCopyList(parameters);
        return profile;
    }

    private static ProviderManagerConfig providerManagerOf(ApiProviderProfile... profiles) {
        ProviderManagerConfig manager = new ProviderManagerConfig();
        manager.providers = new ArrayList<>(List.of(profiles));
        return manager;
    }

    private static CustomParameterEntry scalar(String key, String value) {
        CustomParameterEntry entry = new CustomParameterEntry();
        entry.key = key;
        entry.value = value;
        entry.is_object = false;
        entry.children = new ArrayList<>();
        return entry;
    }

    private static CustomParameterEntry objectEntry(String key, CustomParameterEntry... children) {
        CustomParameterEntry entry = new CustomParameterEntry();
        entry.key = key;
        entry.value = "";
        entry.is_object = true;
        entry.children = new ArrayList<>(List.of(children));
        return entry;
    }

    private static CustomParameterEntry thinkingWithType(String type) {
        return objectEntry("thinking", scalar("type", type));
    }

    private static CustomParameterEntry findParam(List<CustomParameterEntry> params, String key) {
        for (CustomParameterEntry entry : params) {
            if (entry != null && entry.key != null && key.equalsIgnoreCase(entry.key.trim())) {
                return entry;
            }
        }
        return null;
    }

    private static CustomParameterEntry findChild(CustomParameterEntry parent, String key) {
        return findParam(parent.children, key);
    }

    private static long countParams(List<CustomParameterEntry> params, String key) {
        return params.stream()
                .filter(entry -> entry != null && entry.key != null && entry.key.trim().equalsIgnoreCase(key))
                .count();
    }

    private static String thinkingTypeOf(CustomParameterEntry thinking) {
        CustomParameterEntry type = findChild(thinking, "type");
        return type == null ? null : type.value;
    }

    @Test
    void addsThinkingDisabledForDeepSeekBaseUrl() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
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
                providerWithBaseUrl(MIMO_URL),
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
                providerWithBaseUrl(UNRELATED_URL),
                List.of()
        );
        assertNull(findParam(result, "thinking"));
    }

    @Test
    void doesNotAddForNullProfile() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(null, List.of());
        assertNull(findParam(result, "thinking"));
    }

    @Test
    void doesNotDuplicateWhenThinkingParameterAlreadyDisabled() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(thinkingWithType("disabled"))
        );
        assertEquals(1, countParams(result, "thinking"));
        assertEquals("disabled", thinkingTypeOf(findParam(result, "thinking")));
    }

    @Test
    void newModelOverridesExplicitlyEnabledThinking() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(thinkingWithType("enabled"))
        );
        assertEquals("disabled", thinkingTypeOf(findParam(result, "thinking")));
    }

    @Test
    void newModelOverridesAutoThinking() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(thinkingWithType("auto"))
        );
        assertEquals("disabled", thinkingTypeOf(findParam(result, "thinking")));
    }

    @Test
    void newModelOverridesScalarTrueThinking() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(scalar("thinking", "true"))
        );
        CustomParameterEntry thinking = findParam(result, "thinking");
        assertTrue(thinking.is_object);
        assertEquals("disabled", thinkingTypeOf(thinking));
    }

    @Test
    void newModelReplacesScalarThinkingWithDisabledObject() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(scalar("thinking", "auto"))
        );
        CustomParameterEntry thinking = findParam(result, "thinking");
        assertTrue(thinking.is_object);
        assertEquals(1, thinking.children.size());
        assertEquals("disabled", thinkingTypeOf(thinking));
    }

    @Test
    void newModelPreservesSiblingKeysWhenRewritingThinkingType() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(objectEntry("thinking", scalar("type", "auto"), scalar("budget_tokens", "4096")))
        );
        CustomParameterEntry thinking = findParam(result, "thinking");
        assertEquals("disabled", thinkingTypeOf(thinking));
        CustomParameterEntry budget = findChild(thinking, "budget_tokens");
        assertNotNull(budget);
        assertEquals("4096", budget.value);
    }

    @Test
    void newModelPreservesUnrelatedParameters() {
        List<CustomParameterEntry> result = ModelCustomParameterDefaultsSupport.applyForNewModel(
                providerWithBaseUrl(DEEPSEEK_URL),
                List.of(scalar("top_p", "0.9"))
        );
        assertEquals("0.9", findParam(result, "top_p").value);
        assertNotNull(findParam(result, "thinking"));
    }

    @Test
    void newModelDoesNotMutateSourceList() {
        List<CustomParameterEntry> source = new ArrayList<>(List.of(thinkingWithType("enabled")));
        ModelCustomParameterDefaultsSupport.applyForNewModel(providerWithBaseUrl(DEEPSEEK_URL), source);
        assertEquals("enabled", thinkingTypeOf(findParam(source, "thinking")));
    }

    @Test
    void exitAddsThinkingToModelsWithoutTheParameter() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of());
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(List.of(profile.id + "::m1"), applied);
        assertEquals("disabled", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
    }

    @Test
    void exitKeepsExplicitlyEnabledThinkingObject() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(thinkingWithType("enabled")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(applied.isEmpty());
        assertEquals("enabled", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
    }

    @Test
    void exitKeepsExplicitlyEnabledScalarThinking() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(scalar("thinking", "true")));
        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        CustomParameterEntry thinking = findParam(profile.getModelSettings("m1").custom_parameters, "thinking");
        assertFalse(thinking.is_object);
        assertEquals("true", thinking.value);
    }

    @Test
    void exitKeepsExplicitlyEnabledTextValueThinking() {
        ApiProviderProfile profile = providerWithModel(MIMO_URL, "m1", List.of(scalar("thinking", "enabled")));
        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        CustomParameterEntry thinking = findParam(profile.getModelSettings("m1").custom_parameters, "thinking");
        assertEquals("enabled", thinking.value);
    }

    @Test
    void exitKeepsExplicitlyEnabledTypeTextTrueThinking() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(thinkingWithType("true")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(applied.isEmpty());
        assertEquals("true", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
    }

    @Test
    void exitKeepsExplicitlyEnabledThinkingCaseInsensitively() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(thinkingWithType("ENABLED")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(applied.isEmpty());
        assertEquals("ENABLED", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
    }

    @Test
    void exitNormalizesScalarFalseThinkingToDisabledObject() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(scalar("thinking", "false")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(1, applied.size());
        CustomParameterEntry thinking = findParam(profile.getModelSettings("m1").custom_parameters, "thinking");
        assertTrue(thinking.is_object);
        assertEquals("disabled", thinkingTypeOf(thinking));
    }

    @Test
    void exitAddsTypeWhenThinkingObjectHasNoTypeKey() {
        ApiProviderProfile profile = providerWithModel(
                DEEPSEEK_URL,
                "m1",
                List.of(objectEntry("thinking", scalar("budget_tokens", "4096")))
        );
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(1, applied.size());
        CustomParameterEntry thinking = findParam(profile.getModelSettings("m1").custom_parameters, "thinking");
        assertEquals("disabled", thinkingTypeOf(thinking));
        assertEquals("4096", findChild(thinking, "budget_tokens").value);
    }

    @Test
    void exitOverridesAutoThinking() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(thinkingWithType("auto")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(1, applied.size());
        assertEquals("disabled", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
    }

    @Test
    void exitPreservesSiblingKeysWhenRewritingThinkingType() {
        ApiProviderProfile profile = providerWithModel(
                DEEPSEEK_URL,
                "m1",
                List.of(objectEntry("thinking", scalar("type", "auto"), scalar("budget_tokens", "4096")))
        );
        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        CustomParameterEntry thinking = findParam(profile.getModelSettings("m1").custom_parameters, "thinking");
        assertEquals("disabled", thinkingTypeOf(thinking));
        assertEquals("4096", findChild(thinking, "budget_tokens").value);
    }

    @Test
    void exitIsIdempotentForAlreadyDisabledModels() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(thinkingWithType("disabled")));
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(applied.isEmpty());
        assertEquals(1, countParams(profile.getModelSettings("m1").custom_parameters, "thinking"));
    }

    @Test
    void exitSkipsUnrelatedBaseUrlProviders() {
        ApiProviderProfile profile = providerWithModel(UNRELATED_URL, "m1", List.of());
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(applied.isEmpty());
        assertTrue(profile.getModelSettings("m1").custom_parameters.isEmpty());
    }

    @Test
    void exitHandlesEveryModelOfHitProvider() {
        ApiProviderProfile profile = providerWithBaseUrl(DEEPSEEK_URL);
        profile.model_ids = new ArrayList<>(List.of("m1", "m2", "m3"));
        profile.model_id = "m1";
        profile.model_settings = new ArrayList<>();
        profile.ensureModelSettings();
        profile.getModelSettings("m2").custom_parameters = new ArrayList<>(List.of(thinkingWithType("enabled")));

        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(2, applied.size());
        assertEquals("disabled", thinkingTypeOf(findParam(profile.getModelSettings("m1").custom_parameters, "thinking")));
        assertEquals("enabled", thinkingTypeOf(findParam(profile.getModelSettings("m2").custom_parameters, "thinking")));
        assertEquals("disabled", thinkingTypeOf(findParam(profile.getModelSettings("m3").custom_parameters, "thinking")));
    }

    @Test
    void exitCoversDisabledProviderToo() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of());
        profile.enabled = false;
        List<String> applied = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertEquals(1, applied.size());
    }

    @Test
    void exitLeavesLegacyTopLevelParametersUntouched() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of());
        profile.custom_parameters = new ArrayList<>();

        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        assertTrue(profile.custom_parameters.isEmpty());
    }

    @Test
    void exitIgnoresUnrelatedParameterKeys() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(scalar("enable_thinking", "true")));
        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        List<CustomParameterEntry> parameters = profile.getModelSettings("m1").custom_parameters;
        assertEquals("true", findParam(parameters, "enable_thinking").value);
        assertNotNull(findParam(parameters, "thinking"));
    }

    @Test
    void exitHandlesNullProviderManager() {
        assertTrue(ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels((ProviderManagerConfig) null).isEmpty());
    }

    @Test
    void exitHandlesProviderWithoutModels() {
        ApiProviderProfile profile = providerWithBaseUrl(DEEPSEEK_URL);
        profile.model_id = "";
        profile.model_ids = new ArrayList<>();
        profile.model_settings = new ArrayList<>();

        assertTrue(ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile)).isEmpty());
    }

    @Test
    void exitKeepsUnrelatedParametersIntact() {
        ApiProviderProfile profile = providerWithModel(DEEPSEEK_URL, "m1", List.of(scalar("top_p", "0.9")));
        ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(providerManagerOf(profile));

        List<CustomParameterEntry> parameters = profile.getModelSettings("m1").custom_parameters;
        assertEquals("0.9", findParam(parameters, "top_p").value);
        assertEquals("disabled", thinkingTypeOf(findParam(parameters, "thinking")));
    }
}
