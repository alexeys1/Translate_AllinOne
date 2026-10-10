package com.alexeys.translate_allinone.gui.configui.modals;

import com.alexeys.translate_allinone.gui.configui.controls.CheckboxBlock;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.TextFieldAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ModelSettingsModalSupport {
    private ModelSettingsModalSupport() {
    }

    public static EditBox render(
            ApiProviderProfile profile,
            int screenWidth,
            int screenHeight,
            String modelSettingsDraft,
            String modelSettingsKeepAliveDraft,
            String modelSettingsSystemPromptSuffixDraft,
            int customParameterCount,
            boolean modelSettingsSupportsSystemDraft,
            boolean modelSettingsSetDefault,
            Translator translator,
            ActionBlockAdder ActionBlockAdder,
            FloatingCheckboxAdder floatingCheckboxAdder,
            TextFieldAdder TextFieldAdder,
            Consumer<String> onModelIdChanged,
            Consumer<String> onKeepAliveChanged,
            Consumer<String> onSystemPromptSuffixChanged,
            Runnable onEditTemperatures,
            Runnable onEditCustomParameters,
            Consumer<Boolean> onSupportsSystemChanged,
            Consumer<Boolean> onSetDefaultChanged,
            Runnable onCancel,
            Runnable onSave,
            Style style,
            CheckboxBlock.Style checkboxStyle
    ) {
        UiRect rect = ConfigUiModalSupport.modelSettingsModalRect(screenWidth, screenHeight);
        int rowY = rect.y + 48;
        int labelWidth = 130;
        int fieldX = rect.x + 24 + labelWidth + 8;
        int fieldWidth = rect.width - 24 - 24 - labelWidth - 8;

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.model.name"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        , null);

        EditBox modelNameField = TextFieldAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                128,
                modelSettingsDraft,
                translator.t("placeholder.model_id"),
                onModelIdChanged,
                true
        );
        rowY += 24;

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.model.temperature"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false,
                translator.t("desc.temperature")
        );
        ActionBlockAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                20,
                () -> translator.t("button.edit_temperature_settings"),
                onEditTemperatures,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                false,
                translator.t("desc.temperature")
        );
        rowY += 24;

        if (profile.type == ApiProviderType.OLLAMA) {
            ActionBlockAdder.add(
                    rect.x + 24,
                    rowY,
                    labelWidth,
                    20,
                    () -> translator.t("modal.model.keep_alive"),
                    () -> {
                    },
                    style.colorBlockMuted(),
                    style.colorBlockMuted(),
                    style.colorText(),
                    false
            , null);
            TextFieldAdder.add(
                    fieldX,
                    rowY,
                    fieldWidth,
                    64,
                    modelSettingsKeepAliveDraft,
                    translator.t("modal.model.keep_alive"),
                    onKeepAliveChanged,
                    true
            );
            rowY += 24;
        }

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.model.system_prompt_suffix"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        , null);
        TextFieldAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                256,
                modelSettingsSystemPromptSuffixDraft,
                translator.t("modal.model.system_prompt_suffix"),
                onSystemPromptSuffixChanged,
                true
        );
        rowY += 24;

        ActionBlockAdder.add(
                rect.x + 24,
                rowY,
                labelWidth,
                20,
                () -> translator.t("modal.model.custom_parameters"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        , null);
        ActionBlockAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                20,
                () -> translator.t("button.edit_custom_parameters", customParameterCount),
                onEditCustomParameters,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                false
        , null);
        rowY += 24;

        floatingCheckboxAdder.add(
                rect.x + 24,
                rowY,
                rect.width - 48,
                20,
                () -> translator.t("modal.model.supports_system_msg"),
                () -> modelSettingsSupportsSystemDraft,
                onSupportsSystemChanged,
                checkboxStyle,
                translator.t("desc.supports_system_msg")
        );
        rowY += 24;

        floatingCheckboxAdder.add(
                rect.x + 24,
                rowY,
                rect.width - 48,
                20,
                () -> translator.t("modal.model.set_default"),
                () -> modelSettingsSetDefault,
                onSetDefaultChanged,
                checkboxStyle
        , null);

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
        , null);

        ActionBlockAdder.add(
                rightX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.model_save"),
                onSave,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        , null);

        return modelNameField;
    }

    @FunctionalInterface
    public interface FloatingCheckboxAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Component> labelSupplier,
                BooleanSupplier checked,
                Consumer<Boolean> changed,
                CheckboxBlock.Style style,
                Component tooltip
        );
    }

    public record Style(
            int colorBlockMuted,
            int colorBlock,
            int colorBlockHover,
            int colorBlockSelected,
            int colorBlockSelectedHover,
            int colorBlockAccent,
            int colorBlockAccentHover,
            int colorText
    ) {
    }
}
