package com.cedarxuesong.translate_allinone.utils.config.pojos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class DictionaryConfig {
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_TEXT_DEBUG_ENABLED = false;

    public Boolean enabled = DEFAULT_ENABLED;
    public Boolean text_debug_enabled = DEFAULT_TEXT_DEBUG_ENABLED;
    public Boolean item_skill_enabled = DEFAULT_ENABLED;
    public Boolean wynncraft_dialogue_enabled = DEFAULT_ENABLED;
    public Boolean wynncraft_quest_enabled = DEFAULT_ENABLED;
    public List<String> item_skill_dictionary_files = new ArrayList<>();
    public String wynncraft_dialogue_dictionary_file = "";
    public String wynncraft_quest_dictionary_file = "";

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isTextDebugEnabled() {
        return Boolean.TRUE.equals(text_debug_enabled);
    }

    public void normalize() {
        if (enabled == null) {
            enabled = DEFAULT_ENABLED;
        }
        if (text_debug_enabled == null) {
            text_debug_enabled = DEFAULT_TEXT_DEBUG_ENABLED;
        }
        if (item_skill_enabled == null) {
            item_skill_enabled = DEFAULT_ENABLED;
        }
        if (wynncraft_dialogue_enabled == null) {
            wynncraft_dialogue_enabled = DEFAULT_ENABLED;
        }
        if (wynncraft_quest_enabled == null) {
            wynncraft_quest_enabled = DEFAULT_ENABLED;
        }
        item_skill_dictionary_files = sanitizeFileNames(item_skill_dictionary_files);
        wynncraft_dialogue_dictionary_file = sanitizeFileName(wynncraft_dialogue_dictionary_file);
        wynncraft_quest_dictionary_file = sanitizeFileName(wynncraft_quest_dictionary_file);
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim();
    }

    private static List<String> sanitizeFileNames(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String fileName : fileNames) {
            String sanitized = sanitizeFileName(fileName);
            if (!sanitized.isBlank()) {
                normalized.add(sanitized);
            }
        }
        return new ArrayList<>(normalized);
    }
}
