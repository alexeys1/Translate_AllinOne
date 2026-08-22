package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public final class WynntilsTaskTrackerTextCache extends TextTranslationCacheService {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_LABEL = "wynncraft_quest_translate_cache.json";

    private WynntilsTaskTrackerTextCache() {
        this(resolveDefaultCachePath(), true);
    }

    WynntilsTaskTrackerTextCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    WynntilsTaskTrackerTextCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                CACHE_LABEL,
                List.of(CACHE_LABEL),
                "translate_allinone-" + CACHE_LABEL + "-save",
                "Wynn quest",
                CacheBackupManager::maybeBackup
        );
    }

    public static WynntilsTaskTrackerTextCache getInstance() {
        return Holder.INSTANCE;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_DIRECTORY_NAME)
                .resolve(CACHE_LABEL);
    }

    private static final class Holder {
        private static final WynntilsTaskTrackerTextCache INSTANCE = new WynntilsTaskTrackerTextCache();
    }
}
