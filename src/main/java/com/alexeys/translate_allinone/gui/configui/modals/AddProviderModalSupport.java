package com.alexeys.translate_allinone.gui.configui.modals;

import com.alexeys.translate_allinone.gui.configui.model.UiRect;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

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
            FloatingActionBlockAdder floatingActionBlockAdder,
            FloatingTextFieldAdder floatingTextFieldAdder,
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

        floatingActionBlockAdder.add(
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

        EditBox nameField = floatingTextFieldAdder.add(
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

        floatingActionBlockAdder.add(
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
        floatingActionBlockAdder.add(
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
                floatingActionBlockAdder.add(
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

        floatingActionBlockAdder.add(
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

        floatingActionBlockAdder.add(
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

    @FunctionalInterface
    public interface Translator {
        Component t(String key, Object... args);
    }

    @FunctionalInterface
    public interface ProviderTypeLabelProvider {
        Component label(ApiProviderType type);
    }

    @FunctionalInterface
    public interface FloatingTextFieldAdder {
        EditBox add(
                int x,
                int y,
                int width,
                int maxLength,
                String initialValue,
                Component placeholder,
                Consumer<String> changed,
                boolean editable
        );
    }

    @FunctionalInterface
    public interface FloatingActionBlockAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Component> labelSupplier,
                Runnable action,
                int color,
                int hoverColor,
                int textColor,
                boolean centered
        );
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
