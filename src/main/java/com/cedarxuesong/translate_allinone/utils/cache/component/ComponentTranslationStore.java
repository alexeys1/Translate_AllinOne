package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheFileSaveSupport;
import com.cedarxuesong.translate_allinone.utils.cache.CacheStats;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonException;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonLimits;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationPreparedRequest;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationTemplateIdentity;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ComponentTranslationStore {
    public static final int SCHEMA_VERSION = 4;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4_096;
    private static final int MAX_TRANSLATIONS_PER_ENTRY = ComponentJsonLimits.DEFAULT.maxTextUnits();
    private static final int MAX_TRANSLATION_CHARS = ComponentJsonLimits.DEFAULT.maxTranslationChars();
    private static final int MAX_ID_CHARS = 256;
    private static final long SAVE_DEBOUNCE_MILLIS = 1_500L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;
    private final ComponentCacheModule module;
    private final boolean passiveBackupEnabled;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> entityTemplates = new LinkedHashMap<>();
    private final ComponentTranslationValidator validator = new ComponentTranslationValidator();
    private final ScheduledExecutorService saveExecutor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private boolean dirty;
    private boolean writeProtected;

    ComponentTranslationStore(Path cacheDirectory, ComponentCacheModule module, boolean passiveBackupEnabled) {
        if (cacheDirectory == null) {
            throw new IllegalArgumentException("Component cache directory is required.");
        }
        if (module == null) {
            throw new IllegalArgumentException("Component cache module is required.");
        }
        this.path = cacheDirectory.resolve(module.fileName());
        this.module = module;
        this.passiveBackupEnabled = passiveBackupEnabled;
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "translate_allinone-component-" + module.wireName() + "-save");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void load() {
        entries.clear();
        entityTemplates.clear();
        dirty = false;
        writeProtected = false;
        saveScheduled.set(false);
        if (!Files.exists(path)) {
            Translate_AllinOne.LOGGER.info("Component {} cache file not found; starting empty.", module.wireName());
            return;
        }

        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                throw new IOException("Component cache file exceeds the size limit.");
            }
            LoadedFile loaded = readFile();
            entries.putAll(loaded.entries());
            entityTemplates.putAll(loaded.entityTemplates());
            ComponentTranslationDebugLogger.flowForNamespace(
                    module.wireName(),
                    "cache_load schema={} entries={} entityTemplates={} path={}",
                    loaded.schemaVersion(),
                    entries.size(),
                    entityTemplates.size(),
                    path
            );
        } catch (IOException | RuntimeException error) {
            entries.clear();
            writeProtected = true;
            Translate_AllinOne.LOGGER.error(
                    "Failed to load Component {} cache. The original file will be preserved and not overwritten this session: {}",
                    module.wireName(),
                    path,
                    error
            );
        }
    }

    public synchronized Lookup lookup(ComponentTranslationPreparedRequest request) {
        if (request == null || !module.owns(request.document().route())) {
            return new Lookup(Status.MISS, "", null);
        }
        String key = request.identity().key();
        Entry entry = entries.get(key);
        if (entry == null) {
            return new Lookup(Status.MISS, key, null);
        }
        if (entry.route() != request.document().route() || !request.identity().binding().equals(entry.binding())) {
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
                    "Component cache entry rejected: route={} key={} reason={}",
                    request.document().route().wireName(),
                    key,
                    error.getMessage(),
                    error
            );
            return new Lookup(Status.INVALID, key, null);
        }
    }

    public synchronized Lookup lookupEntityTemplate(ComponentTranslationPreparedRequest request) {
        if (request == null
                || module != ComponentCacheModule.ENTITY
                || request.document().route() != ComponentTranslationRoute.ENTITY_NAME) {
            return new Lookup(Status.MISS, "", null);
        }
        ComponentTranslationTemplateIdentity identity = ComponentTranslationTemplateIdentity.createEntityName(
                request.document(),
                request.targetLanguage()
        );
        String key = identity.key();
        Entry entry = entityTemplates.get(key);
        if (entry == null) {
            return new Lookup(Status.MISS, key, null);
        }
        if (entry.route() != ComponentTranslationRoute.ENTITY_NAME || !identity.binding().equals(entry.binding())) {
            removeEntityTemplateInternal(key);
            return new Lookup(Status.INVALID, key, null);
        }
        try {
            ComponentTranslationResponse response = validator.validate(
                    request.document(),
                    new ComponentTranslationResponse(request.document().protocol(), entry.translations())
            );
            return new Lookup(Status.HIT, key, response);
        } catch (ComponentJsonException | IllegalArgumentException error) {
            removeEntityTemplateInternal(key);
            ComponentTranslationDebugLogger.error(
                    request.document().route(),
                    "Entity template cache entry rejected: templateKey={} reason={}",
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
        if (request == null || response == null || writeProtected || !module.owns(request.document().route())) {
            return false;
        }
        ComponentTranslationResponse validated = validator.validate(request.document(), response);
        Entry entry = new Entry(request.document().route(), request.identity().binding(), validated.translations());
        boolean changed = !entry.equals(entries.put(request.identity().key(), entry));
        if (module == ComponentCacheModule.ENTITY && request.document().route() == ComponentTranslationRoute.ENTITY_NAME) {
            ComponentTranslationTemplateIdentity templateIdentity = ComponentTranslationTemplateIdentity.createEntityName(
                    request.document(),
                    request.targetLanguage()
            );
            Entry templateEntry = new Entry(ComponentTranslationRoute.ENTITY_NAME, templateIdentity.binding(), validated.translations());
            changed |= !templateEntry.equals(entityTemplates.put(templateIdentity.key(), templateEntry));
        }
        if (changed) {
            dirty = true;
            scheduleSave();
        }
        return true;
    }

    public synchronized boolean remove(ComponentTranslationPreparedRequest request) {
        return request != null && module.owns(request.document().route()) && removeInternal(request.identity().key());
    }

    public synchronized boolean removeEntityTemplate(ComponentTranslationPreparedRequest request) {
        if (request == null
                || module != ComponentCacheModule.ENTITY
                || request.document().route() != ComponentTranslationRoute.ENTITY_NAME) {
            return false;
        }
        return removeEntityTemplateInternal(ComponentTranslationTemplateIdentity.createEntityName(
                request.document(),
                request.targetLanguage()
        ).key());
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
            root.addProperty("cache_module", module.wireName());
            root.add("entries", serializeEntries(entries));
            if (module == ComponentCacheModule.ENTITY) {
                root.add("entity_templates", serializeEntries(entityTemplates));
            }
            try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(path, "component " + module.wireName());
            }
            ComponentTranslationDebugLogger.flowForNamespace(
                    module.wireName(),
                    "cache_save schema={} entries={} entityTemplates={} path={}",
                    SCHEMA_VERSION,
                    entries.size(),
                    entityTemplates.size(),
                    path
            );
        } catch (IOException error) {
            Translate_AllinOne.LOGGER.error("Failed to save Component {} cache: {}", module.wireName(), path, error);
        }
    }

    synchronized void ensureInitialized() {
        if (!writeProtected && !Files.exists(path)) {
            dirty = true;
            save();
        }
    }

    synchronized int entryCount() {
        return entries.size();
    }

    synchronized int entityTemplateCount() {
        return entityTemplates.size();
    }

    public synchronized boolean isWriteProtected() {
        return writeProtected;
    }

    public synchronized CacheStats getCacheStats() {
        int total = entries.size() + entityTemplates.size();
        return new CacheStats(total, total);
    }

    private LoadedFile readFile() throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cache root");
            Integer schemaVersion = null;
            String cacheModule = null;
            Map<String, Entry> loadedEntries = null;
            Map<String, Entry> loadedEntityTemplates = null;
            Set<String> fields = new HashSet<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw new IOException("Duplicate Component cache field: " + field);
                }
                switch (field) {
                    case "schema_version" -> schemaVersion = readInt(reader, field);
                    case "cache_module" -> cacheModule = readString(reader, field);
                    case "entries" -> loadedEntries = readEntries(reader, false);
                    case "entity_templates" -> loadedEntityTemplates = readEntries(reader, true);
                    default -> throw new IOException("Unknown Component cache field: " + field);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Trailing content after Component cache document.");
            }
            if (schemaVersion == null || schemaVersion != SCHEMA_VERSION) {
                throw new IOException("Unsupported Component cache schema version: " + schemaVersion);
            }
            if (!module.wireName().equals(cacheModule)) {
                throw new IOException("Component cache module does not match file ownership.");
            }
            if (loadedEntries == null) {
                throw new IOException("Component cache entries field is missing.");
            }
            if (module != ComponentCacheModule.ENTITY && loadedEntityTemplates != null) {
                throw new IOException("Only the entity Component cache can contain entity_templates.");
            }
            return new LoadedFile(
                    schemaVersion,
                    loadedEntries,
                    loadedEntityTemplates == null ? Map.of() : loadedEntityTemplates
            );
        }
    }

    private static JsonObject serializeEntries(Map<String, Entry> values) {
        JsonObject serializedEntries = new JsonObject();
        for (Map.Entry<String, Entry> entry : new TreeMap<>(values).entrySet()) {
            JsonObject serialized = new JsonObject();
            serialized.addProperty("route", entry.getValue().route().wireName());
            serialized.addProperty("binding", entry.getValue().binding());
            JsonObject translations = new JsonObject();
            for (Map.Entry<String, String> translation : entry.getValue().translations().entrySet()) {
                translations.addProperty(translation.getKey(), translation.getValue());
            }
            serialized.add("translations", translations);
            serializedEntries.add(entry.getKey(), serialized);
        }
        return serializedEntries;
    }

    private Map<String, Entry> readEntries(JsonReader reader, boolean entityTemplateEntries) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cache entries");
        Map<String, Entry> loaded = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!isSha256(key)) {
                throw new IOException("Invalid Component cache key: " + key);
            }
            if (loaded.containsKey(key)) {
                throw new IOException("Duplicate Component cache key: " + key);
            }
            if (loaded.size() >= MAX_ENTRIES) {
                throw new IOException("Component cache exceeds the entry limit.");
            }
            loaded.put(key, readEntry(reader, entityTemplateEntries));
        }
        reader.endObject();
        return loaded;
    }

    private Entry readEntry(JsonReader reader, boolean entityTemplateEntry) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cache entry");
        ComponentTranslationRoute route = null;
        String binding = null;
        Map<String, String> translations = null;
        Set<String> fields = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                throw new IOException("Duplicate Component cache entry field: " + field);
            }
            switch (field) {
                case "route" -> route = readRoute(reader, field);
                case "binding" -> binding = readSha256(reader, field);
                case "translations" -> translations = readTranslations(reader);
                default -> throw new IOException("Unknown Component cache entry field: " + field);
            }
        }
        reader.endObject();
        if (route == null || binding == null || translations == null) {
            throw new IOException("Component cache entry is missing required fields.");
        }
        if (!module.owns(route)) {
            throw new IOException("Component cache entry route is not owned by this module.");
        }
        if (entityTemplateEntry && route != ComponentTranslationRoute.ENTITY_NAME) {
            throw new IOException("Component entity template must use the entity_name route.");
        }
        return new Entry(route, binding, translations);
    }

    private Map<String, String> readTranslations(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cached translations");
        Map<String, String> translations = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            if (id.isBlank() || id.length() > MAX_ID_CHARS || !isValidUtf16(id)) {
                throw new IOException("Invalid Component cached translation id.");
            }
            if (translations.containsKey(id)) {
                throw new IOException("Duplicate Component cached translation id: " + id);
            }
            if (translations.size() >= MAX_TRANSLATIONS_PER_ENTRY) {
                throw new IOException("Component cache entry exceeds the translation limit.");
            }
            String translation = readString(reader, "translations." + id);
            if (translation.length() > MAX_TRANSLATION_CHARS || !isValidUtf16(translation)) {
                throw new IOException("Invalid Component cached translation: " + id);
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

    private boolean removeEntityTemplateInternal(String key) {
        if (writeProtected || entityTemplates.remove(key) == null) {
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

    private static ComponentTranslationRoute readRoute(JsonReader reader, String field) throws IOException {
        String wireName = readString(reader, field);
        try {
            return ComponentTranslationRoute.fromWireName(wireName);
        } catch (IllegalArgumentException error) {
            throw new IOException(field + " is not a known Component route.", error);
        }
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

    private record Entry(ComponentTranslationRoute route, String binding, Map<String, String> translations) {
        private Entry {
            if (route == null || !isSha256(binding) || translations == null || translations.size() > MAX_TRANSLATIONS_PER_ENTRY) {
                throw new IllegalArgumentException("Component cache entry is invalid.");
            }
            translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
        }
    }

    private record LoadedFile(
            int schemaVersion,
            Map<String, Entry> entries,
            Map<String, Entry> entityTemplates
    ) {
    }
}
