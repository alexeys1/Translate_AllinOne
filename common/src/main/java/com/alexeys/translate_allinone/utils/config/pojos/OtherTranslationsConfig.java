package com.alexeys.translate_allinone.utils.config.pojos;

public class OtherTranslationsConfig {
    public static final String DEFAULT_TARGET_LANGUAGE = "Chinese";

    public boolean enabled = false;
    public boolean enabled_screen_translation = false;
    public boolean enabled_translate_vanilla_advancements = false;
    public boolean enabled_translate_signs = false;
    public boolean continuous_sign_translation = false;
    public int sign_translation_radius = 8;
    public boolean enabled_translate_entity_text = false;
    public int entity_translation_radius = 8;
    public boolean translate_entity_name_tags = true;
    public boolean translate_text_display_entities = false;
    public boolean translate_item_entity_hover_labels = false;
    public boolean enabled_translate_written_books = false;
    public int book_max_page_characters = 8192;
    public boolean book_prefetch_adjacent_pages = false;
    public int max_concurrent_requests = 2;
    public int max_batch_size = 10;
    public String target_language = DEFAULT_TARGET_LANGUAGE;
    public KeybindingConfig keybinding = new KeybindingConfig();
    public DebugConfig debug = new DebugConfig();

    public enum KeybindingMode {
        HOLD_TO_TRANSLATE,
        HOLD_TO_SEE_ORIGINAL,
        DISABLED
    }

    public static class KeybindingConfig {
        public KeybindingMode mode = KeybindingMode.HOLD_TO_TRANSLATE;
        public InputBindingConfig binding = new InputBindingConfig();
        public InputBindingConfig refreshBinding = new InputBindingConfig();
    }

    public static class DebugConfig {
        public boolean enabled = false;
        public boolean log_component_entity_identity = false;
    }
}
