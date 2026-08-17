package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;

public record UiTranslationResult(
        String modId,
        String screenId,
        UiTextRole role,
        Component sourceComponent,
        Component visibleComponent,
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
        sourceComponent = sourceComponent == null ? Component.empty() : sourceComponent;
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
            Component component,
            String targetLanguage,
            UiTranslationStatus status
    ) {
        Component safeComponent = component == null ? Component.empty() : component;
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
