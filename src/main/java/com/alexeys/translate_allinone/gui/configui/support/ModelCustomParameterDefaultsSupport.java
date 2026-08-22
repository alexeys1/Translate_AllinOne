package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelCustomParameterDefaultsSupport {
    private static final String DEEPSEEK_URL_MARKER = "deepseek";
    private static final String MIMO_URL_MARKER = "xiaomimimo";
    private static final String THINKING_PARAMETER_KEY = "thinking";
    private static final String THINKING_TYPE_KEY = "type";
    private static final String THINKING_DISABLED_VALUE = "disabled";

    private ModelCustomParameterDefaultsSupport() {
    }

    public static List<CustomParameterEntry> applyForNewModel(ApiProviderProfile profile, List<CustomParameterEntry> source) {
        List<CustomParameterEntry> result = CustomParameterEntry.deepCopyList(source);
        if (!shouldDisableThinkingByDefault(profile) || hasTopLevelParameter(result, THINKING_PARAMETER_KEY)) {
            return result;
        }
        result.add(createThinkingDisabledParameter());
        return result;
    }

    private static boolean shouldDisableThinkingByDefault(ApiProviderProfile profile) {
        String baseUrl = profile == null || profile.base_url == null ? "" : profile.base_url;
        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains(DEEPSEEK_URL_MARKER) || normalized.contains(MIMO_URL_MARKER);
    }

    private static boolean hasTopLevelParameter(List<CustomParameterEntry> parameters, String key) {
        if (parameters == null || key == null) {
            return false;
        }
        for (CustomParameterEntry parameter : parameters) {
            if (parameter == null || parameter.key == null) {
                continue;
            }
            if (key.equalsIgnoreCase(parameter.key.trim())) {
                return true;
            }
        }
        return false;
    }

    private static CustomParameterEntry createThinkingDisabledParameter() {
        CustomParameterEntry typeEntry = new CustomParameterEntry();
        typeEntry.key = THINKING_TYPE_KEY;
        typeEntry.value = THINKING_DISABLED_VALUE;
        typeEntry.is_object = false;
        typeEntry.children = new ArrayList<>();

        CustomParameterEntry thinkingEntry = new CustomParameterEntry();
        thinkingEntry.key = THINKING_PARAMETER_KEY;
        thinkingEntry.value = "";
        thinkingEntry.is_object = true;
        thinkingEntry.children = new ArrayList<>(List.of(typeEntry));
        return thinkingEntry;
    }
}
