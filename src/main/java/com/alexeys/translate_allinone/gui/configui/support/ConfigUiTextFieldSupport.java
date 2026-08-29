package com.alexeys.translate_allinone.gui.configui.support;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ConfigUiTextFieldSupport {
    private ConfigUiTextFieldSupport() {
    }

    public static TextFieldWidget create(
            TextRenderer textRenderer,
            Consumer<TextFieldWidget> registerField,
            List<TextFieldWidget> providerEditorFields,
            List<TextFieldWidget> floatingEditorFields,
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
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

        TextFieldWidget field = new TextFieldWidget(textRenderer, renderX, renderY, width, 20, Text.empty());
        field.setMaxLength(maxLength);
        field.setText(initialValue == null ? "" : initialValue);
        if (textPredicate != null) {
            field.setTextPredicate(textPredicate::test);
        }
        field.setChangedListener(changed);
        field.setPlaceholder(placeholder);
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

    public static TextFieldWidget createSecret(
            TextRenderer textRenderer,
            Consumer<TextFieldWidget> registerField,
            List<TextFieldWidget> providerEditorFields,
            List<TextFieldWidget> floatingEditorFields,
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
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

        SecretEditBox field = new SecretEditBox(textRenderer, renderX, renderY, width, 20, Text.empty(), masked, clearOnFirstEdit);
        field.setMaxLength(maxLength);
        field.setText(initialValue == null ? "" : initialValue);
        field.setChangedListener(changed);
        field.setPlaceholder(placeholder);
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

    private static final class SecretEditBox extends TextFieldWidget {
        private static final int HIDDEN_TEXT_COLOR = 0xFF555555;

        private Consumer<String> externalResponder;
        private boolean masked;
        private boolean clearOnFirstEdit;
        private boolean oldKeyCleared;

        SecretEditBox(TextRenderer textRenderer, int x, int y, int width, int height, Text message, boolean masked, boolean clearOnFirstEdit) {
            super(textRenderer, x, y, width, height, message);
            this.masked = masked;
            this.clearOnFirstEdit = clearOnFirstEdit;
            if (masked) {
                setEditableColor(HIDDEN_TEXT_COLOR);
            }
            addFormatter((value, cursor) ->
                    Text.literal(this.masked ? ProviderEditorSupport.maskApiKey(value) : value).asOrderedText());
        }

        @Override
        public void setChangedListener(Consumer<String> responder) {
            externalResponder = responder;
            super.setChangedListener(responder);
        }

        @Override
        public boolean charTyped(CharInput input) {
            if (masked && isActive() && isFocused() && input.isValidChar()) {
                beginEdit(true);
            }
            return super.charTyped(input);
        }

        @Override
        public boolean keyPressed(KeyInput key) {
            if (masked && isActive() && isFocused() && key.isPaste()) {
                String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clipboard == null || clipboard.isEmpty()) {
                    return true;
                }
                beginEdit(false);
            }
            return super.keyPressed(key);
        }

        @Override
        public void eraseCharacters(int amount) {
            if (masked && isActive() && isFocused()) {
                beginEdit(true);
            }
            super.eraseCharacters(amount);
        }

        @Override
        public void eraseWords(int amount) {
            if (masked && isActive() && isFocused()) {
                beginEdit(true);
            }
            super.eraseWords(amount);
        }

        private void beginEdit(boolean reveal) {
            if (reveal) {
                masked = false;
                setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR);
            }
            if (oldKeyCleared) {
                return;
            }
            oldKeyCleared = true;
            boolean hadFieldValue = !getText().isEmpty();
            if (hadFieldValue) {
                Consumer<String> responder = externalResponder;
                super.setChangedListener(null);
                super.setText("");
                super.setChangedListener(responder);
            }
            if (clearOnFirstEdit || hadFieldValue) {
                if (externalResponder != null) {
                    externalResponder.accept("");
                }
            }
        }
    }
}
