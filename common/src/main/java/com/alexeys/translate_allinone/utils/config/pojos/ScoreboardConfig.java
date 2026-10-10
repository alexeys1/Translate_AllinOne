package com.alexeys.translate_allinone.utils.config.pojos;

public class ScoreboardConfig {
    public boolean enabled = false;
    public boolean enabled_translate_prefix_and_suffix_name = true;
    public boolean enabled_translate_player_name = false;
    public ExternalCustomScoreboardMode external_custom_scoreboard_mode =
            ExternalCustomScoreboardMode.DISABLED;
    public int max_concurrent_requests = 2;
    public int requests_per_minute = 60;
    public int max_batch_size = 10;
    public String target_language = "Chinese";
    public KeybindingConfig keybinding = new KeybindingConfig();

    public enum KeybindingMode {
        HOLD_TO_TRANSLATE,
        HOLD_TO_SEE_ORIGINAL,
        DISABLED
    }

    public enum ExternalCustomScoreboardMode {
        DISABLED,
        AUTO,
        FORCE
    }

    public static class KeybindingConfig {
        public KeybindingMode mode = KeybindingMode.HOLD_TO_TRANSLATE;
        public InputBindingConfig binding = new InputBindingConfig();
        public InputBindingConfig refreshBinding = new InputBindingConfig();
    }
}
