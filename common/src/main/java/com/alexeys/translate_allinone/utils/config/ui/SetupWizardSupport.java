package com.alexeys.translate_allinone.utils.config.ui;

import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;

import java.util.List;

public final class SetupWizardSupport {
    private SetupWizardSupport() {
    }

    public enum Module {
        CHAT_OUTPUT(true),
        CHAT_INPUT(true),
        ITEM(true),
        SCOREBOARD(false),
        OTHER_SCREEN_UI(false),
        OTHER_ADVANCEMENTS(false),
        OTHER_SIGNS(false),
        OTHER_ENTITY_TEXT(false),
        OTHER_WRITTEN_BOOKS(false),
        WYNN_NPC_DIALOGUE(false),
        WYNNTILS_TASK_TRACKER(false);

        private final boolean recommended;

        Module(boolean recommended) {
            this.recommended = recommended;
        }

        public boolean recommended() {
            return recommended;
        }
    }

    public record SummaryChecks(boolean noModuleEnabled, boolean apiKeyMissing, boolean connectionUnverified) {
    }

    public static boolean shouldShowSetupWizard(boolean setupCompleted, boolean forced, ModConfig config) {
        if (forced) {
            return true;
        }
        if (setupCompleted) {
            return false;
        }
        return !hasUsableConfiguration(config);
    }

    public static boolean hasUsableConfiguration(ModConfig config) {
        if (config == null || config.providerManager == null) {
            return false;
        }

        config.providerManager.ensureDefaults();
        if (!hasUsableProvider(config.providerManager.providers)) {
            return false;
        }

        for (ProviderRouteResolver.Route route : ProviderRouteResolver.Route.values()) {
            if (ProviderRouteResolver.resolve(config, route) != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean isModuleEnabled(ModConfig config, Module module) {
        return switch (module) {
            case CHAT_OUTPUT -> config.chatTranslate.output.enabled;
            case CHAT_INPUT -> config.chatTranslate.input.enabled;
            case ITEM -> config.itemTranslate.enabled;
            case SCOREBOARD -> config.scoreboardTranslate.enabled;
            case OTHER_SCREEN_UI -> config.otherTranslations.enabled_screen_translation;
            case OTHER_ADVANCEMENTS -> config.otherTranslations.enabled_translate_vanilla_advancements;
            case OTHER_SIGNS -> config.otherTranslations.enabled_translate_signs;
            case OTHER_ENTITY_TEXT -> config.otherTranslations.enabled_translate_entity_text;
            case OTHER_WRITTEN_BOOKS -> config.otherTranslations.enabled_translate_written_books;
            case WYNN_NPC_DIALOGUE -> config.wynnCraft.npc_dialogue.enabled;
            case WYNNTILS_TASK_TRACKER -> config.wynnCraft.wynntils_task_tracker.enabled;
        };
    }

    public static void setModuleEnabled(ModConfig config, Module module, boolean enabled) {
        switch (module) {
            case CHAT_OUTPUT -> config.chatTranslate.output.enabled = enabled;
            case CHAT_INPUT -> config.chatTranslate.input.enabled = enabled;
            case ITEM -> config.itemTranslate.enabled = enabled;
            case SCOREBOARD -> config.scoreboardTranslate.enabled = enabled;
            case OTHER_SCREEN_UI -> config.otherTranslations.enabled_screen_translation = enabled;
            case OTHER_ADVANCEMENTS -> config.otherTranslations.enabled_translate_vanilla_advancements = enabled;
            case OTHER_SIGNS -> config.otherTranslations.enabled_translate_signs = enabled;
            case OTHER_ENTITY_TEXT -> config.otherTranslations.enabled_translate_entity_text = enabled;
            case OTHER_WRITTEN_BOOKS -> config.otherTranslations.enabled_translate_written_books = enabled;
            case WYNN_NPC_DIALOGUE -> config.wynnCraft.npc_dialogue.enabled = enabled;
            case WYNNTILS_TASK_TRACKER -> config.wynnCraft.wynntils_task_tracker.enabled = enabled;
        }
    }

    public static void applyRecommendedSelection(ModConfig config) {
        for (Module module : Module.values()) {
            setModuleEnabled(config, module, module.recommended());
        }
    }

    public static boolean hasAnyModuleEnabled(ModConfig config) {
        for (Module module : Module.values()) {
            if (isModuleEnabled(config, module)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasApiKeyMissing(ApiProviderProfile profile) {
        if (profile == null) {
            return true;
        }
        if (profile.type == ApiProviderType.OLLAMA) {
            return false;
        }
        return isBlank(profile.api_key) && !hasEntries(profile.api_key_entries);
    }

    public static SummaryChecks evaluateSummary(ModConfig config, ApiProviderProfile profile, boolean connectionVerified) {
        return new SummaryChecks(!hasAnyModuleEnabled(config), hasApiKeyMissing(profile), !connectionVerified);
    }

    private static boolean hasUsableProvider(List<ApiProviderProfile> providers) {
        if (providers == null) {
            return false;
        }
        for (ApiProviderProfile profile : providers) {
            if (isProviderUsable(profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProviderUsable(ApiProviderProfile profile) {
        if (profile == null || !profile.enabled || isBlank(profile.model_id) || profile.api_key_decrypt_failed) {
            return false;
        }
        if (profile.type == ApiProviderType.OLLAMA) {
            return true;
        }
        return !isBlank(profile.api_key) || hasEntries(profile.api_key_entries);
    }

    private static boolean hasEntries(List<String> entries) {
        if (entries == null) {
            return false;
        }
        for (String entry : entries) {
            if (!isBlank(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
