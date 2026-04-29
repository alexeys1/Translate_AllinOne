package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DictionaryConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DictionaryFileSelectionSupport {
    public enum Slot {
        ITEM_SKILL,
        WYNNCRAFT_DIALOGUE,
        WYNNCRAFT_QUEST
    }

    private DictionaryFileSelectionSupport() {
    }

    public static List<String> listAvailableFiles() {
        Path directory = WynncraftDictionaryInstaller.resolveConfigDictionaryDirectory();
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(DictionaryFileSelectionSupport::isJsonFile)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to list dictionary files from {}", directory, e);
            return List.of();
        }
    }

    public static String getSelectedFile(DictionaryConfig config, Slot slot) {
        if (config == null || slot == null) {
            return "";
        }
        config.normalize();
        return switch (slot) {
            case ITEM_SKILL -> config.item_skill_dictionary_files.isEmpty() ? "" : config.item_skill_dictionary_files.get(0);
            case WYNNCRAFT_DIALOGUE -> config.wynncraft_dialogue_dictionary_file;
            case WYNNCRAFT_QUEST -> config.wynncraft_quest_dictionary_file;
        };
    }

    public static void setSelectedFile(DictionaryConfig config, Slot slot, String fileName) {
        if (config == null || slot == null) {
            return;
        }
        String normalized = sanitizeFileName(fileName);
        switch (slot) {
            case ITEM_SKILL -> config.item_skill_dictionary_files = normalized.isBlank()
                    ? new ArrayList<>()
                    : new ArrayList<>(List.of(normalized));
            case WYNNCRAFT_DIALOGUE -> config.wynncraft_dialogue_dictionary_file = normalized;
            case WYNNCRAFT_QUEST -> config.wynncraft_quest_dictionary_file = normalized;
        }
        config.normalize();
    }

    public static List<String> getSelectedFiles(DictionaryConfig config, Slot slot) {
        if (config == null || slot == null) {
            return List.of();
        }
        config.normalize();
        return switch (slot) {
            case ITEM_SKILL -> List.copyOf(config.item_skill_dictionary_files);
            case WYNNCRAFT_DIALOGUE, WYNNCRAFT_QUEST -> {
                String selected = getSelectedFile(config, slot);
                yield selected.isBlank() ? List.of() : List.of(selected);
            }
        };
    }

    public static void setSelectedFiles(DictionaryConfig config, Slot slot, List<String> fileNames) {
        if (config == null || slot == null) {
            return;
        }
        switch (slot) {
            case ITEM_SKILL -> config.item_skill_dictionary_files = new ArrayList<>(fileNames == null ? List.of() : fileNames);
            case WYNNCRAFT_DIALOGUE, WYNNCRAFT_QUEST -> setSelectedFile(
                    config,
                    slot,
                    fileNames == null || fileNames.isEmpty() ? "" : fileNames.get(0)
            );
        }
        config.normalize();
    }

    public static void toggleSelectedFile(DictionaryConfig config, Slot slot, String fileName) {
        if (config == null || slot == null || fileName == null || fileName.isBlank()) {
            return;
        }
        if (!isMultiSelect(slot)) {
            setSelectedFile(config, slot, fileName);
            return;
        }
        List<String> selectedFiles = new ArrayList<>(getSelectedFiles(config, slot));
        String normalized = sanitizeFileName(fileName);
        if (selectedFiles.contains(normalized)) {
            selectedFiles.remove(normalized);
        } else {
            selectedFiles.add(normalized);
            selectedFiles.sort(String.CASE_INSENSITIVE_ORDER);
        }
        setSelectedFiles(config, slot, selectedFiles);
    }

    public static boolean isMultiSelect(Slot slot) {
        return slot == Slot.ITEM_SKILL;
    }

    public static boolean isSlotEnabled(DictionaryConfig config, Slot slot) {
        if (config == null || slot == null) {
            return true;
        }
        config.normalize();
        return switch (slot) {
            case ITEM_SKILL -> config.item_skill_enabled == null || config.item_skill_enabled;
            case WYNNCRAFT_DIALOGUE -> config.wynncraft_dialogue_enabled == null || config.wynncraft_dialogue_enabled;
            case WYNNCRAFT_QUEST -> config.wynncraft_quest_enabled == null || config.wynncraft_quest_enabled;
        };
    }

    public static void setSlotEnabled(DictionaryConfig config, Slot slot, boolean enabled) {
        if (config == null || slot == null) {
            return;
        }
        switch (slot) {
            case ITEM_SKILL -> config.item_skill_enabled = enabled;
            case WYNNCRAFT_DIALOGUE -> config.wynncraft_dialogue_enabled = enabled;
            case WYNNCRAFT_QUEST -> config.wynncraft_quest_enabled = enabled;
        }
        config.normalize();
    }

    public static Path resolveItemDictionaryPath() {
        DictionaryConfig config = currentDictionaryConfig();
        if (!isSlotEnabled(config, Slot.ITEM_SKILL)) {
            return null;
        }
        List<Path> paths = resolveItemSkillDictionaryPaths(config);
        if (paths.isEmpty()) {
            return null;
        }
        return paths.size() >= 2 ? paths.get(0) : null;
    }

    public static Path resolveSkillDictionaryPath() {
        DictionaryConfig config = currentDictionaryConfig();
        if (!isSlotEnabled(config, Slot.ITEM_SKILL)) {
            return null;
        }
        List<Path> paths = resolveItemSkillDictionaryPaths(config);
        return paths.size() >= 2 ? paths.get(1) : null;
    }

    public static List<Path> resolveItemSkillDictionaryPaths() {
        DictionaryConfig config = currentDictionaryConfig();
        if (!isSlotEnabled(config, Slot.ITEM_SKILL)) {
            return List.of();
        }
        return resolveItemSkillDictionaryPaths(config);
    }

    static List<Path> resolveItemSkillDictionaryPathsByLookupPriority() {
        List<Path> paths = resolveItemSkillDictionaryPaths();
        if (paths.size() <= 1) {
            return paths;
        }

        List<Path> prioritizedPaths = new ArrayList<>(paths);
        prioritizedPaths.sort((left, right) -> Integer.compare(
                itemSkillDictionaryPathPriority(left),
                itemSkillDictionaryPathPriority(right)
        ));
        return List.copyOf(prioritizedPaths);
    }

    public static Path resolveDialogueDictionaryPath() {
        DictionaryConfig config = currentDictionaryConfig();
        String selectedFile = getSelectedFile(config, Slot.WYNNCRAFT_DIALOGUE);
        if (!isSlotEnabled(config, Slot.WYNNCRAFT_DIALOGUE)) {
            return null;
        }
        if (selectedFile.isBlank()) {
            return null;
        }
        return WynncraftDictionaryInstaller.resolveConfigDictionaryFile(selectedFile);
    }

    public static Path resolveQuestDictionaryPath() {
        DictionaryConfig config = currentDictionaryConfig();
        String selectedFile = getSelectedFile(config, Slot.WYNNCRAFT_QUEST);
        if (!isSlotEnabled(config, Slot.WYNNCRAFT_QUEST)) {
            return null;
        }
        if (selectedFile.isBlank()) {
            return null;
        }
        return WynncraftDictionaryInstaller.resolveConfigDictionaryFile(selectedFile);
    }

    public static boolean hasCustomSelection(DictionaryConfig config, Slot slot) {
        return !getSelectedFile(config, slot).isBlank();
    }

    public static String describeEffectiveSelection(DictionaryConfig config, Slot slot) {
        if (slot == null) {
            return "";
        }
        return switch (slot) {
            case ITEM_SKILL -> joinPathNames(resolveItemSkillDictionaryPaths(config));
            case WYNNCRAFT_DIALOGUE -> fileName(resolveDialogueDictionaryPath(config));
            case WYNNCRAFT_QUEST -> fileName(resolveQuestDictionaryPath(config));
        };
    }

    private static DictionaryConfig currentDictionaryConfig() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            if (config == null || config.dictionary == null) {
                return null;
            }
            config.dictionary.normalize();
            return config.dictionary;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim();
    }

    private static boolean isJsonFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static List<Path> resolveItemSkillDictionaryPaths(DictionaryConfig config) {
        List<String> selectedFiles = getSelectedFiles(config, Slot.ITEM_SKILL);
        if (selectedFiles.isEmpty()) {
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        for (String selectedFile : selectedFiles) {
            Path path = WynncraftDictionaryInstaller.resolveConfigDictionaryFile(selectedFile);
            if (path != null) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    private static Path resolveDialogueDictionaryPath(DictionaryConfig config) {
        String selectedFile = getSelectedFile(config, Slot.WYNNCRAFT_DIALOGUE);
        if (selectedFile.isBlank()) {
            return null;
        }
        return WynncraftDictionaryInstaller.resolveConfigDictionaryFile(selectedFile);
    }

    private static Path resolveQuestDictionaryPath(DictionaryConfig config) {
        String selectedFile = getSelectedFile(config, Slot.WYNNCRAFT_QUEST);
        if (selectedFile.isBlank()) {
            return null;
        }
        return WynncraftDictionaryInstaller.resolveConfigDictionaryFile(selectedFile);
    }

    private static String joinPathNames(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (Path path : paths) {
            String fileName = fileName(path);
            if (!fileName.isBlank() && !names.contains(fileName)) {
                names.add(fileName);
            }
        }
        return String.join(" / ", names);
    }

    private static String fileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        return path.getFileName().toString();
    }

    private static int itemSkillDictionaryPathPriority(Path path) {
        String fileName = fileName(path).toLowerCase(Locale.ROOT);
        if (fileName.contains("skill")) {
            return 0;
        }
        if (fileName.contains("item")) {
            return 1;
        }
        return 2;
    }
}
