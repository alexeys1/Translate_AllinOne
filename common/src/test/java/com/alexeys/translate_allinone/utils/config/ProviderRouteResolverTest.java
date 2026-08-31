package com.alexeys.translate_allinone.utils.config;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRouteResolverTest {

    @Test
    void profileReportsDecryptFailure() {
        ApiProviderProfile profile = new ApiProviderProfile();
        assertFalse(profile.hasApiKeyDecryptFailure());
        profile.api_key_decrypt_failed = true;
        assertTrue(profile.hasApiKeyDecryptFailure());
    }

    @Test
    void routeHelperDetectsOriginalProfileFailure() {
        ModConfig config = configWithProvider(true);

        assertTrue(ProviderRouteResolver.hasApiKeyDecryptFailure(config, ProviderRouteResolver.Route.CHAT_OUTPUT));
        assertFalse(ProviderRouteResolver.hasApiKeyDecryptFailure(config, ProviderRouteResolver.Route.ITEM));
    }

    @Test
    void routeHelperReturnsFalseWhenNoFailure() {
        ModConfig config = configWithProvider(false);

        assertFalse(ProviderRouteResolver.hasApiKeyDecryptFailure(config, ProviderRouteResolver.Route.CHAT_OUTPUT));
    }

    @Test
    void routeHelperReturnsFalseForMissingConfigOrRoute() {
        ModConfig config = configWithProvider(true);

        assertFalse(ProviderRouteResolver.hasApiKeyDecryptFailure(null, ProviderRouteResolver.Route.CHAT_OUTPUT));
        assertFalse(ProviderRouteResolver.hasApiKeyDecryptFailure(config, null));
    }

    private static ModConfig configWithProvider(boolean decryptFailed) {
        ModConfig config = new ModConfig();
        ApiProviderProfile provider = new ApiProviderProfile();
        provider.id = "provider";
        provider.name = "Provider";
        provider.type = ApiProviderType.OPENAI_COMPAT;
        provider.model_id = "model";
        provider.model_ids = new ArrayList<>(List.of("model"));
        provider.api_key_decrypt_failed = decryptFailed;
        config.providerManager.providers = new ArrayList<>(List.of(provider));
        config.providerManager.routes.chat_output = "provider::model";
        return config;
    }
}
