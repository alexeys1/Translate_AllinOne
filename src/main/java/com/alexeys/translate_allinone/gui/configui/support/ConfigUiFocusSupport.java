package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.gui.configui.model.FocusTarget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public final class ConfigUiFocusSupport {
    private ConfigUiFocusSupport() {
    }

    public static void applyPendingFocus(
            Screen screen,
            FocusTarget pendingFocusTarget,
            TextFieldWidget providerSearchField,
            TextFieldWidget addProviderNameField,
            TextFieldWidget modelSettingsField,
            TextFieldWidget customParameterNameField,
            TextFieldWidget modelTemperatureChatField
    ) {
        TextFieldWidget focusField = switch (pendingFocusTarget) {
            case PROVIDER_SEARCH -> providerSearchField;
            case ADD_PROVIDER_NAME -> addProviderNameField;
            case MODEL_NAME -> modelSettingsField;
            case CUSTOM_PARAMETER_NAME -> customParameterNameField;
            case MODEL_TEMPERATURE_CHAT -> modelTemperatureChatField;
            default -> null;
        };

        if (focusField != null) {
            screen.setFocused(focusField);
            focusField.setFocused(true);
        }
    }
}
