package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class ChatOutputTranslationCache extends JsonStringTranslationCacheService {
    private static final String CACHE_DIRECTORY_NAME = "translate_cache";
    private static final String CACHE_FILE_NAME = "chat_output_translate_cache.json";

    private ChatOutputTranslationCache() {
        this(resolveDefaultCachePath(), true);
    }

    ChatOutputTranslationCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ChatOutputTranslationCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        super(
                cacheFilePath,
                passiveBackupEnabled,
                "chat-output-cache-save",
                "chat output translation",
                CacheBackupManager::maybeBackup
        );
    }

    public static ChatOutputTranslationCache getInstance() {
        return Holder.INSTANCE;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_DIRECTORY_NAME)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final ChatOutputTranslationCache INSTANCE = new ChatOutputTranslationCache();
    }
}
