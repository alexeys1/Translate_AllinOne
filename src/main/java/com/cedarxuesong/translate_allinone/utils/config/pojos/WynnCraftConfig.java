package com.cedarxuesong.translate_allinone.utils.config.pojos;

import com.google.gson.annotations.SerializedName;

public class WynnCraftConfig {
    public boolean wynn_item_compatibility = false;
    public WynntilsTaskTrackerConfig wynntils_task_tracker = new WynntilsTaskTrackerConfig();

    public static class WynntilsTaskTrackerConfig {
        public boolean enabled = false;
        public boolean translate_title = true;
        public boolean translate_description = true;
        public String target_language = "Chinese";
        public KeybindingConfig keybinding = new KeybindingConfig();
        @SerializedName(value = "debug", alternate = {"dev"})
        public DebugConfig debug = new DebugConfig();
    }

    public static class DebugConfig {
        public boolean enabled = false;
    }

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
