package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelCustomParameterDefaultsSupport {
    private static final String DEEPSEEK_URL_MARKER = "deepseek";
    private static final String MIMO_URL_MARKER = "xiaomimimo";
    private static final String THINKING_PARAMETER_KEY = "thinking";
    private static final String THINKING_TYPE_KEY = "type";
    private static final String THINKING_DISABLED_VALUE = "disabled";
    private static final String THINKING_ENABLED_VALUE = "enabled";
    private static final String BOOLEAN_TRUE_VALUE = "true";

    private ModelCustomParameterDefaultsSupport() {
    }

    public static List<CustomParameterEntry> applyForNewModel(ApiProviderProfile profile, List<CustomParameterEntry> source) {
        List<CustomParameterEntry> result = CustomParameterEntry.deepCopyList(source);
        if (shouldDisableThinking(profile)) {
            disableThinkingAlways(result);
        }
        return result;
    }

    public static List<String> applyDefaultsToAllModels(ProviderManagerConfig providerManager) {
        List<String> appliedRoutes = new ArrayList<>();
        if (providerManager == null || providerManager.providers == null) {
            return appliedRoutes;
        }

        for (ApiProviderProfile profile : providerManager.providers) {
            applyDefaultsToProvider(profile, appliedRoutes);
        }
        return appliedRoutes;
    }

    private static void applyDefaultsToProvider(ApiProviderProfile profile, List<String> appliedRoutes) {
        if (profile == null || !shouldDisableThinking(profile)) {
            return;
        }

        for (ApiProviderProfile.ModelSettings settings : profile.ensureModelSettings()) {
            if (settings == null) {
                continue;
            }
            if (disableThinkingUnlessEnabled(settings.custom_parameters)) {
                appliedRoutes.add(ProviderManagerConfig.composeRouteKey(profile.id, settings.model_id));
            }
        }
    }

    private static boolean disableThinkingAlways(List<CustomParameterEntry> parameters) {
        return applyThinkingDisable(parameters, false);
    }

    private static boolean disableThinkingUnlessEnabled(List<CustomParameterEntry> parameters) {
        return applyThinkingDisable(parameters, true);
    }

    private static boolean applyThinkingDisable(List<CustomParameterEntry> parameters, boolean keepExplicitlyEnabled) {
        if (parameters == null) {
            return false;
        }

        CustomParameterEntry thinkingEntry = findParameter(parameters, THINKING_PARAMETER_KEY);
        if (thinkingEntry == null) {
            parameters.add(createThinkingDisabledParameter());
            return true;
        }
        if (keepExplicitlyEnabled && isThinkingExplicitlyEnabled(thinkingEntry)) {
            return false;
        }
        return disableThinking(thinkingEntry);
    }

    private static boolean disableThinking(CustomParameterEntry thinkingEntry) {
        if (!isObjectEntry(thinkingEntry)) {
            thinkingEntry.is_object = true;
            thinkingEntry.value = "";
            thinkingEntry.children = new ArrayList<>(List.of(createTypeDisabledEntry()));
            return true;
        }

        CustomParameterEntry typeEntry = findParameter(thinkingEntry.children, THINKING_TYPE_KEY);
        if (typeEntry == null) {
            thinkingEntry.children.add(createTypeDisabledEntry());
            return true;
        }
        if (!isObjectEntry(typeEntry) && isDisabledValue(typeEntry.value)) {
            return false;
        }

        typeEntry.is_object = false;
        typeEntry.value = THINKING_DISABLED_VALUE;
        typeEntry.children = new ArrayList<>();
        return true;
    }

    private static boolean isThinkingExplicitlyEnabled(CustomParameterEntry thinkingEntry) {
        if (isObjectEntry(thinkingEntry)) {
            CustomParameterEntry typeEntry = findParameter(thinkingEntry.children, THINKING_TYPE_KEY);
            return typeEntry != null && !isObjectEntry(typeEntry) && isEnabledValue(normalizeText(typeEntry.value));
        }
        return isEnabledValue(normalizeText(thinkingEntry.value));
    }

    private static boolean isEnabledValue(String value) {
        return THINKING_ENABLED_VALUE.equalsIgnoreCase(value) || BOOLEAN_TRUE_VALUE.equalsIgnoreCase(value);
    }

    private static boolean isDisabledValue(String value) {
        return THINKING_DISABLED_VALUE.equalsIgnoreCase(normalizeText(value));
    }

    private static boolean isObjectEntry(CustomParameterEntry entry) {
        return entry != null && (entry.is_object || (entry.children != null && !entry.children.isEmpty()));
    }

    private static boolean shouldDisableThinking(ApiProviderProfile profile) {
        String baseUrl = profile == null || profile.base_url == null ? "" : profile.base_url;
        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains(DEEPSEEK_URL_MARKER) || normalized.contains(MIMO_URL_MARKER);
    }

    private static CustomParameterEntry findParameter(List<CustomParameterEntry> parameters, String key) {
        if (parameters == null || key == null) {
            return null;
        }
        for (CustomParameterEntry parameter : parameters) {
            if (parameter == null || parameter.key == null) {
                continue;
            }
            if (key.equalsIgnoreCase(parameter.key.trim())) {
                return parameter;
            }
        }
        return null;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static CustomParameterEntry createThinkingDisabledParameter() {
        CustomParameterEntry thinkingEntry = new CustomParameterEntry();
        thinkingEntry.key = THINKING_PARAMETER_KEY;
        thinkingEntry.value = "";
        thinkingEntry.is_object = true;
        thinkingEntry.children = new ArrayList<>(List.of(createTypeDisabledEntry()));
        return thinkingEntry;
    }

    private static CustomParameterEntry createTypeDisabledEntry() {
        CustomParameterEntry typeEntry = new CustomParameterEntry();
        typeEntry.key = THINKING_TYPE_KEY;
        typeEntry.value = THINKING_DISABLED_VALUE;
        typeEntry.is_object = false;
        typeEntry.children = new ArrayList<>();
        return typeEntry;
    }
}
