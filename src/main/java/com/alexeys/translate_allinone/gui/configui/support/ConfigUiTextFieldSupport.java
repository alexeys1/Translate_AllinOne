package com.alexeys.translate_allinone.gui.configui.support;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
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

        if (floating || !modalOpen) {
            registerField.accept(field);
        }
        if (floating) {
            floatingEditorFields.add(field);
        } else {
            providerEditorFields.add(field);
        }

        return field;
    }

    public static EditBox createSecret(
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
            boolean masked,
            boolean clearOnFirstEdit,
            boolean floating,
            boolean modalOpen
    ) {
        int renderX = x;
        int renderY = y;
        if (modalOpen && !floating) {
            renderX = -10000;
            renderY = -10000;
        }

        SecretEditBox field = new SecretEditBox(textRenderer, renderX, renderY, width, 20, Component.empty(), masked, clearOnFirstEdit);
        field.setMaxLength(maxLength);
        field.setValue(initialValue == null ? "" : initialValue);
        field.setResponder(changed);
        field.setHint(placeholder);
        field.setEditable(true);

        if (floating || !modalOpen) {
            registerField.accept(field);
        }
        if (floating) {
            floatingEditorFields.add(field);
        } else {
            providerEditorFields.add(field);
        }

        return field;
    }

    private static final class SecretEditBox extends EditBox {
        private static final int HIDDEN_TEXT_COLOR = 0xFF555555;

        private Consumer<String> externalResponder;
        private boolean masked;
        private boolean clearOnFirstEdit;
        private boolean oldKeyCleared;

        SecretEditBox(Font textRenderer, int x, int y, int width, int height, Component message, boolean masked, boolean clearOnFirstEdit) {
            super(textRenderer, x, y, width, height, message);
            this.masked = masked;
            this.clearOnFirstEdit = clearOnFirstEdit;
            if (masked) {
                setTextColor(HIDDEN_TEXT_COLOR);
            }
            addFormatter((value, cursor) ->
                    Component.literal(this.masked ? ProviderEditorSupport.maskApiKey(value) : value).getVisualOrderText());
        }

        @Override
        public void setResponder(Consumer<String> responder) {
            externalResponder = responder;
            super.setResponder(responder);
        }

        @Override
        public boolean charTyped(CharacterEvent input) {
            if (masked && isActive() && isFocused() && input.isAllowedChatCharacter()) {
                beginEdit(true);
            }
            return super.charTyped(input);
        }

        @Override
        public boolean keyPressed(KeyEvent key) {
            if (masked && isActive() && isFocused() && key.isPaste()) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard == null || clipboard.isEmpty()) {
                    return true;
                }
                beginEdit(false);
            }
            return super.keyPressed(key);
        }

        @Override
        public void deleteChars(int amount) {
            if (masked && isActive() && isFocused()) {
                beginEdit(true);
            }
            super.deleteChars(amount);
        }

        @Override
        public void deleteWords(int amount) {
            if (masked && isActive() && isFocused()) {
                beginEdit(true);
            }
            super.deleteWords(amount);
        }

        private void beginEdit(boolean reveal) {
            if (reveal) {
                masked = false;
                setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            }
            if (oldKeyCleared) {
                return;
            }
            oldKeyCleared = true;
            boolean hadFieldValue = !getValue().isEmpty();
            if (hadFieldValue) {
                Consumer<String> responder = externalResponder;
                super.setResponder(null);
                super.setValue("");
                super.setResponder(responder);
            }
            if (clearOnFirstEdit || hadFieldValue) {
                if (externalResponder != null) {
                    externalResponder.accept("");
                }
            }
        }
    }
}
