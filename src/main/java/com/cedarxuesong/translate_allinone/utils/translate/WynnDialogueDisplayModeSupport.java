package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;

final class WynnDialogueDisplayModeSupport {
    private WynnDialogueDisplayModeSupport() {
    }

    static boolean shouldShowOriginal(WynnCraftConfig.KeybindingMode mode, boolean isKeyPressed) {
        if (mode == null) {
            return false;
        }
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> !isKeyPressed;
            case HOLD_TO_SEE_ORIGINAL -> isKeyPressed;
            case DISABLED -> false;
        };
    }

    static boolean shouldRenderTranslatedText(
            WynnCraftConfig.NpcDialogueConfig config,
            WynnCraftConfig.KeybindingMode sharedMode,
            boolean isKeyPressed
    ) {
        return config != null
                && config.enabled
                && !shouldShowOriginal(sharedMode, isKeyPressed);
    }

    static boolean shouldRequestTranslations(
            WynnCraftConfig.NpcDialogueConfig config,
            WynnCraftConfig.KeybindingMode sharedMode,
            boolean isKeyPressed
    ) {
        if (config == null || !config.enabled) {
            return false;
        }
        return sharedMode != WynnCraftConfig.KeybindingMode.HOLD_TO_TRANSLATE || isKeyPressed;
    }
}
