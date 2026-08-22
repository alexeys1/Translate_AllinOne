package com.alexeys.translate_allinone.utils.cache.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ComponentCacheMigrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("translate_allinone");
    public static final String MIGRATION_ID = "component-cache-layout-v4";
    private static final String STATE_FILE_NAME = "state.json";
    private static final String SNAPSHOT_MANIFEST_FILE_NAME = "pre_migration_manifest.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<LegacySource> SOURCES = List.of(
            new LegacySource("scoreboard_translate_cache.json", "scoreboard_translate_cache.json", "scoreboard"),
            new LegacySource("other_translations_translate_cache.json", "advancement_translate_cache.json", "advancement")
    );

    private ComponentCacheMigrationService() {
    }

    public static synchronized void migrateLegacyCaches(Path root, Path componentRoot, Runnable initializeStores) {
        if (root == null || componentRoot == null || initializeStores == null) {
            return;
        }
        Path migrationRoot = componentRoot.resolve("migration");
        Path statePath = migrationRoot.resolve(MIGRATION_ID).resolve(STATE_FILE_NAME);
        try {
            Files.createDirectories(componentRoot);
            createSnapshotIfMissing(root, migrationRoot.resolve(MIGRATION_ID));
            initializeStores.run();
            Map<String, String> states = new LinkedHashMap<>();
            for (LegacySource source : SOURCES) {
                states.put(source.sourceName(), migrateSource(root, migrationRoot.resolve("legacy"), source));
                writeState(statePath, states);
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Failed to migrate legacy Component cache layout.", error);
        }
    }

    private static String migrateSource(Path root, Path archiveRoot, LegacySource source) {
        Path sourcePath = root.resolve(source.sourceName());
        Path archivePath = archiveRoot.resolve(source.archiveName());
        try {
            if (Files.isRegularFile(archivePath)) {
                if (!Files.exists(sourcePath)) {
                    return "archived";
                }
                if (sha256(sourcePath).equals(sha256(archivePath))) {
                    Files.delete(sourcePath);
                    return "archived";
                }
                return "conflict";
            }
            if (!Files.isRegularFile(sourcePath)) {
                return "absent";
            }
            if (!isCompleteJsonObject(sourcePath)) {
                return "invalid";
            }
            Files.createDirectories(archivePath.getParent());
            moveWithVerifiedFallback(sourcePath, archivePath);
            return "archived";
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Failed to archive legacy {} cache file: {}", source.module(), sourcePath, error);
            return "failed";
        }
    }

    private static void createSnapshotIfMissing(Path root, Path stateRoot) throws IOException {
        Path manifestPath = stateRoot.resolve(SNAPSHOT_MANIFEST_FILE_NAME);
        if (Files.isRegularFile(manifestPath)) {
            return;
        }
        Path snapshotRoot = stateRoot.resolve("pre_migration_snapshot");
        Files.createDirectories(snapshotRoot);
        JsonArray files = new JsonArray();
        if (Files.isDirectory(root)) {
            try (Stream<Path> paths = Files.list(root)) {
                for (Path source : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String fileName = source.getFileName().toString();
                    if (!fileName.endsWith("_cache.json")) {
                        continue;
                    }
                    Path target = snapshotRoot.resolve(fileName);
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("relative_path", fileName);
                    entry.addProperty("sha256", sha256(source));
                    entry.addProperty("size", Files.size(source));
                    files.add(entry);
                }
            }
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("migration_id", MIGRATION_ID);
        manifest.addProperty("created_at", Instant.now().toString());
        manifest.add("files", files);
        writeJson(manifestPath, manifest);
    }

    private static boolean isCompleteJsonObject(Path source) {
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && parsed.isJsonObject();
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static void moveWithVerifiedFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException ignored) {
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        if (!sha256(source).equals(sha256(target))) {
            Files.deleteIfExists(target);
            throw new IOException("Legacy cache archive checksum verification failed.");
        }
        Files.delete(source);
    }

    private static void writeState(Path statePath, Map<String, String> states) throws IOException {
        JsonObject state = new JsonObject();
        state.addProperty("migration_id", MIGRATION_ID);
        state.addProperty("updated_at", Instant.now().toString());
        JsonObject sources = new JsonObject();
        states.forEach(sources::addProperty);
        state.add("sources", sources);
        writeJson(statePath, state);
    }

    private static void writeJson(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder("sha256:");
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private record LegacySource(String sourceName, String archiveName, String module) {
    }
}
