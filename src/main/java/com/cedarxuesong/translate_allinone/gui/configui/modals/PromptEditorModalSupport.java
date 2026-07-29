package com.cedarxuesong.translate_allinone.gui.configui.modals;

import com.cedarxuesong.translate_allinone.gui.configui.model.UiRect;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ProviderProfileSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class PromptEditorModalSupport {
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 4;
    private static final String ROUTE_ITEM = "item";
    private static final String ROUTE_SCOREBOARD = "scoreboard";
    private static final String ROUTE_CHAT_OUTPUT = "chat_output";
    private static final String ROUTE_CHAT_INPUT_TRANSLATE = "chat_input_translate";
    private static final String ROUTE_WYNN_NPC_DIALOGUE = "wynn_npc_dialogue";
    private static final String ROUTE_WYNNTILS_TASK_TRACKER = "wynntils_task_tracker";
    private static final String ROUTE_SIGN_BOOK = "sign_book";
    private static final String ROUTE_ENTITY_TEXT = "entity_text";

    private PromptEditorModalSupport() {
    }

    public static Map<String, String> defaultOverrides() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(ROUTE_ITEM, "");
        defaults.put(ROUTE_SCOREBOARD, "");
        defaults.put(ROUTE_CHAT_OUTPUT, "");
        defaults.put(ROUTE_CHAT_INPUT_TRANSLATE, "");
        defaults.put(ROUTE_WYNN_NPC_DIALOGUE, "");
        defaults.put(ROUTE_WYNNTILS_TASK_TRACKER, "");
        defaults.put(ROUTE_SIGN_BOOK, "");
        defaults.put(ROUTE_ENTITY_TEXT, "");
        return defaults;
    }

    public static Fields render(
            int screenWidth,
            int screenHeight,
            Map<String, String> draftOverrides,
            Translator translator,
            FloatingActionBlockAdder floatingActionBlockAdder,
            FloatingTextFieldAdder floatingTextFieldAdder,
            Runnable onCancel,
            Runnable onSave,
            Runnable onReset,
            Style style
    ) {
        UiRect rect = ConfigUiModalSupport.promptEditorModalRect(screenWidth, screenHeight);
        int contentTop = rect.y + 48;
        int contentBottom = rect.bottom() - 36;
        int leftX = rect.x + 20;
        int rightX = rect.x + rect.width / 2 + 10;
        int labelWidth = Math.max(140, rect.width / 2 - 40);
        int fieldWidth = rect.right() - rightX - 20;

        Map<String, Component> routeLabels = routeLabels(translator);
        String[] routeKeys = {
                ROUTE_ITEM, ROUTE_SCOREBOARD, ROUTE_CHAT_OUTPUT, ROUTE_CHAT_INPUT_TRANSLATE,
                ROUTE_WYNN_NPC_DIALOGUE, ROUTE_WYNNTILS_TASK_TRACKER, ROUTE_SIGN_BOOK, ROUTE_ENTITY_TEXT
        };
        Map<String, EditBox> fields = new LinkedHashMap<>();
        int rowY = contentTop;

        for (String routeKey : routeKeys) {
            Component label = routeLabels.getOrDefault(routeKey, Component.literal(routeKey));
            floatingActionBlockAdder.add(
                    leftX,
                    rowY,
                    labelWidth,
                    20,
                    () -> label,
                    () -> {
                    },
                    style.colorBlockMuted(),
                    style.colorBlockMuted(),
                    style.colorText(),
                    false
            );

            String value = ProviderProfileSupport.sanitizeText(draftOverrides.getOrDefault(routeKey, ""));
            EditBox field = floatingTextFieldAdder.add(
                    rightX,
                    rowY,
                    fieldWidth,
                    1024,
                    value,
                    label,
                    newValue -> draftOverrides.put(routeKey, ProviderProfileSupport.sanitizeText(newValue)),
                    true
            );
            fields.put(routeKey, field);
            rowY += ROW_HEIGHT + GAP;
        }

        int buttonsY = rect.y + rect.height - 32;
        int buttonWidth = (rect.width - 60) / 3;

        floatingActionBlockAdder.add(
                rect.x + 20,
                buttonsY,
                buttonWidth,
                20,
                () -> translator.t("button.prompt_reset"),
                onReset,
                style.colorBlockDanger(),
                style.colorBlockDangerHover(),
                style.colorText(),
                true
        );

        floatingActionBlockAdder.add(
                rect.x + 20 + buttonWidth + 6,
                buttonsY,
                buttonWidth,
                20,
                () -> translator.t("button.cancel"),
                onCancel,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        floatingActionBlockAdder.add(
                rect.x + 20 + (buttonWidth + 6) * 2,
                buttonsY,
                buttonWidth,
                20,
                () -> translator.t("button.prompt_save"),
                onSave,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );

        return new Fields(fields);
    }

    private static Map<String, Component> routeLabels(Translator translator) {
        Map<String, Component> labels = new LinkedHashMap<>();
        labels.put(ROUTE_ITEM, translator.t("prompt_editor.route.item"));
        labels.put(ROUTE_SCOREBOARD, translator.t("prompt_editor.route.scoreboard"));
        labels.put(ROUTE_CHAT_OUTPUT, translator.t("prompt_editor.route.chat_output"));
        labels.put(ROUTE_CHAT_INPUT_TRANSLATE, translator.t("prompt_editor.route.chat_input_translate"));
        labels.put(ROUTE_WYNN_NPC_DIALOGUE, translator.t("prompt_editor.route.wynn_npc_dialogue"));
        labels.put(ROUTE_WYNNTILS_TASK_TRACKER, translator.t("prompt_editor.route.wynntils_task_tracker"));
        labels.put(ROUTE_SIGN_BOOK, translator.t("prompt_editor.route.sign_book"));
        labels.put(ROUTE_ENTITY_TEXT, translator.t("prompt_editor.route.entity_text"));
        return labels;
    }

    @FunctionalInterface
    public interface Translator {
        Component t(String key, Object... args);
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
            int colorBlockDanger,
            int colorBlockDangerHover,
            int colorText
    ) {
    }

    public record Fields(Map<String, EditBox> fields) {
    }
}
