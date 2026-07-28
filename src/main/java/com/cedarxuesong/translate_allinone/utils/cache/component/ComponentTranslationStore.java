package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheFileSaveSupport;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonException;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonLimits;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationPreparedRequest;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ComponentTranslationStore {
    public static final String CACHE_FILE = "component_v1_translate_cache_v2.json";
    public static final int SCHEMA_VERSION = 2;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4_096;
    private static final int MAX_TRANSLATIONS_PER_ENTRY = ComponentJsonLimits.DEFAULT.maxTextUnits();
    private static final int MAX_TRANSLATION_CHARS = ComponentJsonLimits.DEFAULT.maxTranslationChars();
    private static final int MAX_ID_CHARS = 256;
    private static final long SAVE_DEBOUNCE_MILLIS = 1_500L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;
    private final boolean passiveBackupEnabled;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ComponentTranslationValidator validator = new ComponentTranslationValidator();
    private final ScheduledExecutorService saveExecutor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private boolean dirty;
    private boolean writeProtected;

    private ComponentTranslationStore() {
        this(CacheBackupManager.getCacheDirectory(), true);
    }

    ComponentTranslationStore(Path cacheDirectory) {
        this(cacheDirectory, false);
    }

    private ComponentTranslationStore(Path cacheDirectory, boolean passiveBackupEnabled) {
        if (cacheDirectory == null) {
            throw new IllegalArgumentException("Component V2 cache directory is required.");
        }
        this.path = cacheDirectory.resolve(CACHE_FILE);
        this.passiveBackupEnabled = passiveBackupEnabled;
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "translate_allinone-component-v2-save");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static ComponentTranslationStore getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void load() {
        entries.clear();
        dirty = false;
        writeProtected = false;
        saveScheduled.set(false);
        if (!Files.exists(path)) {
            Translate_AllinOne.LOGGER.info("Component V2 cache file not found; starting empty.");
            return;
        }

        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                throw new IOException("Component V2 cache file exceeds the size limit.");
            }
            entries.putAll(readFile());
            ComponentTranslationDebugLogger.flowForNamespace(
                    "v2",
                    "cache_load schema=v2 entries={} path={}",
                    entries.size(),
                    path
            );
        } catch (IOException | RuntimeException error) {
            entries.clear();
            writeProtected = true;
            Translate_AllinOne.LOGGER.error(
                    "Failed to load Component V2 cache. The original file will be preserved and not overwritten this session: {}",
                    path,
                    error
            );
        }
    }

    public synchronized Lookup lookup(ComponentTranslationPreparedRequest request) {
        if (request == null) {
            return new Lookup(Status.MISS, "", null);
        }
        String key = request.identity().key();
        Entry entry = entries.get(key);
        if (entry == null) {
            return new Lookup(Status.MISS, key, null);
        }
        if (!request.identity().binding().equals(entry.binding())) {
            removeInternal(key);
            return new Lookup(Status.INVALID, key, null);
        }
        try {
            ComponentTranslationResponse response = validator.validate(
                    request.document(),
                    new ComponentTranslationResponse(request.document().protocol(), entry.translations())
            );
            return new Lookup(Status.HIT, key, response);
        } catch (ComponentJsonException | IllegalArgumentException error) {
            removeInternal(key);
            ComponentTranslationDebugLogger.error(
                    request.document().route(),
                    "V2 cache entry rejected: route={} key={} reason={}",
                    request.document().route().wireName(),
                    key,
                    error.getMessage(),
                    error
            );
            return new Lookup(Status.INVALID, key, null);
        }
    }

    public synchronized boolean put(
            ComponentTranslationPreparedRequest request,
            ComponentTranslationResponse response
    ) {
        if (request == null || response == null || writeProtected) {
            return false;
        }
        ComponentTranslationResponse validated = validator.validate(request.document(), response);
        Entry entry = new Entry(request.identity().binding(), validated.translations());
        Entry previous = entries.put(request.identity().key(), entry);
        if (!entry.equals(previous)) {
            dirty = true;
            scheduleSave();
        }
        return true;
    }

    public synchronized boolean remove(ComponentTranslationPreparedRequest request) {
        return request != null && removeInternal(request.identity().key());
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
            for (Map.Entry<String, Entry> entry : new TreeMap<>(entries).entrySet()) {
                JsonObject serialized = new JsonObject();
                serialized.addProperty("binding", entry.getValue().binding());
                JsonObject translations = new JsonObject();
                for (Map.Entry<String, String> translation : entry.getValue().translations().entrySet()) {
                    translations.addProperty(translation.getKey(), translation.getValue());
                }
                serialized.add("translations", translations);
                serializedEntries.add(entry.getKey(), serialized);
            }
            root.add("entries", serializedEntries);
            try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(path, "component v2");
            }
            ComponentTranslationDebugLogger.flowForNamespace(
                    "v2",
                    "cache_save schema=v2 entries={} path={}",
                    entries.size(),
                    path
            );
        } catch (IOException error) {
            Translate_AllinOne.LOGGER.error("Failed to save Component V2 cache: {}", path, error);
        }
    }

    synchronized int entryCount() {
        return entries.size();
    }

    public synchronized boolean isWriteProtected() {
        return writeProtected;
    }

    private Map<String, Entry> readFile() throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Component V2 cache root");
            Integer schemaVersion = null;
            Map<String, Entry> loadedEntries = null;
            Set<String> fields = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw new IOException("Duplicate Component V2 cache field: " + field);
                }
                switch (field) {
                    case "schema_version" -> schemaVersion = readInt(reader, field);
                    case "entries" -> loadedEntries = readEntries(reader);
                    default -> throw new IOException("Unknown Component V2 cache field: " + field);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Trailing content after Component V2 cache document.");
            }
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                throw new IOException("Unsupported Component V2 cache schema version: " + schemaVersion);
            }
            if (loadedEntries == null) {
                throw new IOException("Component V2 cache entries field is missing.");
            }
            return loadedEntries;
        }
    }

    private Map<String, Entry> readEntries(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component V2 cache entries");
        Map<String, Entry> loaded = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!isSha256(key)) {
                throw new IOException("Invalid Component V2 cache key: " + key);
            }
            if (loaded.containsKey(key)) {
                throw new IOException("Duplicate Component V2 cache key: " + key);
            }
            if (loaded.size() >= MAX_ENTRIES) {
                throw new IOException("Component V2 cache exceeds the entry limit.");
            }
            loaded.put(key, readEntry(reader));
        }
        reader.endObject();
        return loaded;
    }

    private Entry readEntry(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component V2 cache entry");
        String binding = null;
        Map<String, String> translations = null;
        Set<String> fields = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                throw new IOException("Duplicate Component V2 cache entry field: " + field);
            }
            switch (field) {
                case "binding" -> binding = readSha256(reader, field);
                case "translations" -> translations = readTranslations(reader);
                default -> throw new IOException("Unknown Component V2 cache entry field: " + field);
            }
        }
        reader.endObject();
        if (binding == null || translations == null) {
            throw new IOException("Component V2 cache entry is missing required fields.");
        }
        return new Entry(binding, translations);
    }

    private Map<String, String> readTranslations(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component V2 cached translations");
        Map<String, String> translations = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            if (id.isBlank() || id.length() > MAX_ID_CHARS || !isValidUtf16(id)) {
                throw new IOException("Invalid Component V2 cached translation id.");
            }
            if (translations.containsKey(id)) {
                throw new IOException("Duplicate Component V2 cached translation id: " + id);
            }
            if (translations.size() >= MAX_TRANSLATIONS_PER_ENTRY) {
                throw new IOException("Component V2 cache entry exceeds the translation limit.");
            }
            String translation = readString(reader, "translations." + id);
            if (translation.length() > MAX_TRANSLATION_CHARS || !isValidUtf16(translation)) {
                throw new IOException("Invalid Component V2 cached translation: " + id);
            }
            translations.put(id, translation);
        }
        reader.endObject();
        return translations;
    }

    private boolean removeInternal(String key) {
        if (writeProtected || entries.remove(key) == null) {
            return false;
        }
        dirty = true;
        scheduleSave();
        return true;
    }

    private void scheduleSave() {
        if (!writeProtected && saveScheduled.compareAndSet(false, true)) {
            saveExecutor.schedule(this::save, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static void requireToken(JsonReader reader, JsonToken expected, String field) throws IOException {
        if (reader.peek() != expected) {
            throw new IOException(field + " must be " + expected + ".");
        }
    }

    private static String readString(JsonReader reader, String field) throws IOException {
        requireToken(reader, JsonToken.STRING, field);
        return reader.nextString();
    }

    private static String readSha256(JsonReader reader, String field) throws IOException {
        String value = readString(reader, field);
        if (!isSha256(value)) {
            throw new IOException(field + " must be a SHA-256 cache digest.");
        }
        return value;
    }

    private static int readInt(JsonReader reader, String field) throws IOException {
        requireToken(reader, JsonToken.NUMBER, field);
        try {
            return reader.nextInt();
        } catch (NumberFormatException error) {
            throw new IOException(field + " must be an integer.", error);
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

    private static boolean isValidUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    public record Lookup(Status status, String cacheKey, ComponentTranslationResponse response) {
    }

    public enum Status {
        HIT,
        MISS,
        INVALID
    }

    private record Entry(String binding, Map<String, String> translations) {
        private Entry {
            if (!isSha256(binding) || translations == null || translations.size() > MAX_TRANSLATIONS_PER_ENTRY) {
                throw new IllegalArgumentException("Component V2 cache entry is invalid.");
            }
            translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
        }
    }

    private static final class Holder {
        private static final ComponentTranslationStore INSTANCE = new ComponentTranslationStore();
    }
}
