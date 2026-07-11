package com.cedarxuesong.translate_allinone.gui.configui.interaction;

public final class ConfigUiModalInteractionSupport {
    private ConfigUiModalInteractionSupport() {
    }

    public static ModalCloseAction outsideModalClickAction(
            boolean addProviderModalOpen,
            boolean modelSettingsModalOpen,
            boolean customParametersModalOpen,
            boolean modelTemperatureModalOpen,
            boolean dictionaryFilesModalOpen,
            boolean resetConfirmModalOpen,
            boolean updateNoticeModalOpen,
            boolean unsavedChangesConfirmModalOpen,
            boolean promptEditorWarningOpen
    ) {
        if (updateNoticeModalOpen) {
            return ModalCloseAction.CLOSE_UPDATE_NOTICE;
        }
        if (resetConfirmModalOpen) {
            return ModalCloseAction.CLOSE_RESET_CONFIRM;
        }
        if (unsavedChangesConfirmModalOpen) {
            return ModalCloseAction.CLOSE_UNSAVED_CHANGES;
        }
        if (dictionaryFilesModalOpen) {
            return ModalCloseAction.CLOSE_DICTIONARY_FILES;
        }
        if (promptEditorWarningOpen) {
            return ModalCloseAction.CLOSE_PROMPT_EDITOR_WARNING;
        }
        if (customParametersModalOpen) {
            return ModalCloseAction.CLOSE_CUSTOM_PARAMETERS;
        }
        if (modelTemperatureModalOpen) {
            return ModalCloseAction.CLOSE_MODEL_TEMPERATURES;
        }
        if (addProviderModalOpen) {
            return ModalCloseAction.CLOSE_ADD_PROVIDER;
        }
        if (modelSettingsModalOpen) {
            return ModalCloseAction.CLOSE_MODEL_SETTINGS;
        }
        return ModalCloseAction.NONE;
    }

    public static ModalCloseAction closeByPriority(
            boolean addProviderModalOpen,
            boolean modelSettingsModalOpen,
            boolean customParametersModalOpen,
            boolean modelTemperatureModalOpen,
            boolean dictionaryFilesModalOpen,
            boolean resetConfirmModalOpen,
            boolean updateNoticeModalOpen,
            boolean unsavedChangesConfirmModalOpen,
            boolean promptEditorWarningOpen
    ) {
        if (updateNoticeModalOpen) {
            return ModalCloseAction.CLOSE_UPDATE_NOTICE;
        }
        if (resetConfirmModalOpen) {
            return ModalCloseAction.CLOSE_RESET_CONFIRM;
        }
        if (unsavedChangesConfirmModalOpen) {
            return ModalCloseAction.CLOSE_UNSAVED_CHANGES;
        }
        if (dictionaryFilesModalOpen) {
            return ModalCloseAction.CLOSE_DICTIONARY_FILES;
        }
        if (promptEditorWarningOpen) {
            return ModalCloseAction.CLOSE_PROMPT_EDITOR_WARNING;
        }
        if (addProviderModalOpen) {
            return ModalCloseAction.CLOSE_ADD_PROVIDER;
        }
        if (modelTemperatureModalOpen) {
            return ModalCloseAction.CLOSE_MODEL_TEMPERATURES;
        }
        if (customParametersModalOpen) {
            return ModalCloseAction.CLOSE_CUSTOM_PARAMETERS;
        }
        if (modelSettingsModalOpen) {
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
