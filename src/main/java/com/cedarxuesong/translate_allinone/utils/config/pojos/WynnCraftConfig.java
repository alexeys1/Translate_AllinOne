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
        public boolean log_dialogues_local_hits = false;
        public HudConfig hud = new HudConfig();
        @SerializedName(value = "debug", alternate = {"dev"})
        public DebugConfig debug = new DebugConfig();
    }

    public static class HudConfig {
        public static final int MIN_SCALE_PERCENT = 50;
        public static final int MAX_SCALE_PERCENT = 200;
        public static final int DEFAULT_SCALE_PERCENT = 100;
        public static final int MIN_X_OFFSET = -300;
        public static final int MAX_X_OFFSET = 300;
        public static final int DEFAULT_X_OFFSET = 0;
        public static final int MIN_Y_OFFSET = -300;
        public static final int MAX_Y_OFFSET = 300;
        public static final int DEFAULT_Y_OFFSET = 0;

        public int scale_percent = DEFAULT_SCALE_PERCENT;
        public int x_offset = DEFAULT_X_OFFSET;
        public int y_offset = DEFAULT_Y_OFFSET;
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
        // Legacy compatibility only. Dialogue local-hit logging now uses npc_dialogue.log_dialogues_local_hits.
        public boolean log_local_dictionary_hits = false;
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
