package com.cedarxuesong.translate_allinone.gui.configui.support;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelCustomParameterDefaultsSupport {
    private static final String DEEPSEEK_URL_MARKER = "deepseek";
    private static final String THINKING_PARAMETER_KEY = "thinking";
    private static final String THINKING_TYPE_KEY = "type";
    private static final String THINKING_DISABLED_VALUE = "disabled";

    private ModelCustomParameterDefaultsSupport() {
    }

    public static List<CustomParameterEntry> applyForNewModel(ApiProviderProfile profile, List<CustomParameterEntry> source) {
        List<CustomParameterEntry> result = CustomParameterEntry.deepCopyList(source);
        if (!shouldUseDeepSeekThinkingDefault(profile) || hasTopLevelParameter(result, THINKING_PARAMETER_KEY)) {
            return result;
        }
        result.add(createDeepSeekThinkingParameter());
        return result;
    }

    private static boolean shouldUseDeepSeekThinkingDefault(ApiProviderProfile profile) {
        String baseUrl = profile == null || profile.base_url == null ? "" : profile.base_url;
        return baseUrl.toLowerCase(Locale.ROOT).contains(DEEPSEEK_URL_MARKER);
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

    private static CustomParameterEntry createDeepSeekThinkingParameter() {
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
