package com.cedarxuesong.translate_allinone.utils.config.pojos;

public class OtherTranslationsConfig {
    public static final String DEFAULT_TARGET_LANGUAGE = "Chinese";

    public boolean enabled = false;
    public boolean enabled_translate_vanilla_advancements = false;
    public boolean component_json_v1_advancements = false;
    public int max_concurrent_requests = 2;
    public int max_batch_size = 10;
    public String target_language = DEFAULT_TARGET_LANGUAGE;
    public KeybindingConfig keybinding = new KeybindingConfig();

    public enum KeybindingMode {
        HOLD_TO_TRANSLATE,
        HOLD_TO_SEE_ORIGINAL,
        DISABLED
    }

    public static class KeybindingConfig {
        public KeybindingMode mode = KeybindingMode.DISABLED;
        public InputBindingConfig binding = new InputBindingConfig();
        public InputBindingConfig refreshBinding = new InputBindingConfig();
    }
}
