package com.alexeys.translate_allinone.utils.config.pojos;

import com.google.gson.annotations.SerializedName;

public class ChatTranslateConfig {
    public ChatOutputTranslateConfig output = new ChatOutputTranslateConfig();
    public ChatInputTranslateConfig input = new ChatInputTranslateConfig();

    public static class ChatOutputTranslateConfig {
        public static final String ORIGINAL_DISPLAY_OFF = "off";
        public static final String ORIGINAL_DISPLAY_HOVER = "hover";
        public static final String ORIGINAL_DISPLAY_SUBTITLE = "subtitle";
        public static final String DEFAULT_ORIGINAL_DISPLAY_MODE = ORIGINAL_DISPLAY_OFF;
        public static final int DEFAULT_ORIGINAL_SUBTITLE_MAX_LENGTH = 30;
        public static final int MAX_ORIGINAL_SUBTITLE_MAX_LENGTH = 100;
        public boolean enabled = false;
        public boolean auto_translate = false;
        public boolean skyblock_npc_auto_translate = false;
        public String target_language = "Chinese";
        public boolean streaming_response = false;
        public int max_concurrent_requests = 1;
        public String original_display_mode = DEFAULT_ORIGINAL_DISPLAY_MODE;
        public int original_subtitle_max_length = DEFAULT_ORIGINAL_SUBTITLE_MAX_LENGTH;
        @SerializedName(value = "debug", alternate = {"dev"})
        public DebugConfig debug = new DebugConfig();

        public static class DebugConfig {
            public boolean enabled = false;
            public boolean log_intercepted_message = false;
            public boolean log_llm_submission = false;
            public boolean log_reflow_mapping = false;
        }
    }

    public static class ChatInputTranslateConfig {
        public boolean enabled = false;
        public Boolean assistant_panel_enabled = false;
        public String target_language = "English";
        public boolean streaming_response = false;
        public InputBindingConfig keybinding = new InputBindingConfig();
        public ChatInputPanelState panel = new ChatInputPanelState();
    }

    public static class ChatInputPanelState {
        public boolean collapsed = false;
        public double x = -1;
        public double y = -1;
    }
}
