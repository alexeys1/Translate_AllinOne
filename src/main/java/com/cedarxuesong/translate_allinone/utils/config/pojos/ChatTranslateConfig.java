package com.cedarxuesong.translate_allinone.utils.config.pojos;

public class ChatTranslateConfig {
    public ChatOutputTranslateConfig output = new ChatOutputTranslateConfig();
    public ChatInputTranslateConfig input = new ChatInputTranslateConfig();

    public static class ChatOutputTranslateConfig {
        public boolean enabled = false;
        public boolean auto_translate = false;
        public int interaction_offset_amount = 0;
        public String target_language = "Chinese";
        public boolean streaming_response = false;
        public int max_concurrent_requests = 1;
    }

    public static class ChatInputTranslateConfig {
        public boolean enabled = false;
        public Boolean assistant_panel_enabled = true;
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
