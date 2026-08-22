package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.pojos.CacheBackupConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class CacheBackupManager {
    private static final Path CACHE_ROOT = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Translate_AllinOne.MOD_ID);
    private static final CacheBackupService SERVICE = new CacheBackupService(
            CACHE_ROOT,
            CacheBackupManager::getConfiguredSettings
    );

    private CacheBackupManager() {
    }

    public static Path getCacheDirectory() {
        return SERVICE.getCacheDirectory();
    }

    public static Path getComponentCacheDirectory() {
        return SERVICE.getComponentCacheDirectory();
    }

    public static Path getBackupRoot() {
        return SERVICE.getBackupRoot();
    }

    public static List<BackupDirectorySummary> listManagedBackupDirectories() {
        return SERVICE.listManagedBackupDirectories().stream()
                .map(summary -> new BackupDirectorySummary(
                        summary.directoryName(),
                        summary.backupTime(),
                        summary.fileNames(),
                        summary.totalBytes()
                ))
                .toList();
    }

    public static void maybeBackup(Path cacheFilePath, String cacheTypeLabel) {
        SERVICE.maybeBackup(cacheFilePath, cacheTypeLabel);
    }

    public static void enforceBackupLimit() {
        SERVICE.enforceBackupLimit();
    }

    public static List<Path> findVerifiedComponentCacheBackups(Path componentCacheFile) {
        return SERVICE.findVerifiedComponentCacheBackups(componentCacheFile);
    }

    private static CacheBackupConfig getConfiguredSettings() {
        return Translate_AllinOne.getConfig().cacheBackup;
    }

    public record BackupDirectorySummary(
            String directoryName,
            Instant backupTime,
            List<String> fileNames,
            long totalBytes
    ) {
    }
}
