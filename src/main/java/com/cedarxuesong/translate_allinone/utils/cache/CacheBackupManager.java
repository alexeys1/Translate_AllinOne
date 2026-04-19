package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class CacheBackupManager {

    private static final String BACKUP_FOLDER_NAME = "translate_cache_backup";
    private static final String BACKUP_MARKER_FILE_NAME = ".translate_allinone_cache_backup";
    private static final String BACKUP_MARKER_CONTENT = "translate_allinone:cache-backup\n";
    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final ZoneId BACKUP_ZONE = ZoneId.systemDefault();
    private static final Path CACHE_ROOT = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Translate_AllinOne.MOD_ID);
    private static final Path BACKUP_ROOT = CACHE_ROOT
            .resolve(BACKUP_FOLDER_NAME);

    private CacheBackupManager() {
    }

    public static Path getCacheDirectory() {
        return CACHE_ROOT;
    }

    public static Path getBackupRoot() {
        return BACKUP_ROOT;
    }

    public static List<BackupDirectorySummary> listManagedBackupDirectories() {
        if (!Files.isDirectory(BACKUP_ROOT)) {
            return List.of();
        }

        try (Stream<Path> directories = Files.list(BACKUP_ROOT)) {
            return directories
                    .filter(CacheBackupManager::isManagedBackupDirectory)
                    .map(CacheBackupManager::toBackupDirectorySummary)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupDirectorySummary::backupTime).reversed())
                    .toList();
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to list cache backup directories under {}", BACKUP_ROOT, e);
            return List.of();
        }
    }

    static void maybeBackup(Path cacheFilePath, String cacheTypeLabel) {
        if (!isBackupEnabled() || cacheFilePath == null || !Files.isRegularFile(cacheFilePath)) {
            return;
        }

        String fileName = cacheFilePath.getFileName().toString();
        Instant now = Instant.now();
        Duration backupInterval = getBackupInterval();

        try {
            List<BackupEntry> existingBackups = listBackups(fileName);
            BackupEntry latestBackup = existingBackups.stream()
                    .max(Comparator.comparing(BackupEntry::backupTime))
                    .orElse(null);

            if (latestBackup != null && Duration.between(latestBackup.backupTime(), now).compareTo(backupInterval) < 0) {
                return;
            }

            Path backupDirectory = BACKUP_ROOT.resolve(BACKUP_TIME_FORMATTER.format(LocalDateTime.ofInstant(now, BACKUP_ZONE)));
            ensureManagedBackupDirectory(backupDirectory);

            Path backupFilePath = backupDirectory.resolve(fileName);
            Files.copy(cacheFilePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);

            Translate_AllinOne.LOGGER.info(
                    "Created passive backup for {} cache at {}.",
                    cacheTypeLabel,
                    backupFilePath
            );

            cleanupBackups(fileName);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn(
                    "Failed to create passive backup for {} cache file {}.",
                    cacheTypeLabel,
                    cacheFilePath,
                    e
            );
        }
    }

    private static void cleanupBackups(String fileName) throws IOException {
        List<BackupEntry> backups = new ArrayList<>(listBackups(fileName));
        int maxBackupsPerFile = getMaxBackupsPerFile();
        if (backups.size() <= maxBackupsPerFile) {
            return;
        }

        backups.sort(Comparator.comparing(BackupEntry::backupTime));
        int overflowCount = backups.size() - maxBackupsPerFile;
        for (int index = 0; index < overflowCount; index++) {
            BackupEntry oldestBackup = backups.get(index);
            Files.deleteIfExists(oldestBackup.path());
            deleteDirectoryIfUnused(oldestBackup.directory());
        }
    }

    private static List<BackupEntry> listBackups(String fileName) throws IOException {
        if (!Files.isDirectory(BACKUP_ROOT)) {
            return List.of();
        }

        try (Stream<Path> directories = Files.list(BACKUP_ROOT)) {
            return directories
                    .filter(CacheBackupManager::isManagedBackupDirectory)
                    .map(directory -> toBackupEntry(directory, fileName))
                    .filter(entry -> entry != null)
                    .toList();
        }
    }

    private static BackupEntry toBackupEntry(Path directory, String fileName) {
        Path backupFilePath = directory.resolve(fileName);
        if (!Files.isRegularFile(backupFilePath)) {
            return null;
        }

        Instant backupTime = parseBackupTime(directory.getFileName().toString());
        if (backupTime == null) {
            return null;
        }

        return new BackupEntry(directory, backupFilePath, backupTime);
    }

    private static BackupDirectorySummary toBackupDirectorySummary(Path directory) {
        Instant backupTime = parseBackupTime(directory.getFileName().toString());
        if (backupTime == null) {
            return null;
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> backupFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !BACKUP_MARKER_FILE_NAME.equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            if (backupFiles.isEmpty()) {
                return null;
            }

            long totalBytes = 0L;
            List<String> fileNames = new ArrayList<>(backupFiles.size());
            for (Path backupFile : backupFiles) {
                fileNames.add(backupFile.getFileName().toString());
                totalBytes += Files.size(backupFile);
            }

            return new BackupDirectorySummary(directory.getFileName().toString(), backupTime, fileNames, totalBytes);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to inspect cache backup directory {}", directory, e);
            return null;
        }
    }

    private static Instant parseBackupTime(String directoryName) {
        try {
            return LocalDateTime.parse(directoryName, BACKUP_TIME_FORMATTER)
                    .atZone(BACKUP_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static void ensureManagedBackupDirectory(Path backupDirectory) throws IOException {
        Files.createDirectories(backupDirectory);
        Files.writeString(
                backupDirectory.resolve(BACKUP_MARKER_FILE_NAME),
                BACKUP_MARKER_CONTENT,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static boolean isManagedBackupDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }

        if (parseBackupTime(directory.getFileName().toString()) == null) {
            return false;
        }

        return Files.isRegularFile(directory.resolve(BACKUP_MARKER_FILE_NAME));
    }

    private static void deleteDirectoryIfUnused(Path directory) {
        try (Stream<Path> contents = Files.list(directory)) {
            List<Path> entries = contents.toList();
            if (entries.isEmpty()) {
                Files.deleteIfExists(directory);
                return;
            }

            if (entries.size() == 1 && BACKUP_MARKER_FILE_NAME.equals(entries.get(0).getFileName().toString())) {
                Files.deleteIfExists(entries.get(0));
                Files.deleteIfExists(directory);
            }
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to clean up backup directory {}", directory, e);
        }
    }

    private static Duration getBackupInterval() {
        return Duration.ofMinutes(getConfiguredBackupIntervalMinutes());
    }

    private static int getMaxBackupsPerFile() {
        return getConfiguredSettings().max_backup_count;
    }

    private static boolean isBackupEnabled() {
        return getConfiguredSettings().isEnabled();
    }

    private static int getConfiguredBackupIntervalMinutes() {
        return getConfiguredSettings().backup_interval_minutes;
    }

    private static CacheBackupConfig getConfiguredSettings() {
        CacheBackupConfig fallback = new CacheBackupConfig();
        try {
            if (Translate_AllinOne.getConfig().cacheBackup == null) {
                return fallback;
            }

            CacheBackupConfig configured = Translate_AllinOne.getConfig().cacheBackup;
            CacheBackupConfig safe = new CacheBackupConfig();
            safe.enabled = configured.isEnabled();
            safe.backup_interval_minutes = clamp(
                    configured.backup_interval_minutes,
                    CacheBackupConfig.MIN_BACKUP_INTERVAL_MINUTES,
                    CacheBackupConfig.MAX_BACKUP_INTERVAL_MINUTES,
                    CacheBackupConfig.DEFAULT_BACKUP_INTERVAL_MINUTES
            );
            safe.max_backup_count = clamp(
                    configured.max_backup_count,
                    CacheBackupConfig.MIN_MAX_BACKUP_COUNT,
                    CacheBackupConfig.MAX_MAX_BACKUP_COUNT,
                    CacheBackupConfig.DEFAULT_MAX_BACKUP_COUNT
            );
            return safe;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private record BackupEntry(Path directory, Path path, Instant backupTime) {
    }

    public record BackupDirectorySummary(
            String directoryName,
            Instant backupTime,
            List<String> fileNames,
            long totalBytes
    ) {
    }
}
