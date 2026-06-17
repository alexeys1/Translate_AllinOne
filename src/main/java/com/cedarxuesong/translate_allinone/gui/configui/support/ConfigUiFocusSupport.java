package com.cedarxuesong.translate_allinone.gui.configui.support;

import com.cedarxuesong.translate_allinone.gui.configui.model.FocusTarget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

public final class ConfigUiFocusSupport {
    private ConfigUiFocusSupport() {
    }

    public static void applyPendingFocus(
            Screen screen,
            FocusTarget pendingFocusTarget,
            EditBox providerSearchField,
            EditBox addProviderNameField,
            EditBox modelSettingsField,
            EditBox customParameterNameField
    ) {
        EditBox focusField = switch (pendingFocusTarget) {
            case PROVIDER_SEARCH -> providerSearchField;
            case ADD_PROVIDER_NAME -> addProviderNameField;
            case MODEL_NAME -> modelSettingsField;
            case CUSTOM_PARAMETER_NAME -> customParameterNameField;
            default -> null;
        };

        if (focusField != null) {
            screen.setFocused(focusField);
            focusField.setFocused(true);
        }
    }
}
