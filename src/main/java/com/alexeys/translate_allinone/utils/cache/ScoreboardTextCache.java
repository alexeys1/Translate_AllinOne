package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class ScoreboardTextCache extends JsonStringTranslationCacheService {
    private static final String CACHE_FILE_NAME = "scoreboard_translate_cache.json";

    private ScoreboardTextCache() {
        this(resolveDefaultCachePath(), true);
    }

    ScoreboardTextCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ScoreboardTextCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                "scoreboard-cache-save",
                "scoreboard translation",
                CacheBackupManager::maybeBackup
        );
    }

    public static ScoreboardTextCache getInstance() {
        return Holder.INSTANCE;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final ScoreboardTextCache INSTANCE = new ScoreboardTextCache();
    }
}
