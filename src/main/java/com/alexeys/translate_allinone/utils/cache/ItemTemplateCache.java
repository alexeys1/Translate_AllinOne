package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ItemTemplateCache extends ItemTemplateCacheService {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_FILE_NAME = "item_translate_cache.json";

    private ItemTemplateCache() {
        this(resolveDefaultCachePath(), true);
    }

    ItemTemplateCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ItemTemplateCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                CacheBackupManager::maybeBackup,
                ItemTemplateCache::shouldLogCacheTimingDev
        );
    }

    public static ItemTemplateCache getInstance() {
        return Holder.INSTANCE;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_DIRECTORY_NAME)
                .resolve(CACHE_FILE_NAME);
    }

    private static boolean shouldLogCacheTimingDev() {
        return TooltipTextMatcherSupport.shouldLogItemCacheMigration(
                Translate_AllinOne.getConfig().itemTranslate
        );
    }

    private static final class Holder {
        private static final ItemTemplateCache INSTANCE = new ItemTemplateCache();
    }
}
