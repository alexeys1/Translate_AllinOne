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
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
public final class ComponentTranslationStore {
    public static final int SCHEMA_VERSION = 4;
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int DEFAULT_MAX_ENTRIES = Integer.MAX_VALUE;
    private static final int MAX_TRANSLATIONS_PER_ENTRY = ComponentJsonLimits.DEFAULT.maxTextUnits();
    private static final int MAX_TRANSLATION_CHARS = ComponentJsonLimits.DEFAULT.maxTranslationChars();
    private static final int MAX_ID_CHARS = 256;
    private static final long SAVE_DEBOUNCE_MILLIS = 1_500L;
    private static final long SAVE_RETRY_BASE_MILLIS = 1_000L;
    private static final int SAVE_RETRY_LIMIT = 3;
    private static final long LOAD_RETRY_BASE_MILLIS = 50L;
    private static final int LOAD_RETRY_LIMIT = 3;
    private static final int MAX_MEMORY_ONLY_ENTRIES = 128;
    private static final String RECOVERY_DIRECTORY_NAME = "corrupt";
    private static final int MAX_RECOVERY_ARTIFACTS = 5;
    private static final AtomicLong RECOVERY_SEQUENCE = new AtomicLong();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;
    private final ComponentCacheModule module;
    private final boolean passiveBackupEnabled;
    private final long maxFileBytes;
    private final int maxEntries;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> entityTemplates = new LinkedHashMap<>();
    private final Map<String, Entry> memoryEntries = new LinkedHashMap<>();
    private final Map<String, Entry> memoryEntityTemplates = new LinkedHashMap<>();
    private final ComponentTranslationValidator validator = new ComponentTranslationValidator();
    private final ScheduledExecutorService saveExecutor;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private boolean dirty;
    private PersistenceState persistenceState = PersistenceState.HEALTHY;
    private int saveRetryAttempts;
    private ScheduledFuture<?> scheduledSave;

    ComponentTranslationStore(Path cacheDirectory, ComponentCacheModule module, boolean passiveBackupEnabled) {
        this(cacheDirectory, module, passiveBackupEnabled, MAX_FILE_BYTES, DEFAULT_MAX_ENTRIES);
    }

    ComponentTranslationStore(
            Path cacheDirectory,
            ComponentCacheModule module,
            boolean passiveBackupEnabled,
            long maxFileBytes,
            int maxEntries
    ) {
        if (cacheDirectory == null) {
            throw new IllegalArgumentException("Component cache directory is required.");
        }
        if (module == null) {
            throw new IllegalArgumentException("Component cache module is required.");
        }
        if (maxFileBytes < 1L || maxEntries < 1) {
            throw new IllegalArgumentException("Component cache limits must be positive.");
        }
        this.path = cacheDirectory.resolve(module.fileName());
        this.module = module;
        this.passiveBackupEnabled = passiveBackupEnabled;
        this.maxFileBytes = maxFileBytes;
        this.maxEntries = maxEntries;
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "translate_allinone-component-" + module.wireName() + "-save");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void load() {
        cancelScheduledSave();
        entries.clear();
        entityTemplates.clear();
        memoryEntries.clear();
        memoryEntityTemplates.clear();
        dirty = false;
        persistenceState = PersistenceState.HEALTHY;
        saveRetryAttempts = 0;
        if (!Files.exists(path)) {
            Translate_AllinOne.LOGGER.info("Component {} cache file not found; starting empty.", module.wireName());
            return;
        }

        try {
            LoadedFile loaded = readFileWithRetry(path);
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
            recoverFromLoadFailure(error);
        }
    }

    public synchronized Lookup lookup(ComponentTranslationPreparedRequest request) {
        if (request == null || !module.owns(request.document().route())) {
            return new Lookup(Status.MISS, "", null);
        }
        String key = request.identity().key();
        Entry entry = findEntry(entries, memoryEntries, key);
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
        Entry entry = findEntry(entityTemplates, memoryEntityTemplates, key);
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
        if (request == null || response == null || !module.owns(request.document().route())) {
            return false;
        }
        ComponentTranslationResponse validated = validator.validate(request.document(), response);
        Entry entry = new Entry(request.document().route(), request.identity().binding(), validated.translations());
        String entryKey = request.identity().key();
        boolean changed = !entry.equals(entries.put(entryKey, entry));
        memoryEntries.remove(entryKey);
        String templateKey = null;
        if (module == ComponentCacheModule.ENTITY && request.document().route() == ComponentTranslationRoute.ENTITY_NAME) {
            ComponentTranslationTemplateIdentity templateIdentity = ComponentTranslationTemplateIdentity.createEntityName(
                    request.document(),
                    request.targetLanguage()
            );
            Entry templateEntry = new Entry(ComponentTranslationRoute.ENTITY_NAME, templateIdentity.binding(), validated.translations());
            templateKey = templateIdentity.key();
            changed |= !templateEntry.equals(entityTemplates.put(templateKey, templateEntry));
            memoryEntityTemplates.remove(templateKey);
        }
        try {
            changed |= enforcePersistentLimits(entryKey, templateKey).changed();
        } catch (RuntimeException error) {
            moveToMemory(memoryEntries, entryKey, entries.remove(entryKey));
            if (templateKey != null) {
                moveToMemory(memoryEntityTemplates, templateKey, entityTemplates.remove(templateKey));
            }
            changed = true;
            Translate_AllinOne.LOGGER.warn(
                    "Component {} cache entry will remain memory-only because it exceeds the persistence budget.",
                    module.wireName(),
                    error
            );
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
        cancelScheduledSave();
        if (!dirty || persistenceState != PersistenceState.HEALTHY) {
            return;
        }

        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            byte[] serialized = enforcePersistentLimits(null, null).bytes();
            Files.write(tempPath, serialized);
            readFile(tempPath);
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            saveRetryAttempts = 0;
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
        } catch (IOException | RuntimeException error) {
            deleteTempFile(tempPath, error);
            handleSaveFailure(error);
        }
    }

    synchronized void ensureInitialized() {
        if (persistenceState == PersistenceState.HEALTHY && !Files.exists(path)) {
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

    synchronized int memoryEntryCount() {
        return memoryEntries.size();
    }

    synchronized int memoryEntityTemplateCount() {
        return memoryEntityTemplates.size();
    }

    public synchronized PersistenceState persistenceState() {
        return persistenceState;
    }

    public synchronized CacheStats getCacheStats() {
        int total = entries.size() + entityTemplates.size() + memoryEntries.size() + memoryEntityTemplates.size();
        return new CacheStats(total, total);
    }

    synchronized void endSession() {
        cancelScheduledSave();
        saveRetryAttempts = 0;
    }

    private LoadedFile readFile(Path file) throws IOException {
        if (Files.size(file) > maxFileBytes) {
            throw new IOException("Component cache file exceeds the size limit.");
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
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

    private LoadedFile readFileWithRetry(Path file) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= LOAD_RETRY_LIMIT; attempt++) {
            try {
                return readFile(file);
            } catch (AccessDeniedException error) {
                lastFailure = error;
                if (attempt < LOAD_RETRY_LIMIT) {
                    sleepBeforeRetry(LOAD_RETRY_BASE_MILLIS << (attempt - 1), error);
                    continue;
                }
            }
        }
        throw lastFailure;
    }

    private SalvagedFile readFileWithSalvage(Path file) throws IOException {
        if (Files.size(file) > maxFileBytes) {
            throw new IOException("Component cache file exceeds the size limit.");
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cache root");
            Integer schemaVersion = null;
            String cacheModule = null;
            Map<String, Entry> loadedEntries = null;
            Map<String, Entry> loadedEntityTemplates = null;
            int skippedEntries = 0;
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
                    case "entries" -> {
                        SalvagedEntries salvaged = readEntriesWithSalvage(reader, false);
                        loadedEntries = salvaged.entries();
                        skippedEntries += salvaged.skippedEntries();
                    }
                    case "entity_templates" -> {
                        SalvagedEntries salvaged = readEntriesWithSalvage(reader, true);
                        loadedEntityTemplates = salvaged.entries();
                        skippedEntries += salvaged.skippedEntries();
                    }
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
            if (loadedEntries == null || skippedEntries == 0) {
                throw new IOException("Component cache does not contain salvageable entry failures.");
            }
            if (module != ComponentCacheModule.ENTITY && loadedEntityTemplates != null) {
                throw new IOException("Only the entity Component cache can contain entity_templates.");
            }
            return new SalvagedFile(
                    loadedEntries,
                    loadedEntityTemplates == null ? Map.of() : loadedEntityTemplates,
                    skippedEntries
            );
        }
    }

    private SalvagedEntries readEntriesWithSalvage(JsonReader reader, boolean entityTemplateEntries) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Component cache entries");
        Map<String, Entry> loaded = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        int skippedEntries = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!keys.add(key) || !isSha256(key) || loaded.size() >= maxEntries) {
                reader.skipValue();
                skippedEntries++;
                continue;
            }
            Entry entry = readSalvagedEntry(reader, entityTemplateEntries);
            if (entry == null) {
                skippedEntries++;
                continue;
            }
            loaded.put(key, entry);
        }
        reader.endObject();
        return new SalvagedEntries(loaded, skippedEntries);
    }

    private Entry readSalvagedEntry(JsonReader reader, boolean entityTemplateEntry) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        boolean valid = true;
        ComponentTranslationRoute route = null;
        String binding = null;
        Map<String, String> translations = null;
        Set<String> fields = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                reader.skipValue();
                valid = false;
                continue;
            }
            switch (field) {
                case "route" -> {
                    String wireName = readOptionalString(reader);
                    if (wireName == null) {
                        valid = false;
                        continue;
                    }
                    try {
                        route = ComponentTranslationRoute.fromWireName(wireName);
                    } catch (IllegalArgumentException error) {
                        valid = false;
                    }
                }
                case "binding" -> {
                    binding = readOptionalString(reader);
                    if (!isSha256(binding)) {
                        valid = false;
                    }
                }
                case "translations" -> {
                    SalvagedTranslations salvaged = readTranslationsWithSalvage(reader);
                    translations = salvaged.translations();
                    valid &= salvaged.valid();
                }
                default -> {
                    reader.skipValue();
                    valid = false;
                }
            }
        }
        reader.endObject();
        if (!valid || route == null || binding == null || translations == null || !module.owns(route)) {
            return null;
        }
        if (entityTemplateEntry && route != ComponentTranslationRoute.ENTITY_NAME) {
            return null;
        }
        return new Entry(route, binding, translations);
    }

    private SalvagedTranslations readTranslationsWithSalvage(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return new SalvagedTranslations(Map.of(), false);
        }
        Map<String, String> translations = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        boolean valid = true;
        int translationCount = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            String value = readOptionalString(reader);
            translationCount++;
            if (!ids.add(id)
                    || translationCount > MAX_TRANSLATIONS_PER_ENTRY
                    || id.isBlank()
                    || id.length() > MAX_ID_CHARS
                    || !isValidUtf16(id)
                    || value == null
                    || value.length() > MAX_TRANSLATION_CHARS
                    || !isValidUtf16(value)) {
                valid = false;
                continue;
            }
            translations.put(id, value);
        }
        reader.endObject();
        return new SalvagedTranslations(translations, valid);
    }

    private static String readOptionalString(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            reader.skipValue();
            return null;
        }
        return reader.nextString();
    }

    private void recoverFromLoadFailure(Throwable loadFailure) {
        persistenceState = PersistenceState.RECOVERING;
        try {
            Path backupPath = preserveCorruptFile();
            try {
                SalvagedFile salvaged = readFileWithSalvage(path);
                restoreSalvaged(salvaged);
                Translate_AllinOne.LOGGER.warn(
                        "Rebuilt Component {} cache by skipping {} invalid entry record(s); preserved original file at {}.",
                        module.wireName(),
                        salvaged.skippedEntries(),
                        backupPath
                );
                return;
            } catch (IOException | RuntimeException salvageError) {
                Translate_AllinOne.LOGGER.warn(
                        "Component {} cache is not eligible for single-entry salvage.",
                        module.wireName(),
                        salvageError
                );
            }
            List<Path> candidates = passiveBackupEnabled
                    ? CacheBackupManager.findVerifiedComponentCacheBackups(path)
                    : List.of();
            for (Path candidate : candidates) {
                try {
                    readFile(candidate);
                    restoreCandidate(candidate);
                    Translate_AllinOne.LOGGER.warn(
                            "Recovered Component {} cache from verified passive backup {} after preserving corrupt file {}.",
                            module.wireName(),
                            candidate,
                            backupPath,
                            loadFailure
                    );
                    return;
                } catch (IOException | RuntimeException recoveryError) {
                    Translate_AllinOne.LOGGER.warn(
                            "Rejected verified Component {} cache backup candidate {} during strict recovery.",
                            module.wireName(),
                            candidate,
                            recoveryError
                    );
                }
            }
            rebuildEmptyCache(backupPath, loadFailure);
        } catch (IOException | RuntimeException recoveryError) {
            entries.clear();
            entityTemplates.clear();
            persistenceState = PersistenceState.MEMORY_ONLY;
            recoveryError.addSuppressed(loadFailure);
            Translate_AllinOne.LOGGER.error(
                    "Failed to recover Component {} cache. This session will use memory-only cache data; inspect preserved cache artifacts for {}.",
                    module.wireName(),
                    path,
                    recoveryError
            );
        }
    }

    private Path preserveCorruptFile() throws IOException {
        Path backupPath = recoveryArtifactPath("corrupt-backup");
        Files.copy(path, backupPath);
        if (!MessageDigest.isEqual(fileHash(path), fileHash(backupPath))) {
            Files.deleteIfExists(backupPath);
            throw new IOException("Component cache corrupt backup hash does not match the original file.");
        }
        cleanupRecoveryArtifacts();
        return backupPath;
    }

    private void restoreCandidate(Path candidate) throws IOException {
        byte[] contents = Files.readAllBytes(candidate);
        Path tempPath = path.resolveSibling(path.getFileName() + ".recovery.tmp");
        try {
            Files.write(tempPath, contents);
            LoadedFile verified = readFile(tempPath);
            entries.clear();
            entries.putAll(verified.entries());
            entityTemplates.clear();
            entityTemplates.putAll(verified.entityTemplates());
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            persistenceState = PersistenceState.HEALTHY;
            saveRetryAttempts = 0;
        } catch (IOException | RuntimeException error) {
            deleteTempFile(tempPath, error);
            throw error;
        }
    }

    private void restoreSalvaged(SalvagedFile salvaged) throws IOException {
        entries.clear();
        entries.putAll(salvaged.entries());
        entityTemplates.clear();
        entityTemplates.putAll(salvaged.entityTemplates());
        Path tempPath = path.resolveSibling(path.getFileName() + ".recovery.tmp");
        try {
            byte[] contents = serializePersistentEntries();
            Files.write(tempPath, contents);
            readFile(tempPath);
            CacheFileSaveSupport.replaceWithRetry(tempPath, path);
            dirty = false;
            persistenceState = PersistenceState.HEALTHY;
            saveRetryAttempts = 0;
        } catch (IOException | RuntimeException error) {
            deleteTempFile(tempPath, error);
            throw error;
        }
    }

    private void rebuildEmptyCache(Path backupPath, Throwable loadFailure) throws IOException {
        Path isolatedPath = recoveryArtifactPath("corrupt-isolated");
        Files.move(path, isolatedPath);
        cleanupRecoveryArtifacts();
        entries.clear();
        entityTemplates.clear();
        memoryEntries.clear();
        memoryEntityTemplates.clear();
        dirty = true;
        persistenceState = PersistenceState.HEALTHY;
        saveRetryAttempts = 0;
        save();
        if (persistenceState != PersistenceState.HEALTHY || dirty) {
            throw new IOException("Failed to create an empty replacement Component cache file.");
        }
        Translate_AllinOne.LOGGER.warn(
                "Isolated unrecoverable Component {} cache {} after preserving backup {} and created an empty cache file.",
                module.wireName(),
                isolatedPath,
                backupPath,
                loadFailure
        );
    }

    private Path recoveryArtifactPath(String kind) throws IOException {
        Path cacheDirectory = path.getParent();
        if (cacheDirectory == null) {
            throw new IOException("Component cache directory is unavailable.");
        }
        Path recoveryDirectory = cacheDirectory.resolve(RECOVERY_DIRECTORY_NAME);
        Files.createDirectories(recoveryDirectory);
        String fileName = path.getFileName().toString();
        Path candidate;
        do {
            long identifier = System.currentTimeMillis() * 1_000L + RECOVERY_SEQUENCE.incrementAndGet();
            candidate = recoveryDirectory.resolve(fileName + "." + kind + "-" + identifier);
        } while (Files.exists(candidate));
        return candidate;
    }

    private void cleanupRecoveryArtifacts() {
        Path cacheDirectory = path.getParent();
        if (cacheDirectory == null) {
            return;
        }
        Path recoveryDirectory = cacheDirectory.resolve(RECOVERY_DIRECTORY_NAME);
        try (var files = Files.list(recoveryDirectory)) {
            List<Path> artifacts = files
                    .filter(Files::isRegularFile)
                    .filter(ComponentTranslationStore::isRecoveryArtifact)
                    .sorted(Comparator
                            .comparingLong(ComponentTranslationStore::recoveryArtifactIdentifier)
                            .thenComparing(candidate -> candidate.getFileName().toString()))
                    .toList();
            for (int index = 0; index < artifacts.size() - MAX_RECOVERY_ARTIFACTS; index++) {
                Files.deleteIfExists(artifacts.get(index));
            }
        } catch (IOException error) {
            Translate_AllinOne.LOGGER.warn(
                    "Failed to prune obsolete Component {} cache recovery artifacts under {}.",
                    module.wireName(),
                    recoveryDirectory,
                    error
            );
        }
    }

    private static boolean isRecoveryArtifact(Path candidate) {
        String fileName = candidate.getFileName().toString();
        for (ComponentCacheModule cacheModule : ComponentCacheModule.values()) {
            String backupPrefix = cacheModule.fileName() + ".corrupt-backup-";
            String isolatedPrefix = cacheModule.fileName() + ".corrupt-isolated-";
            if (fileName.startsWith(backupPrefix) || fileName.startsWith(isolatedPrefix)) {
                return recoveryArtifactIdentifier(candidate) >= 0;
            }
        }
        return false;
    }

    private static long recoveryArtifactIdentifier(Path candidate) {
        String fileName = candidate.getFileName().toString();
        int separatorIndex = fileName.lastIndexOf('-');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            return -1;
        }
        try {
            return Long.parseLong(fileName.substring(separatorIndex + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static byte[] fileHash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static void sleepBeforeRetry(long delayMillis, IOException retryCause) throws IOException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while retrying Component cache file access.", error);
            interrupted.addSuppressed(retryCause);
            throw interrupted;
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

    private PersistenceSnapshot enforcePersistentLimits(String retainedEntryKey, String retainedTemplateKey) {
        boolean changed = trimToEntryLimit(entries, retainedEntryKey);
        changed |= trimToEntryLimit(entityTemplates, retainedTemplateKey);
        byte[] serialized = serializePersistentEntries();
        while (serialized.length > maxFileBytes) {
            Eviction eviction = nextEviction(retainedEntryKey, retainedTemplateKey);
            if (eviction != null) {
                if (eviction.entityTemplate()) {
                    entityTemplates.remove(eviction.key());
                } else {
                    entries.remove(eviction.key());
                }
                changed = true;
            } else if (moveRetainedEntriesToMemory(retainedEntryKey, retainedTemplateKey)) {
                changed = true;
            } else {
                throw new IllegalStateException("Component cache limits cannot represent a valid cache document.");
            }
            serialized = serializePersistentEntries();
        }
        return new PersistenceSnapshot(serialized, changed);
    }

    private boolean trimToEntryLimit(Map<String, Entry> values, String retainedKey) {
        boolean changed = false;
        while (values.size() > maxEntries) {
            String key = earliestKey(values, retainedKey);
            if (key == null) {
                throw new IllegalStateException("Component cache entry limit cannot retain the current result.");
            }
            values.remove(key);
            changed = true;
        }
        return changed;
    }

    private Eviction nextEviction(String retainedEntryKey, String retainedTemplateKey) {
        List<Eviction> candidates = new ArrayList<>();
        for (String key : entries.keySet()) {
            if (!key.equals(retainedEntryKey)) {
                candidates.add(new Eviction(false, key));
            }
        }
        for (String key : entityTemplates.keySet()) {
            if (!key.equals(retainedTemplateKey)) {
                candidates.add(new Eviction(true, key));
            }
        }
        return candidates.stream()
                .min(Comparator.comparing(Eviction::key).thenComparing(Eviction::entityTemplate))
                .orElse(null);
    }

    private boolean moveRetainedEntriesToMemory(String retainedEntryKey, String retainedTemplateKey) {
        boolean moved = false;
        if (retainedEntryKey != null) {
            Entry entry = entries.remove(retainedEntryKey);
            if (entry != null) {
                moveToMemory(memoryEntries, retainedEntryKey, entry);
                moved = true;
            }
        }
        if (retainedTemplateKey != null) {
            Entry entry = entityTemplates.remove(retainedTemplateKey);
            if (entry != null) {
                moveToMemory(memoryEntityTemplates, retainedTemplateKey, entry);
                moved = true;
            }
        }
        return moved;
    }

    private byte[] serializePersistentEntries() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("cache_module", module.wireName());
        root.add("entries", serializeEntries(entries));
        if (module == ComponentCacheModule.ENTITY) {
            root.add("entity_templates", serializeEntries(entityTemplates));
        }
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
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
            if (loaded.size() >= maxEntries) {
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
        boolean removed = memoryEntries.remove(key) != null;
        if (entries.remove(key) != null) {
            dirty = true;
            scheduleSave();
            removed = true;
        }
        return removed;
    }

    private boolean removeEntityTemplateInternal(String key) {
        boolean removed = memoryEntityTemplates.remove(key) != null;
        if (entityTemplates.remove(key) != null) {
            dirty = true;
            scheduleSave();
            removed = true;
        }
        return removed;
    }

    private void scheduleSave() {
        scheduleSave(SAVE_DEBOUNCE_MILLIS);
    }

    private void scheduleSave(long delayMillis) {
        if (persistenceState != PersistenceState.HEALTHY || !saveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduledSave = saveExecutor.schedule(this::save, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException error) {
            saveScheduled.set(false);
            persistenceState = PersistenceState.MEMORY_ONLY;
            Translate_AllinOne.LOGGER.error(
                    "Component {} cache executor is unavailable; continuing with memory-only cache data.",
                    module.wireName(),
                    error
            );
        }
    }

    private void handleSaveFailure(Throwable error) {
        if (error instanceof AccessDeniedException && saveRetryAttempts < SAVE_RETRY_LIMIT) {
            saveRetryAttempts++;
            long delayMillis = SAVE_RETRY_BASE_MILLIS << (saveRetryAttempts - 1);
            Translate_AllinOne.LOGGER.warn(
                    "Failed to save Component {} cache; retrying persistence in {} ms (attempt {}/{}).",
                    module.wireName(),
                    delayMillis,
                    saveRetryAttempts,
                    SAVE_RETRY_LIMIT,
                    error
            );
            scheduleSave(delayMillis);
            return;
        }
        persistenceState = PersistenceState.MEMORY_ONLY;
        Translate_AllinOne.LOGGER.error(
                "Failed to save Component {} cache. The current session will continue with memory-only cache data: {}",
                module.wireName(),
                path,
                error
        );
    }

    private void deleteTempFile(Path tempPath, Throwable originalError) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException cleanupError) {
            originalError.addSuppressed(cleanupError);
        }
    }

    private void cancelScheduledSave() {
        if (scheduledSave != null) {
            scheduledSave.cancel(false);
            scheduledSave = null;
        }
        saveScheduled.set(false);
    }

    private static Entry findEntry(Map<String, Entry> persistentEntries, Map<String, Entry> memoryOnlyEntries, String key) {
        Entry entry = persistentEntries.get(key);
        return entry == null ? memoryOnlyEntries.get(key) : entry;
    }

    private static String earliestKey(Map<String, Entry> values, String retainedKey) {
        return values.keySet().stream()
                .filter(key -> !key.equals(retainedKey))
                .min(String::compareTo)
                .orElse(null);
    }

    private static void moveToMemory(Map<String, Entry> values, String key, Entry entry) {
        if (entry == null) {
            return;
        }
        values.put(key, entry);
        while (values.size() > MAX_MEMORY_ONLY_ENTRIES) {
            String discardedKey = earliestKey(values, key);
            if (discardedKey == null) {
                return;
            }
            values.remove(discardedKey);
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

    public enum PersistenceState {
        HEALTHY,
        RECOVERING,
        MEMORY_ONLY
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

    private record Eviction(boolean entityTemplate, String key) {
    }

    private record PersistenceSnapshot(byte[] bytes, boolean changed) {
    }

    private record SalvagedFile(
            Map<String, Entry> entries,
            Map<String, Entry> entityTemplates,
            int skippedEntries
    ) {
    }

    private record SalvagedEntries(Map<String, Entry> entries, int skippedEntries) {
    }

    private record SalvagedTranslations(Map<String, String> translations, boolean valid) {
    }
}
