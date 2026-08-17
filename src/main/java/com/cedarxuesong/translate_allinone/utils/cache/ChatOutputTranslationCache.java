package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatOutputTranslationCache extends AbstractTranslateCache<List<String>> {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_FILE_NAME = "chat_output_translate_cache.json";

    private ChatOutputTranslationCache() {
        this(resolveDefaultCachePath(), true);
    }

    ChatOutputTranslationCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ChatOutputTranslationCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(cacheFilePath, passiveBackupEnabled, "chat-output-cache-save");
    }

    public static ChatOutputTranslationCache getInstance() {
        return Holder.INSTANCE;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID)
                .resolve(CACHE_DIRECTORY_NAME)
                .resolve(CACHE_FILE_NAME);
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
                GSON.toJson(templateCache, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, cacheFilePath);
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(cacheFilePath, "chat output translation");
            }
            persistence.finishSave();
        } catch (IOException error) {
            Translate_AllinOne.LOGGER.error("Failed to save chat output translation cache to {}", cacheFilePath, error);
        }
    }

    private static final class Holder {
        private static final ChatOutputTranslationCache INSTANCE = new ChatOutputTranslationCache();
    }
}
