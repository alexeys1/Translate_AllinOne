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

public final class SkyblockNpcTranslationCache extends AbstractTranslateCache<List<String>> {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_FILE_NAME = "skyblock_npc_translate_cache.json";

    private SkyblockNpcTranslationCache() {
        this(resolveDefaultCachePath(), true);
    }

    SkyblockNpcTranslationCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    SkyblockNpcTranslationCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(cacheFilePath, passiveBackupEnabled, "skyblock-npc-cache-save");
    }

    public static SkyblockNpcTranslationCache getInstance() {
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
            Map<String, String> snapshot = new LinkedHashMap<>(templateCache);
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, cacheFilePath);
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(cacheFilePath, "SkyBlock NPC translation");
            }
            persistence.finishSave();
        } catch (IOException error) {
            Translate_AllinOne.LOGGER.error("Failed to save SkyBlock NPC translation cache to {}", cacheFilePath, error);
        }
    }

    private static final class Holder {
        private static final SkyblockNpcTranslationCache INSTANCE = new SkyblockNpcTranslationCache();
    }
}
