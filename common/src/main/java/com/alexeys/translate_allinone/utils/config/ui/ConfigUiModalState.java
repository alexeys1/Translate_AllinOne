package com.alexeys.translate_allinone.utils.config.ui;

public record ConfigUiModalState(
        boolean addProvider,
        boolean modelSettings,
        boolean customParameters,
        boolean modelTemperature,
        boolean dictionaryFiles,
        boolean resetConfirm,
        boolean updateNotice,
        boolean unsavedChanges,
        boolean promptEditorWarning,
        boolean setupWizard
) {
    public boolean isAnyOpen() {
        return addProvider
                || modelSettings
                || customParameters
                || modelTemperature
                || dictionaryFiles
                || resetConfirm
                || updateNotice
                || unsavedChanges
                || promptEditorWarning
                || setupWizard;
    }
}
