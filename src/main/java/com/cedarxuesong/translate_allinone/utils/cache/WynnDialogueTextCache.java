package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public final class WynnDialogueTextCache extends TextTranslationCacheService {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_LABEL = "wynncraft_dialogue_translate_cache.json";
    private static final String LEGACY_CACHE_LABEL = "wynn_dialogue_translate_cache.json";

    private WynnDialogueTextCache() {
        this(resolveDefaultCachePath(), true);
    }

    WynnDialogueTextCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    WynnDialogueTextCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                CACHE_LABEL,
                List.of(CACHE_LABEL, LEGACY_CACHE_LABEL),
                "translate_allinone-wynn-dialogue-cache-save",
                "Wynn dialogue",
                CacheBackupManager::maybeBackup
        );
    }

    public static WynnDialogueTextCache getInstance() {
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
        private static final WynnDialogueTextCache INSTANCE = new WynnDialogueTextCache();
    }
}
