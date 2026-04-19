package com.cedarxuesong.translate_allinone.utils.config.pojos;

import com.google.gson.annotations.SerializedName;

public class ItemTranslateConfig {
    public boolean enabled = false;
    public boolean enabled_translate_item_custom_name = false;
    public boolean enabled_translate_item_lore = false;
    public transient boolean wynn_item_compatibility = false;
    public int max_concurrent_requests = 2;
    public int requests_per_minute = 60;
    public int max_batch_size = 10;
    public String target_language = "Chinese";
    public KeybindingConfig keybinding = new KeybindingConfig();
    @SerializedName(value = "debug", alternate = {"dev"})
    public DebugConfig debug = new DebugConfig();

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

    public static class DebugConfig {
        public boolean enabled = false;
        public boolean log_tooltip_filter_result = false;
        public boolean log_tooltip_node_summary = false;
        public boolean log_tooltip_paragraph_result = false;
        public boolean log_tooltip_style_map = false;
        public boolean log_tooltip_timing = false;
        public boolean log_item_batch_timing = false;
        public boolean log_cache_migration = false;
    }
}
