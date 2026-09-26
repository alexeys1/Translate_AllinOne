package com.alexeys.translate_allinone.gui.configui.modals;

import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ProviderTypeLabelProvider;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.TextFieldAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;

public final class AddProviderModalSupport {
    private AddProviderModalSupport() {
    }

    public static EditBox render(
            int screenWidth,
            int screenHeight,
            String addProviderNameDraft,
            ApiProviderType addProviderTypeDraft,
            boolean addProviderTypeDropdownOpen,
            Translator translator,
            ProviderTypeLabelProvider providerTypeLabelProvider,
            ActionBlockAdder ActionBlockAdder,
            TextFieldAdder TextFieldAdder,
            Consumer<String> onProviderNameChanged,
            Runnable onToggleTypeDropdown,
            Consumer<ApiProviderType> onSelectProviderType,
            Runnable onCancel,
            Runnable onConfirm,
            Style style
    ) {
        UiRect rect = ConfigUiModalSupport.addProviderModalRect(screenWidth, screenHeight);
        int rowY = rect.y + 48;
        int labelWidth = 110;
        int fieldX = rect.x + 24 + labelWidth + 8;
        int fieldWidth = rect.width - 24 - 24 - labelWidth - 8;

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.add_provider.name"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        );

        EditBox nameField = TextFieldAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                64,
                addProviderNameDraft,
                translator.t("placeholder.provider_name"),
                onProviderNameChanged,
                true
        );
        rowY += 24;

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.add_provider.type"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        );
        ActionBlockAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                20,
                () -> providerTypeLabelProvider.label(addProviderTypeDraft),
                onToggleTypeDropdown,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                false
        );

        int dropdownY = rowY + 24;
        if (addProviderTypeDropdownOpen) {
            for (ApiProviderType type : ApiProviderType.values()) {
                boolean selected = type == addProviderTypeDraft;
                ActionBlockAdder.add(
                        fieldX,
                        dropdownY,
                        fieldWidth,
                        20,
                        () -> providerTypeLabelProvider.label(type),
                        () -> onSelectProviderType.accept(type),
                        selected ? style.colorBlockAccent() : style.colorBlock(),
                        selected ? style.colorBlockAccentHover() : style.colorBlockHover(),
                        selected ? style.colorTextAccent() : style.colorText(),
                        false
                );
                dropdownY += 22;
            }
        }

        int buttonsY = rect.y + rect.height - 32;
        int half = (rect.width - 24 - 24 - 6) / 2;
        int leftX = rect.x + 24;
        int rightX = leftX + half + 6;

        ActionBlockAdder.add(
                leftX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.cancel"),
                onCancel,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        ActionBlockAdder.add(
                rightX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.confirm_add_provider"),
                onConfirm,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );

        return nameField;
    }

    public record Style(
            int colorBlockMuted,
            int colorBlock,
            int colorBlockHover,
            int colorBlockAccent,
            int colorBlockAccentHover,
            int colorText,
            int colorTextAccent
    ) {
    }
}
