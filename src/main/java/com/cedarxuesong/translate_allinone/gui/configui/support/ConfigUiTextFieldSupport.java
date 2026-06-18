package com.cedarxuesong.translate_allinone.gui.configui.support;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class ConfigUiTextFieldSupport {
    private ConfigUiTextFieldSupport() {
    }

    public static EditBox create(
            Font textRenderer,
            Consumer<EditBox> registerField,
            List<EditBox> providerEditorFields,
            List<EditBox> floatingEditorFields,
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable,
            boolean floating,
            boolean modalOpen
    ) {
        int renderX = x;
        int renderY = y;
        if (modalOpen && !floating) {
            renderX = -10000;
            renderY = -10000;
        }

        EditBox field = new EditBox(textRenderer, renderX, renderY, width, 20, Component.empty());
        field.setMaxLength(maxLength);
        field.setValue(initialValue == null ? "" : initialValue);
        field.setResponder(changed);
        field.setHint(placeholder);
        field.setEditable(editable);

        registerField.accept(field);
        if (floating) {
            floatingEditorFields.add(field);
        } else {
            providerEditorFields.add(field);
        }

        return field;
    }
}
