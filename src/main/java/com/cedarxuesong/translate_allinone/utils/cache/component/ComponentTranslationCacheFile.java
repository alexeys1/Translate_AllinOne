package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheFileSaveSupport;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ComponentTranslationCacheFile {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final long SAVE_DEBOUNCE_MILLIS = 1500L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private final String label;
    private final boolean passiveBackupEnabled;
    private final Map<String, ComponentTranslationCacheEntry> entries = new LinkedHashMap<>();
    private final ScheduledExecutorService saveExecutor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private boolean dirty;
    private boolean writeProtected;

    ComponentTranslationCacheFile(Path path, String label, boolean passiveBackupEnabled) {
        this.path = path;
        this.label = label;
        this.passiveBackupEnabled = passiveBackupEnabled;
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "translate_allinone-component-v1-" + label + "-save");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void load() {
        entries.clear();
        dirty = false;
        writeProtected = false;
        saveScheduled.set(false);
        if (!Files.exists(path)) {
            Translate_AllinOne.LOGGER.info("Component V1 {} cache file not found; starting empty.", label);
            return;
        }

        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                throw new IOException("Component V1 cache file exceeds the size limit.");
            }
            Map<String, ComponentTranslationCacheEntry> loaded = readFile();
            entries.putAll(loaded);
            ComponentTranslationDebugLogger.flowForNamespace(
                    label,
                    "cache_load namespace={} entries={} path={}",
                    label,
                    entries.size(),
                    path
            );
            ComponentTranslationDebugLogger.throttled(
                    "cache-lifecycle",
                    "Loaded {} Component V1 {} cache entries.",
                    entries.size(),
                    label
            );
        } catch (IOException | RuntimeException e) {
            entries.clear();
            writeProtected = true;
            Translate_AllinOne.LOGGER.error(
                    "Failed to load Component V1 {} cache. The original file will be preserved and not overwritten this session: {}",
                    label,
                    path,
                    e
            );
        }
    }

    public synchronized ComponentTranslationCacheEntry get(String key) {
        return entries.get(key);
    }

    public synchronized void put(String key, ComponentTranslationCacheEntry entry) {
        ComponentTranslationCacheEntry previous = entries.put(key, entry);
        if (!entry.equals(previous)) {
            dirty = true;
            scheduleSave();
        }
    }

    public synchronized boolean remove(String key) {
        if (entries.remove(key) == null) {
            return false;
        }
        dirty = true;
        scheduleSave();
        return true;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized boolean isWriteProtected() {
        return writeProtected;
    }

    public synchronized void save() {
        saveScheduled.set(false);
        if (!dirty || writeProtected) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            JsonObject root = new JsonObject();
            root.addProperty("schema_version", SCHEMA_VERSION);
            JsonObject serializedEntries = new JsonObject();
            for (Map.Entry<String, ComponentTranslationCacheEntry> entry : new TreeMap<>(entries).entrySet()) {
                serializedEntries.add(entry.getKey(), toJson(entry.getValue()));
            }
            root.add("entries", serializedEntries);
            try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(path, "component v1 " + label);
            }
            ComponentTranslationDebugLogger.flowForNamespace(
                    label,
                    "cache_save namespace={} entries={} path={}",
                    label,
                    entries.size(),
                    path
            );
            ComponentTranslationDebugLogger.throttled(
                    "cache-lifecycle",
                    "Saved {} Component V1 {} cache entries.",
                    entries.size(),
                    label
            );
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.error("Failed to save Component V1 {} cache: {}", label, path, e);
        }
    }

    private Map<String, ComponentTranslationCacheEntry> readFile() throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IOException("Component V1 cache root must be an object.");
            }
            Integer schemaVersion = null;
            Map<String, ComponentTranslationCacheEntry> loadedEntries = null;
            Set<String> seen = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!seen.add(field)) {
                    throw new IOException("Duplicate Component V1 cache field: " + field);
                }
                switch (field) {
                    case "schema_version" -> schemaVersion = readInt(reader, field);
                    case "entries" -> loadedEntries = readEntries(reader);
                    default -> throw new IOException("Unknown Component V1 cache field: " + field);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Trailing content after Component V1 cache document.");
            }
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                throw new IOException("Unsupported Component V1 cache schema version: " + schemaVersion);
            }
            if (loadedEntries == null) {
                throw new IOException("Component V1 cache entries field is missing.");
            }
            return loadedEntries;
        }
    }

    private Map<String, ComponentTranslationCacheEntry> readEntries(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IOException("Component V1 cache entries must be an object.");
        }
        Map<String, ComponentTranslationCacheEntry> loaded = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (loaded.containsKey(key)) {
                throw new IOException("Duplicate Component V1 cache key: " + key);
            }
            if (!key.startsWith("sha256:") || key.length() != 71) {
                throw new IOException("Invalid Component V1 cache key: " + key);
            }
            loaded.put(key, readEntry(reader));
        }
        reader.endObject();
        return loaded;
    }

    private ComponentTranslationCacheEntry readEntry(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IOException("Component V1 cache entry must be an object.");
        }
        String protocol = null;
        Integer policyVersion = null;
        String route = null;
        String targetLanguage = null;
        String structureFingerprint = null;
        String sourceFingerprint = null;
        String tokenFingerprint = null;
        Map<String, String> translations = null;
        Set<String> seen = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!seen.add(field)) {
                throw new IOException("Duplicate Component V1 cache entry field: " + field);
            }
            switch (field) {
                case "protocol" -> protocol = readString(reader, field);
                case "policy_version" -> policyVersion = readInt(reader, field);
                case "route" -> route = readString(reader, field);
                case "target_language" -> targetLanguage = readString(reader, field);
                case "structure_fingerprint" -> structureFingerprint = readString(reader, field);
                case "source_fingerprint" -> sourceFingerprint = readString(reader, field);
                case "token_fingerprint" -> tokenFingerprint = readString(reader, field);
                case "translations" -> translations = readTranslations(reader);
                default -> throw new IOException("Unknown Component V1 cache entry field: " + field);
            }
        }
        reader.endObject();
        if (protocol == null
                || policyVersion == null
                || route == null
                || targetLanguage == null
                || structureFingerprint == null
                || sourceFingerprint == null
                || tokenFingerprint == null
                || translations == null) {
            throw new IOException("Component V1 cache entry is missing required fields.");
        }
        return new ComponentTranslationCacheEntry(
                protocol,
                policyVersion,
                route,
                targetLanguage,
                structureFingerprint,
                sourceFingerprint,
                tokenFingerprint,
                translations
        );
    }

    private Map<String, String> readTranslations(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IOException("Component V1 cached translations must be an object.");
        }
        Map<String, String> translations = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            if (translations.containsKey(id)) {
                throw new IOException("Duplicate cached translation id: " + id);
            }
            translations.put(id, readString(reader, "translations." + id));
        }
        reader.endObject();
        return translations;
    }

    private static String readString(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            throw new IOException(field + " must be a string.");
        }
        return reader.nextString();
    }

    private static int readInt(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw new IOException(field + " must be an integer.");
        }
        return reader.nextInt();
    }

    private static JsonObject toJson(ComponentTranslationCacheEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("protocol", entry.protocol());
        object.addProperty("policy_version", entry.policyVersion());
        object.addProperty("route", entry.route());
        object.addProperty("target_language", entry.targetLanguage());
        object.addProperty("structure_fingerprint", entry.structureFingerprint());
        object.addProperty("source_fingerprint", entry.sourceFingerprint());
        object.addProperty("token_fingerprint", entry.tokenFingerprint());
        JsonObject translations = new JsonObject();
        for (Map.Entry<String, String> translation : entry.translations().entrySet()) {
            translations.addProperty(translation.getKey(), translation.getValue());
        }
        object.add("translations", translations);
        return object;
    }

    private void scheduleSave() {
        if (writeProtected || !saveScheduled.compareAndSet(false, true)) {
            return;
        }
        saveExecutor.schedule(this::save, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }
}
