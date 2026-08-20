package com.cedarxuesong.translate_allinone.utils.cache;

import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class JsonStringTranslationCacheService extends AbstractTranslateCache<List<String>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone");
    private final String cacheLabel;
    private final BiConsumer<Path, String> backupAction;

    protected JsonStringTranslationCacheService(
            Path cacheFilePath,
            boolean passiveBackupEnabled,
            String saveThreadName,
            String cacheLabel,
            BiConsumer<Path, String> backupAction
    ) {
        super(cacheFilePath, passiveBackupEnabled, saveThreadName);
        this.cacheLabel = cacheLabel;
        this.backupAction = backupAction;
    }

    @Override
    protected Map<String, String> loadEntries() throws IOException {
        if (!Files.exists(cacheFilePath)) {
            return Map.of();
        }

        try (Reader reader = Files.newBufferedReader(cacheFilePath, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
            }.getType());
            if (loaded == null || loaded.isEmpty()) {
                return Map.of();
            }

            Map<String, String> filtered = new LinkedHashMap<>();
            loaded.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    filtered.put(key, value);
                }
            });
            return filtered;
        }
    }

    @Override
    public synchronized void updateTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        super.updateTranslations(translations);
        persistence.markDirty();
        scheduleSave();
    }

    @Override
    public synchronized void save() {
        if (!persistence.beginSave()) {
            return;
        }

        try {
            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(entriesForSave(), writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, cacheFilePath);
            if (passiveBackupEnabled) {
                backupAction.accept(cacheFilePath, cacheLabel);
            }
            persistence.finishSave();
        } catch (IOException error) {
            LOGGER.error("Failed to save {} cache to {}", cacheLabel, cacheFilePath, error);
        }
    }

    protected Map<String, String> entriesForSave() {
        return templateCache;
    }
}
