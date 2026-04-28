package com.cedarxuesong.translate_allinone.utils.config.pojos;

import com.google.gson.annotations.SerializedName;

public class WynnCraftConfig {
    public static final String DEFAULT_TARGET_LANGUAGE = "Chinese";

    public String target_language = DEFAULT_TARGET_LANGUAGE;
    public WynntilsTaskTrackerConfig wynntils_task_tracker = new WynntilsTaskTrackerConfig();
    public NpcDialogueConfig npc_dialogue = new NpcDialogueConfig();

    public static class NpcDialogueConfig {
        public boolean enabled = false;
        public boolean translate_npc_name = true;
        public boolean translate_options = false;
        public boolean log_dialogues_local_hits = false;
        public HudConfig hud = new HudConfig();
        public HudConfig options_hud = HudConfig.optionsDefaults();
        @SerializedName(value = "debug", alternate = {"dev"})
        public DebugConfig debug = new DebugConfig();
    }

    public static class HudConfig {
        public static final int MIN_SCALE_PERCENT = 50;
        public static final int MAX_SCALE_PERCENT = 200;
        public static final int DEFAULT_SCALE_PERCENT = 90;
        public static final int MIN_X_OFFSET = -300;
        public static final int MAX_X_OFFSET = 300;
        public static final int MIN_Y_OFFSET = -300;
        public static final int MAX_Y_OFFSET = 300;
        public static final int DEFAULT_X_OFFSET = 0;
        public static final int DEFAULT_Y_OFFSET = 0;
        public static final int DEFAULT_OPTIONS_X_OFFSET = 190;
        public static final int DEFAULT_OPTIONS_Y_OFFSET = -70;

        public int scale_percent = DEFAULT_SCALE_PERCENT;
        public int x_offset = DEFAULT_X_OFFSET;
        public int y_offset = DEFAULT_Y_OFFSET;

        public HudConfig() {
        }

        public HudConfig(int scalePercent, int xOffset, int yOffset) {
            this.scale_percent = scalePercent;
            this.x_offset = xOffset;
            this.y_offset = yOffset;
        }

        public static HudConfig optionsDefaults() {
            return new HudConfig(DEFAULT_SCALE_PERCENT, DEFAULT_OPTIONS_X_OFFSET, DEFAULT_OPTIONS_Y_OFFSET);
        }
    }

    public static class WynntilsTaskTrackerConfig {
        public boolean enabled = false;
        public boolean translate_title = true;
        public boolean translate_description = true;
        public KeybindingConfig keybinding = new KeybindingConfig();
        @SerializedName(value = "debug", alternate = {"dev"})
        public DebugConfig debug = new DebugConfig();
    }

    public static class DebugConfig {
        public boolean enabled = false;
        public boolean log_local_dictionary_hits = false;
        public boolean log_dialogues_local_hits = false;
        public boolean log_quests_local_hits = false;
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
