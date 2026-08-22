package com.alexeys.translate_allinone.utils.llmapi;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProviderSettings(OpenAISettings openAISettings, OllamaSettings ollamaSettings) {

    public static ProviderSettings fromOpenAI(OpenAISettings settings) {
        return new ProviderSettings(settings, null);
    }

    public static ProviderSettings fromOllama(OllamaSettings settings) {
        return new ProviderSettings(null, settings);
    }

    public static ProviderSettings fromProviderProfile(ApiProviderProfile profile) {
        if (profile == null) {
            return new ProviderSettings(null, null);
        }

        ApiProviderProfile.ModelSettings activeModelSettings = profile.getActiveModelSettings();
        ApiProviderType providerType = profile.type == null ? ApiProviderType.OPENAI_COMPAT : profile.type;
        String modelId = activeModelSettings == null ? profile.model_id : activeModelSettings.model_id;
        double temperature = activeModelSettings == null
                ? profile.activeTemperature()
                : activeModelSettings.temperature == null
                ? activeModelSettings.temperatureFor(ApiProviderProfile.TemperatureScene.CHAT)
                : activeModelSettings.temperature;
        String keepAlive = activeModelSettings == null ? profile.keep_alive_time : activeModelSettings.keep_alive_time;
        Map<String, Object> parameters = toParameterMap(activeModelSettings == null ? profile.custom_parameters : activeModelSettings.custom_parameters);

        if (keepAlive == null || keepAlive.isBlank()) {
            keepAlive = "1m";
        }

        if (providerType == ApiProviderType.OLLAMA) {
            Map<String, Object> options = new java.util.HashMap<>();
            options.put("temperature", temperature);
            options.putAll(parameters);
            OllamaSettings ollamaSettings = new OllamaSettings(
                    profile.base_url,
                    profile.api_key,
                    modelId,
                    keepAlive,
                    options
            );
            return fromOllama(ollamaSettings);
        }

        OpenAISettings openAISettings = new OpenAISettings(
                profile.base_url,
                profile.api_key,
                modelId,
                temperature,
                parameters,
                providerType
        );
        return fromOpenAI(openAISettings);
    }

    public static Map<String, Object> toParameterMap(List<CustomParameterEntry> customParameters) {
        Map<String, Object> parameterMap = new LinkedHashMap<>();
        if (customParameters == null) {
            return parameterMap;
        }

        for (CustomParameterEntry parameter : customParameters) {
            if (parameter == null || parameter.key == null) {
                continue;
            }

            String key = parameter.key.trim();
            if (key.isEmpty()) {
                continue;
            }

            if (parameter.is_object || (parameter.children != null && !parameter.children.isEmpty())) {
                parameterMap.put(key, toParameterMap(parameter.children));
            } else {
                parameterMap.put(key, convertParameterValue(parameter.value));
            }
        }
        return parameterMap;
    }

    private static Object convertParameterValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.equalsIgnoreCase("true")) return true;
        if (trimmedValue.equalsIgnoreCase("false")) return false;
        try {
            return Integer.parseInt(trimmedValue);
        } catch (NumberFormatException e1) {
            try {
                return Double.parseDouble(trimmedValue);
            } catch (NumberFormatException e2) {
                return trimmedValue;
            }
        }
    }

    public static record OpenAISettings(
            String baseUrl,
            String apiKey,
            String modelId,
            double temperature,
            Map<String, Object> customParameters,
            ApiProviderType providerType
    ) {

        public OpenAISettings {
            if (providerType == null || providerType == ApiProviderType.OLLAMA) {
                providerType = ApiProviderType.OPENAI_COMPAT;
            }
        }

        public OpenAISettings(String baseUrl, String apiKey, String modelId, double temperature, Map<String, Object> customParameters) {
            this(baseUrl, apiKey, modelId, temperature, customParameters, ApiProviderType.OPENAI_COMPAT);
        }

        public OpenAISettings(String baseUrl, String apiKey, String modelId, double temperature) {
            this(baseUrl, apiKey, modelId, temperature, null, ApiProviderType.OPENAI_COMPAT);
        }
    }

    public static record OllamaSettings(String baseUrl, String apiKey, String modelId, String keepAlive, Map<String, Object> options) {
        public OllamaSettings(String baseUrl, String modelId, String keepAlive, Map<String, Object> options) {
            this(baseUrl, "", modelId, keepAlive, options);
        }

        public OllamaSettings(String baseUrl, String modelId) {
            this(baseUrl, "", modelId, "5m", null);
        }
    }
}
