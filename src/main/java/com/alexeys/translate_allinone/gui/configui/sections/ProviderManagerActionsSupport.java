package com.alexeys.translate_allinone.gui.configui.sections;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.GroupBoxAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.TextFieldAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ToggleAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import java.util.Locale;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ProviderManagerActionsSupport {
    private ProviderManagerActionsSupport() {
    }

    public interface Runtime {
        void onSearchChanged(String value);

        void onProviderSelected(String providerId);

        void onOpenAddProvider();

        void onToggleProviderEnabled(ApiProviderProfile profile);

        void onDeleteProvider(ProviderManagerConfig providerManager, ApiProviderProfile profile);

        void onToggleApiKeyVisible();

        void onTestProviderConnection(ApiProviderProfile profile);

        void onSetDefaultModel(ApiProviderProfile profile, String modelId);

        void onOpenModelSettings(ApiProviderProfile profile, String modelId);

        void onRemoveModel(ApiProviderProfile profile, String modelId);

        void onAddModel(ApiProviderProfile profile);

        void onOpenPromptEditor(ApiProviderProfile profile);

        void onToggleTranslationEnabled(boolean enabled);

        void onRerunSetupWizard();

        void onEditorStateResolved(EditBox providerSearchField, String selectedProviderId);

        void onAddActiveProviderModals(ProviderManagerConfig providerManager);
    }

    public static int render(
            ProviderManagerConfig providerManager,
            int x,
            int y,
            int width,
            int viewportHeight,
            String providerSearchQuery,
            String selectedProviderId,
            int selectedProviderIndex,
            boolean providerApiKeyVisible,
            ProviderListSectionSupport.Style providerListStyle,
            ProviderDetailSectionSupport.Style providerDetailStyle,
            Translator translator,
            ToggleAdder toggleAdder,
            GroupBoxAdder groupBoxAdder,
            ActionBlockAdder actionBlockAdder,
            TextFieldAdder textFieldAdder,
            ProviderDetailSectionSupport.SecretTextFieldAdder secretTextFieldAdder,
            Runtime runtime
    ) {
        ProviderManagerSectionSupport.RenderResult result = ProviderManagerSectionSupport.render(
                providerManager,
                viewportHeight,
                x,
                y,
                width,
                providerSearchQuery,
                selectedProviderId,
                selectedProviderIndex,
                providerApiKeyVisible,
                providerListStyle,
                providerDetailStyle,
                translator,
                toggleAdder,
                groupBoxAdder,
                providerType -> providerTypeDisplayName(translator, providerType),
                actionBlockAdder,
                textFieldAdder,
                runtime::onSearchChanged,
                runtime::onProviderSelected,
                runtime::onOpenAddProvider,
                actionBlockAdder,
                textFieldAdder,
                secretTextFieldAdder,
                runtime::onToggleProviderEnabled,
                profile -> runtime.onDeleteProvider(providerManager, profile),
                runtime::onToggleApiKeyVisible,
                runtime::onTestProviderConnection,
                runtime::onSetDefaultModel,
                runtime::onOpenModelSettings,
                runtime::onRemoveModel,
                runtime::onAddModel,
                runtime::onOpenPromptEditor,
                runtime::onToggleTranslationEnabled,
                runtime::onRerunSetupWizard
        );

        runtime.onEditorStateResolved(result.providerSearchField(), result.selectedProviderId());
        runtime.onAddActiveProviderModals(providerManager);
        return result.contentBottomY();
    }

    public static Component providerTypeDisplayName(Translator translator, ApiProviderType providerType) {
        return translator.t("provider_type." + providerType.name().toLowerCase(Locale.ROOT));
    }

    public static boolean isProviderApiKeyVisible(ProviderManagerConfig providerManager) {
        return providerManager == null || providerManager.api_key_visible == null || providerManager.api_key_visible;
    }

    public static void storeProviderApiKeyVisible(boolean visible) {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config.providerManager == null) {
            config.providerManager = new ProviderManagerConfig();
        }
        config.providerManager.api_key_visible = visible;
    }
}
