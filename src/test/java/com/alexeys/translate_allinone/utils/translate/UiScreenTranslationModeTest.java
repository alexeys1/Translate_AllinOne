package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScreenTranslationModeTest {
    @Test
    void followsSharedHotkeyModes() {
        OtherTranslationsConfig config = new OtherTranslationsConfig();

        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.HOLD_TO_TRANSLATE;
        assertFalse(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, true));

        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.HOLD_TO_SEE_ORIGINAL;
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
        assertFalse(ComponentRenderTranslationSupport.shouldRenderTranslated(config, true));

        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.DISABLED;
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, true));
    }
}
