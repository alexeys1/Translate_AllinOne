package com.alexeys.translate_allinone.utils.config.ui;

public final class ConfigUiModalInteractionSupport {
    private ConfigUiModalInteractionSupport() {
    }

    public static ModalCloseAction outsideModalClickAction(ConfigUiModalState state) {
        if (state.updateNotice()) {
            return ModalCloseAction.CLOSE_UPDATE_NOTICE;
        }
        if (state.resetConfirm()) {
            return ModalCloseAction.CLOSE_RESET_CONFIRM;
        }
        if (state.unsavedChanges()) {
            return ModalCloseAction.CLOSE_UNSAVED_CHANGES;
        }
        if (state.dictionaryFiles()) {
            return ModalCloseAction.CLOSE_DICTIONARY_FILES;
        }
        if (state.promptEditorWarning()) {
            return ModalCloseAction.CLOSE_PROMPT_EDITOR_WARNING;
        }
        if (state.customParameters()) {
            return ModalCloseAction.CLOSE_CUSTOM_PARAMETERS;
        }
        if (state.modelTemperature()) {
            return ModalCloseAction.CLOSE_MODEL_TEMPERATURES;
        }
        if (state.addProvider()) {
            return ModalCloseAction.CLOSE_ADD_PROVIDER;
        }
        if (state.modelSettings()) {
            return ModalCloseAction.CLOSE_MODEL_SETTINGS;
        }
        return ModalCloseAction.NONE;
    }

    public static ModalCloseAction closeByPriority(ConfigUiModalState state) {
        if (state.updateNotice()) {
            return ModalCloseAction.CLOSE_UPDATE_NOTICE;
        }
        if (state.resetConfirm()) {
            return ModalCloseAction.CLOSE_RESET_CONFIRM;
        }
        if (state.unsavedChanges()) {
            return ModalCloseAction.CLOSE_UNSAVED_CHANGES;
        }
        if (state.dictionaryFiles()) {
            return ModalCloseAction.CLOSE_DICTIONARY_FILES;
        }
        if (state.promptEditorWarning()) {
            return ModalCloseAction.CLOSE_PROMPT_EDITOR_WARNING;
        }
        if (state.addProvider()) {
            return ModalCloseAction.CLOSE_ADD_PROVIDER;
        }
        if (state.modelTemperature()) {
            return ModalCloseAction.CLOSE_MODEL_TEMPERATURES;
        }
        if (state.customParameters()) {
            return ModalCloseAction.CLOSE_CUSTOM_PARAMETERS;
        }
        if (state.modelSettings()) {
            return ModalCloseAction.CLOSE_MODEL_SETTINGS;
        }
        return ModalCloseAction.NONE;
    }

    public enum ModalCloseAction {
        NONE,
        CLOSE_UPDATE_NOTICE,
        CLOSE_RESET_CONFIRM,
        CLOSE_UNSAVED_CHANGES,
        CLOSE_DICTIONARY_FILES,
        CLOSE_ADD_PROVIDER,
        CLOSE_MODEL_SETTINGS,
        CLOSE_CUSTOM_PARAMETERS,
        CLOSE_MODEL_TEMPERATURES,
        CLOSE_PROMPT_EDITOR_WARNING
    }
}
