package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkyblockNpcTranslationCache extends JsonStringTranslationCacheService {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_FILE_NAME = "skyblock_npc_translate_cache.json";

    private SkyblockNpcTranslationCache() {
        this(resolveDefaultCachePath(), true);
    }

    SkyblockNpcTranslationCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    SkyblockNpcTranslationCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                "skyblock-npc-cache-save",
                "SkyBlock NPC translation",
                CacheBackupManager::maybeBackup
        );
    }

    public static SkyblockNpcTranslationCache getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    protected Map<String, String> entriesForSave() {
        return new LinkedHashMap<>(templateCache);
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_DIRECTORY_NAME)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final SkyblockNpcTranslationCache INSTANCE = new SkyblockNpcTranslationCache();
    }
}
