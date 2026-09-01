package com.alexeys.translate_allinone.utils.backup;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.registration.ConfigManager;
import com.alexeys.translate_allinone.utils.config.ConfigApiKeyEncryptionSupport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class VersionUpgradeBackupManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UPGRADE_BACKUP_FOLDER_NAME = "translate_update_backup";
    private static final String STATE_FILE_NAME = ".version_backup_state.json";
    private static final String BACKUP_ENCRYPTION_MARKER_FILE_NAME = ".api_key_encryption_completed";
    private static final Set<String> EXCLUDED_TOP_LEVEL_DIRECTORIES = Set.of(
            "translate_cache_backup",
            UPGRADE_BACKUP_FOLDER_NAME
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final ZoneId BACKUP_ZONE = ZoneId.systemDefault();
    private static final Path CONFIG_ROOT = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Translate_AllinOne.MOD_ID);
    private static final Path STATE_PATH = CONFIG_ROOT.resolve(STATE_FILE_NAME);
    private static final Path UPGRADE_BACKUP_ROOT = CONFIG_ROOT.resolve(UPGRADE_BACKUP_FOLDER_NAME);

    private VersionUpgradeBackupManager() {
    }

    public static void backupIfVersionChanged() {
        encryptExistingBackupConfigs();
        String currentVersion = resolveCurrentVersion();
        VersionBackupState state = loadState();
        String previousVersion = normalizeVersion(state.lastSeenModVersion);
        List<Path> filesToBackup = listBackupCandidates();
        boolean hasExistingFiles = !filesToBackup.isEmpty();
        boolean versionChanged = previousVersion != null && !previousVersion.equals(currentVersion);
        boolean shouldCreateSafetyBackup = previousVersion == null && hasExistingFiles;

        if (!versionChanged && !shouldCreateSafetyBackup) {
            if (previousVersion == null || !currentVersion.equals(previousVersion)) {
                saveState(currentVersion, state.lastUpgradeBackupAt);
            }
            return;
        }

        if (!hasExistingFiles) {
            Translate_AllinOne.LOGGER.info(
                    "Detected mod version state change (previous={}, current={}) but no existing config or cache files were found to back up.",
                    previousVersion == null ? "unknown" : previousVersion,
                    currentVersion
            );
            saveState(currentVersion, state.lastUpgradeBackupAt);
            return;
        }

        String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now(BACKUP_ZONE));
        Path backupDirectory = UPGRADE_BACKUP_ROOT.resolve(buildBackupDirectoryName(timestamp, previousVersion, currentVersion));

        try {
            Files.createDirectories(backupDirectory);
            for (Path sourceFile : filesToBackup) {
                Path relativePath = CONFIG_ROOT.relativize(sourceFile);
                Path targetFile = backupDirectory.resolve(relativePath);
                Path targetParent = targetFile.getParent();
                if (targetParent != null) {
                    Files.createDirectories(targetParent);
                }
                if (isMainConfigFile(relativePath)) {
                    copyWithApiKeyEncryption(sourceFile, targetFile);
                } else {
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }

            saveState(currentVersion, Instant.now().toString());
            Translate_AllinOne.LOGGER.info(
                    "Created version-upgrade backup at {} for {} file(s) (previous={}, current={}).",
                    backupDirectory,
                    filesToBackup.size(),
                    previousVersion == null ? "unknown" : previousVersion,
                    currentVersion
            );
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.error(
                    "Failed to create version-upgrade backup for mod version change (previous={}, current={}).",
                    previousVersion == null ? "unknown" : previousVersion,
                    currentVersion,
                    e
            );
        }
    }

    private static void encryptExistingBackupConfigs() {
        if (!Files.isDirectory(UPGRADE_BACKUP_ROOT)) {
            return;
        }
        if (Files.exists(UPGRADE_BACKUP_ROOT.resolve(BACKUP_ENCRYPTION_MARKER_FILE_NAME))) {
            return;
        }
        boolean allSucceeded = true;
        try (Stream<Path> paths = Files.walk(UPGRADE_BACKUP_ROOT)) {
            List<Path> backupConfigs = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(Translate_AllinOne.MOD_ID + ".json"))
                    .toList();
            for (Path backupConfig : backupConfigs) {
                if (!encryptBackupConfigInPlace(backupConfig)) {
                    allSucceeded = false;
                }
            }
        } catch (IOException e) {
            allSucceeded = false;
            Translate_AllinOne.LOGGER.warn("Failed to scan old version-upgrade backups for API key encryption: {}", UPGRADE_BACKUP_ROOT, e);
        }
        if (allSucceeded) {
            createMarkerFile();
        }
    }

    private static boolean encryptBackupConfigInPlace(Path configFile) {
        try {
            String rawJson = Files.readString(configFile, StandardCharsets.UTF_8);
            ConfigApiKeyEncryptionSupport.Result result = ConfigManager.encryptBackupConfig(rawJson);
            if (result.changed()) {
                Files.writeString(configFile, result.json(), StandardCharsets.UTF_8);
                Translate_AllinOne.LOGGER.info("Encrypted API keys in old version-upgrade backup: {}", configFile);
                return true;
            }
            if (result.retryNeeded()) {
                Translate_AllinOne.LOGGER.warn("Could not encrypt API keys in old version-upgrade backup, will retry on next launch: {}", configFile);
                return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.warn("Failed to process old version-upgrade backup for API key encryption: {}", configFile, e);
            return false;
        }
    }

    private static void createMarkerFile() {
        try {
            Files.createDirectories(UPGRADE_BACKUP_ROOT);
            Files.writeString(UPGRADE_BACKUP_ROOT.resolve(BACKUP_ENCRYPTION_MARKER_FILE_NAME), "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to create API key backup encryption marker: {}", UPGRADE_BACKUP_ROOT, e);
        }
    }

    private static boolean isMainConfigFile(Path relativePath) {
        return relativePath.getNameCount() == 1
                && relativePath.getFileName().toString().equals(Translate_AllinOne.MOD_ID + ".json");
    }

    private static void copyWithApiKeyEncryption(Path sourceFile, Path targetFile) throws IOException {
        try {
            String rawJson = Files.readString(sourceFile, StandardCharsets.UTF_8);
            ConfigApiKeyEncryptionSupport.Result result = ConfigManager.encryptBackupConfig(rawJson);
            if (result.changed()) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                Files.writeString(targetFile, result.json(), StandardCharsets.UTF_8);
                return;
            }
            if (result.retryNeeded()) {
                Translate_AllinOne.LOGGER.warn("Could not encrypt API keys in upgrade backup, falling back to raw copy: {}", sourceFile);
            }
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.warn("Failed to encrypt API keys in upgrade backup, falling back to raw copy: {}", sourceFile, e);
        }
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static List<Path> listBackupCandidates() {
        if (!Files.isDirectory(CONFIG_ROOT)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(CONFIG_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(VersionUpgradeBackupManager::shouldIncludeInBackup)
                    .toList();
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to inspect config directory for version-upgrade backup: {}", CONFIG_ROOT, e);
            return List.of();
        }
    }

    private static boolean shouldIncludeInBackup(Path path) {
        Path relativePath = CONFIG_ROOT.relativize(path);
        if (relativePath.getNameCount() == 0) {
            return false;
        }

        String firstSegment = relativePath.getName(0).toString();
        if (EXCLUDED_TOP_LEVEL_DIRECTORIES.contains(firstSegment)) {
            return false;
        }
        if ("translate_cache".equals(firstSegment)
                && relativePath.getNameCount() > 1
                && ("migration".equals(relativePath.getName(1).toString())
                || "corrupt".equals(relativePath.getName(1).toString()))) {
            return false;
        }

        String fileName = path.getFileName().toString();
        if (STATE_FILE_NAME.equals(fileName)) {
            return false;
        }

        return !fileName.endsWith(".tmp");
    }

    private static VersionBackupState loadState() {
        if (!Files.isRegularFile(STATE_PATH)) {
            return new VersionBackupState();
        }

        try (Reader reader = Files.newBufferedReader(STATE_PATH)) {
            VersionBackupState loadedState = GSON.fromJson(reader, VersionBackupState.class);
            return loadedState == null ? new VersionBackupState() : loadedState;
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.warn("Failed to load version backup state from {}. A safety backup may be created.", STATE_PATH, e);
            return new VersionBackupState();
        }
    }

    private static void saveState(String currentVersion, String lastUpgradeBackupAt) {
        VersionBackupState state = new VersionBackupState();
        state.lastSeenModVersion = currentVersion;
        state.lastUpgradeBackupAt = lastUpgradeBackupAt;

        try {
            Files.createDirectories(CONFIG_ROOT);
            Path tempPath = STATE_PATH.resolveSibling(STATE_PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(state, writer);
            }

            try {
                Files.move(tempPath, STATE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, STATE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to save version backup state to {}", STATE_PATH, e);
        }
    }

    private static String resolveCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(Translate_AllinOne.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String buildBackupDirectoryName(String timestamp, String previousVersion, String currentVersion) {
        return timestamp
                + "__"
                + sanitizeVersion(previousVersion == null ? "unknown" : previousVersion)
                + "_to_"
                + sanitizeVersion(currentVersion);
    }

    private static String sanitizeVersion(String version) {
        return version.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }

        String trimmed = version.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class VersionBackupState {
        private String lastSeenModVersion = "";
        private String lastUpgradeBackupAt = "";
    }
}
