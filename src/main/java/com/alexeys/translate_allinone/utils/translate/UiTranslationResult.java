package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;

public record UiTranslationResult(
        String modId,
        String screenId,
        UiTextRole role,
        Text sourceComponent,
        Text visibleComponent,
        String sourceText,
        String visibleText,
        UiTranslationStatus status,
        String targetLanguage,
        boolean fromNativeResource
) {
    public UiTranslationResult {
        modId = modId == null ? "" : modId;
        screenId = screenId == null ? "" : screenId;
        role = role == null ? UiTextRole.OPTION : role;
        sourceComponent = sourceComponent == null ? Text.empty() : sourceComponent;
        visibleComponent = visibleComponent == null ? sourceComponent : visibleComponent;
        sourceText = sourceText == null ? "" : sourceText;
        visibleText = visibleText == null ? sourceText : visibleText;
        status = status == null ? UiTranslationStatus.INELIGIBLE : status;
        targetLanguage = targetLanguage == null ? "" : targetLanguage;
    }

    public static UiTranslationResult original(
            String modId,
            String screenId,
            UiTextRole role,
            Text component,
            String targetLanguage,
            UiTranslationStatus status
    ) {
        Text safeComponent = component == null ? Text.empty() : component;
        String source = safeComponent.getString();
        return new UiTranslationResult(
                modId,
                screenId,
                role,
                safeComponent,
                safeComponent,
                source,
                source,
                status,
                targetLanguage,
                false
        );
    }

    public boolean translated() {
        return status == UiTranslationStatus.NATIVE_RESOURCE
                || status == UiTranslationStatus.TRANSLATED;
    }
}

