package com.cedarxuesong.translate_allinone.gui.configui.modals;

import com.cedarxuesong.translate_allinone.gui.configui.model.UiRect;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ModelTemperatureModalSupport {
    private ModelTemperatureModalSupport() {
    }

    public static Fields render(
            int screenWidth,
            int screenHeight,
            String chatTemperatureDraft,
            String itemTemperatureDraft,
            String scoreboardTemperatureDraft,
            String wynntilsTaskTrackerTemperatureDraft,
            String wynnNpcDialogueTemperatureDraft,
            ModelSettingsModalSupport.Translator translator,
            ModelSettingsModalSupport.FloatingActionBlockAdder floatingActionBlockAdder,
            ModelSettingsModalSupport.FloatingTextFieldAdder floatingTextFieldAdder,
            Consumer<String> onChatTemperatureChanged,
            Consumer<String> onItemTemperatureChanged,
            Consumer<String> onScoreboardTemperatureChanged,
            Consumer<String> onWynntilsTaskTrackerTemperatureChanged,
            Consumer<String> onWynnNpcDialogueTemperatureChanged,
            Runnable onCancel,
            Runnable onDone,
            ModelSettingsModalSupport.Style style
    ) {
        UiRect rect = ConfigUiModalSupport.modelTemperatureModalRect(screenWidth, screenHeight);
        int rowY = rect.y + 52;
        int labelWidth = 230;
        int fieldX = rect.x + 24 + labelWidth + 8;
        int fieldWidth = rect.width - 24 - 24 - labelWidth - 8;

        EditBox chatField = addTemperatureRow(
                rowY,
                rect.x + 24,
                labelWidth,
                fieldX,
                fieldWidth,
                translator.t("modal.model.temperature.chat"),
                chatTemperatureDraft,
                onChatTemperatureChanged,
                translator,
                floatingActionBlockAdder,
                floatingTextFieldAdder,
                style
        );
        rowY += 28;

        addTemperatureRow(
                rowY,
                rect.x + 24,
                labelWidth,
                fieldX,
                fieldWidth,
                translator.t("modal.model.temperature.item"),
                itemTemperatureDraft,
                onItemTemperatureChanged,
                translator,
                floatingActionBlockAdder,
                floatingTextFieldAdder,
                style
        );
        rowY += 28;

        addTemperatureRow(
                rowY,
                rect.x + 24,
                labelWidth,
                fieldX,
                fieldWidth,
                translator.t("modal.model.temperature.scoreboard"),
                scoreboardTemperatureDraft,
                onScoreboardTemperatureChanged,
                translator,
                floatingActionBlockAdder,
                floatingTextFieldAdder,
                style
        );
        rowY += 28;

        addTemperatureRow(
                rowY,
                rect.x + 24,
                labelWidth,
                fieldX,
                fieldWidth,
                translator.t("modal.model.temperature.wynntils_task_tracker"),
                wynntilsTaskTrackerTemperatureDraft,
                onWynntilsTaskTrackerTemperatureChanged,
                translator,
                floatingActionBlockAdder,
                floatingTextFieldAdder,
                style
        );
        rowY += 28;

        addTemperatureRow(
                rowY,
                rect.x + 24,
                labelWidth,
                fieldX,
                fieldWidth,
                translator.t("modal.model.temperature.wynn_npc_dialogue"),
                wynnNpcDialogueTemperatureDraft,
                onWynnNpcDialogueTemperatureChanged,
                translator,
                floatingActionBlockAdder,
                floatingTextFieldAdder,
                style
        );

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
                true,
                null
        );

        floatingActionBlockAdder.add(
                rightX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.done"),
                onDone,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true,
                null
        );

        return new Fields(chatField);
    }

    private static EditBox addTemperatureRow(
            int rowY,
            int labelX,
            int labelWidth,
            int fieldX,
            int fieldWidth,
            Component label,
            String value,
            Consumer<String> changed,
            ModelSettingsModalSupport.Translator translator,
            ModelSettingsModalSupport.FloatingActionBlockAdder floatingActionBlockAdder,
            ModelSettingsModalSupport.FloatingTextFieldAdder floatingTextFieldAdder,
            ModelSettingsModalSupport.Style style
    ) {
        floatingActionBlockAdder.add(
                labelX,
                rowY,
                labelWidth,
                20,
                () -> label,
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false,
                translator.t("desc.temperature")
        );
        return floatingTextFieldAdder.add(
                fieldX,
                rowY,
                fieldWidth,
                24,
                value,
                label,
                changed,
                true
        );
    }

    public record Fields(EditBox chatField) {
    }
}
