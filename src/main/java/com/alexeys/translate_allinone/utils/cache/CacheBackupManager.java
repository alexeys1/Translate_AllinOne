package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class CacheBackupManager {

    private static final String BACKUP_FOLDER_NAME = "translate_cache_backup";
    private static final String BACKUP_MARKER_FILE_NAME = ".translate_allinone_cache_backup";
    private static final String BACKUP_MANIFEST_FILE_NAME = "manifest.json";
    private static final String BACKUP_MARKER_CONTENT = "translate_allinone:cache-backup\n";
    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final ZoneId BACKUP_ZONE = ZoneId.systemDefault();
    private static final Path CACHE_ROOT = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Translate_AllinOne.MOD_ID);
    private static final Path BACKUP_ROOT = CACHE_ROOT
            .resolve(BACKUP_FOLDER_NAME);
    private static final Path COMPONENT_CACHE_ROOT = CACHE_ROOT.resolve("translate_cache");
    private static final Object BACKUP_LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CacheBackupManager() {
    }

    public static Path getCacheDirectory() {
        return CACHE_ROOT;
    }

    public static Path getComponentCacheDirectory() {
        return COMPONENT_CACHE_ROOT;
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

    public static void maybeBackup(Path cacheFilePath, String cacheTypeLabel) {
        if (!isBackupEnabled() || cacheFilePath == null || !Files.isRegularFile(cacheFilePath)) {
            return;
        }

        synchronized (BACKUP_LOCK) {
            cleanupBackupDirectories();
            Instant now = Instant.now();
            Duration backupInterval = getBackupInterval();
            try {
                List<Path> cacheFiles = listCurrentCacheFiles();
                if (cacheFiles.isEmpty()) {
                    return;
                }

                BackupDirectorySummary latestBackup = latestBackupDirectory();
                if (latestBackup != null && Duration.between(latestBackup.backupTime(), now).compareTo(backupInterval) < 0) {
                    return;
                }

                Path backupDirectory = BACKUP_ROOT.resolve(BACKUP_TIME_FORMATTER.format(LocalDateTime.ofInstant(now, BACKUP_ZONE)));
                ensureManagedBackupDirectory(backupDirectory);
                JsonArray manifestFiles = new JsonArray();
                for (Path sourceFile : cacheFiles) {
                    Path relativePath = CACHE_ROOT.relativize(sourceFile);
                    if (relativePath.getNameCount() == 1) {
                        relativePath = Path.of("old_translate_cache").resolve(relativePath);
                    }
                    Path backupFilePath = backupDirectory.resolve(relativePath);
                    Files.createDirectories(backupFilePath.getParent());
                    Files.copy(sourceFile, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
                    JsonObject manifestEntry = new JsonObject();
                    manifestEntry.addProperty("relative_path", relativePath.toString().replace('\\', '/'));
                    manifestEntry.addProperty("sha256", sha256(sourceFile));
                    manifestEntry.addProperty("size", Files.size(sourceFile));
                    manifestFiles.add(manifestEntry);
                }
                JsonObject manifest = new JsonObject();
                manifest.addProperty("created_at", now.toString());
                manifest.add("files", manifestFiles);
                try (var writer = Files.newBufferedWriter(backupDirectory.resolve(BACKUP_MANIFEST_FILE_NAME), StandardCharsets.UTF_8)) {
                    GSON.toJson(manifest, writer);
                }
                markManagedBackupDirectory(backupDirectory);

                Translate_AllinOne.LOGGER.info(
                        "Created passive cache snapshot at {} with {} file(s), triggered by {} cache file {}.",
                        backupDirectory,
                        cacheFiles.size(),
                        cacheTypeLabel,
                        cacheFilePath.getFileName()
                );

                cleanupBackupDirectories();
            } catch (IOException e) {
                Translate_AllinOne.LOGGER.warn(
                        "Failed to create passive cache snapshot triggered by {} cache file {}.",
                        cacheTypeLabel,
                        cacheFilePath,
                        e
                );
            }
        }
    }

    public static void enforceBackupLimit() {
        synchronized (BACKUP_LOCK) {
            cleanupBackupDirectories();
        }
    }

    public static List<Path> findVerifiedComponentCacheBackups(Path componentCacheFile) {
        if (componentCacheFile == null) {
            return List.of();
        }
        Path normalizedCacheFile = componentCacheFile.toAbsolutePath().normalize();
        Path normalizedCacheRoot = CACHE_ROOT.toAbsolutePath().normalize();
        if (!normalizedCacheFile.startsWith(normalizedCacheRoot)) {
            return List.of();
        }
        Path relativePath = normalizedCacheRoot.relativize(normalizedCacheFile);
        String expectedRelativePath = relativePath.toString().replace('\\', '/');
        List<Path> verified = new ArrayList<>();
        for (BackupDirectorySummary backup : listManagedBackupDirectories()) {
            Path backupDirectory = BACKUP_ROOT.resolve(backup.directoryName());
            Path candidate = backupDirectory.resolve(relativePath);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                ManifestEntry manifestEntry = readManifestEntry(backupDirectory, expectedRelativePath);
                if (manifestEntry != null
                        && manifestEntry.size() == Files.size(candidate)
                        && manifestEntry.sha256().equals(sha256(candidate))) {
                    verified.add(candidate);
                }
            } catch (IOException error) {
                Translate_AllinOne.LOGGER.warn(
                        "Failed to verify Component cache backup file {}.",
                        candidate,
                        error
                );
            }
        }
        return List.copyOf(verified);
    }

    private static List<Path> listCurrentCacheFiles() throws IOException {
        if (!Files.isDirectory(CACHE_ROOT)) {
            return List.of();
        }

        List<Path> currentFiles = new ArrayList<>();
        try (Stream<Path> files = Files.list(CACHE_ROOT)) {
            currentFiles.addAll(files
                    .filter(Files::isRegularFile)
                    .filter(CacheBackupManager::isCacheFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList());
        }
        if (Files.isDirectory(COMPONENT_CACHE_ROOT)) {
            try (Stream<Path> files = Files.list(COMPONENT_CACHE_ROOT)) {
                currentFiles.addAll(files
                        .filter(Files::isRegularFile)
                        .filter(CacheBackupManager::isCacheFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList());
            }
        }
        return currentFiles;
    }

    private static boolean isCacheFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith("_cache.json")
                && !fileName.startsWith("component_v1_")
                && !"component_v1_translate_cache_v2.json".equals(fileName);
    }

    private static ManifestEntry readManifestEntry(Path backupDirectory, String expectedRelativePath) throws IOException {
        Path manifestPath = backupDirectory.resolve(BACKUP_MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Cache backup manifest");
            boolean createdAt = false;
            List<ManifestEntry> entries = null;
            Set<String> fields = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw new IOException("Duplicate cache backup manifest field: " + field);
                }
                switch (field) {
                    case "created_at" -> {
                        requireToken(reader, JsonToken.STRING, field);
                        reader.nextString();
                        createdAt = true;
                    }
                    case "files" -> entries = readManifestEntries(reader);
                    default -> throw new IOException("Unknown cache backup manifest field: " + field);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Trailing content after cache backup manifest.");
            }
            if (!createdAt || entries == null) {
                throw new IOException("Cache backup manifest is missing required fields.");
            }
            return entries.stream()
                    .filter(entry -> expectedRelativePath.equals(entry.relativePath()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static List<ManifestEntry> readManifestEntries(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Cache backup manifest files");
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> relativePaths = new HashSet<>();
        reader.beginArray();
        while (reader.hasNext()) {
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Cache backup manifest file");
            String relativePath = null;
            String hash = null;
            Long size = null;
            Set<String> fields = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw new IOException("Duplicate cache backup manifest file field: " + field);
                }
                switch (field) {
                    case "relative_path" -> {
                        requireToken(reader, JsonToken.STRING, field);
                        relativePath = reader.nextString();
                    }
                    case "sha256" -> {
                        requireToken(reader, JsonToken.STRING, field);
                        hash = reader.nextString();
                    }
                    case "size" -> {
                        requireToken(reader, JsonToken.NUMBER, field);
                        try {
                            size = reader.nextLong();
                        } catch (NumberFormatException error) {
                            throw new IOException("Cache backup manifest file size is invalid.", error);
                        }
                    }
                    default -> throw new IOException("Unknown cache backup manifest file field: " + field);
                }
            }
            reader.endObject();
            if (relativePath == null || !isSha256(hash) || size == null || size < 0L) {
                throw new IOException("Cache backup manifest file entry is invalid.");
            }
            if (!relativePaths.add(relativePath)) {
                throw new IOException("Duplicate cache backup manifest relative path: " + relativePath);
            }
            entries.add(new ManifestEntry(relativePath, hash, size));
        }
        reader.endArray();
        return entries;
    }

    private static void requireToken(JsonReader reader, JsonToken expected, String field) throws IOException {
        if (reader.peek() != expected) {
            throw new IOException(field + " must be " + expected + ".");
        }
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 71 || !value.startsWith("sha256:")) {
            return false;
        }
        for (int index = 7; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(current >= '0' && current <= '9') && !(current >= 'a' && current <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private static BackupDirectorySummary latestBackupDirectory() {
        List<BackupDirectorySummary> backups = listManagedBackupDirectories();
        return backups.isEmpty() ? null : backups.get(0);
    }

    private static void cleanupBackupDirectories() {
        List<BackupDirectorySummary> backups = new ArrayList<>(listManagedBackupDirectories());
        int maxBackupDirectories = getMaxBackupDirectories();
        if (backups.size() <= maxBackupDirectories) {
            return;
        }

        backups.sort(Comparator.comparing(BackupDirectorySummary::backupTime).reversed());
        for (int index = maxBackupDirectories; index < backups.size(); index++) {
            Path candidate = BACKUP_ROOT.resolve(backups.get(index).directoryName());
            try {
                deleteBackupDirectory(candidate);
            } catch (IOException e) {
                Translate_AllinOne.LOGGER.warn("Failed to remove obsolete cache backup directory {}", candidate, e);
            }
        }
    }

    private static void deleteBackupDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> contents = Files.walk(directory)) {
            for (Path entry : contents.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static BackupDirectorySummary toBackupDirectorySummary(Path directory) {
        Instant backupTime = parseBackupTime(directory.getFileName().toString());
        if (backupTime == null) {
            return null;
        }

        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> backupFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !BACKUP_MARKER_FILE_NAME.equals(path.getFileName().toString()))
                    .filter(path -> !BACKUP_MANIFEST_FILE_NAME.equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            if (backupFiles.isEmpty()) {
                return null;
            }

            long totalBytes = 0L;
            List<String> fileNames = new ArrayList<>(backupFiles.size());
            for (Path backupFile : backupFiles) {
                fileNames.add(directory.relativize(backupFile).toString().replace('\\', '/'));
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
    }

    private static void markManagedBackupDirectory(Path backupDirectory) throws IOException {
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

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder value = new StringBuilder("sha256:");
            for (byte entry : hash) {
                value.append(String.format("%02x", entry));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static Duration getBackupInterval() {
        return Duration.ofMinutes(getConfiguredBackupIntervalMinutes());
    }

    private static int getMaxBackupDirectories() {
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

    public record BackupDirectorySummary(
            String directoryName,
            Instant backupTime,
            List<String> fileNames,
            long totalBytes
    ) {
    }

    private record ManifestEntry(String relativePath, String sha256, long size) {
    }
}
