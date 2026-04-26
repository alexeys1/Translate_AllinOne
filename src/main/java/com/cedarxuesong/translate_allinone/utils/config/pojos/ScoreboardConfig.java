package com.cedarxuesong.translate_allinone.utils.config.pojos;

import com.google.gson.annotations.SerializedName;

public class ScoreboardConfig {
    public boolean enabled = false;
    public boolean enabled_translate_prefix_and_suffix_name = true;
    public boolean enabled_translate_player_name = false;
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
    }

    public static class DebugConfig {
        public boolean enabled = false;
        public boolean log_batch_lifecycle = false;
        public boolean log_response_validation = false;
        public boolean log_retry_flow = false;
        public boolean log_batch_timing = false;
    }
}
