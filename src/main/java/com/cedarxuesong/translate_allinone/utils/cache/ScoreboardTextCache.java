package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScoreboardTextCache {

    public enum TranslationStatus {
        TRANSLATED,
        IN_PROGRESS,
        PENDING,
        ERROR,
        NOT_CACHED
    }

    public record CacheStats(int translated, int total) {}

    public record LookupResult(TranslationStatus status, String translation, String errorMessage) {}

    private record LoadedEntries(
            Map<String, String> entries,
            int duplicateKeyCount,
            int skippedValueCount,
            int replacementCharCount,
            Charset sourceCharset
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CACHE_FILE_NAME = "scoreboard_translate_cache.json";
    private static final Charset CACHE_CHARSET = StandardCharsets.UTF_8;
    private static final long SAVE_DEBOUNCE_MILLIS = 1500L;
    private final Path cacheFilePath;
    private final boolean passiveBackupEnabled;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingDeque<String> pendingQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingQueue<List<String>> batchWorkQueue = new LinkedBlockingQueue<>();
    private final Set<String> allQueuedOrInProgressKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> errorCache = new ConcurrentHashMap<>();
    private final CacheRuntimeStateSupport<String, List<String>> runtimeState = new CacheRuntimeStateSupport<>(
            templateCache,
            new CacheKeyQueueSupport<>(
                    pendingQueue,
                    inProgress,
                    batchWorkQueue,
                    allQueuedOrInProgressKeys,
                    errorCache
            )
    );
    private final CachePersistenceSupport persistence = new CachePersistenceSupport(SAVE_DEBOUNCE_MILLIS);
    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "translate_allinone-scoreboard-cache-save");
        thread.setDaemon(true);
        return thread;
    });

    private ScoreboardTextCache() {
        this(resolveDefaultCachePath(), true);
    }

    ScoreboardTextCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ScoreboardTextCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        this.cacheFilePath = cacheFilePath;
        this.passiveBackupEnabled = passiveBackupEnabled;
    }

    public void clearPendingAndInProgress() {
        runtimeState.queues().clearPendingAndInProgress();
    }

    public synchronized boolean isPendingQueueEmpty() {
        return runtimeState.queues().isPendingQueueEmpty();
    }

    public static ScoreboardTextCache getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void load() {
        runtimeState.resetForLoad();
        persistence.resetForLoad();
        boolean shouldRewriteCacheFile = false;

        if (Files.exists(cacheFilePath)) {
            try {
                LoadedEntries loadedEntries = loadEntriesWithBestCharset();
                Map<String, String> loadedCache = loadedEntries.entries();
                runtimeState.putLoadedEntries(loadedCache);

                Translate_AllinOne.LOGGER.info(
                        "Successfully loaded {} scoreboard translation cache entries (in-memory total: {}).",
                        loadedCache.size(),
                        runtimeState.templateCache().size()
                );

                if (loadedEntries.duplicateKeyCount() > 0) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Detected {} duplicate scoreboard cache keys while loading. Last value was kept for each duplicate key.",
                            loadedEntries.duplicateKeyCount()
                    );
                }

                if (loadedEntries.skippedValueCount() > 0) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Skipped {} non-primitive scoreboard cache values while loading.",
                            loadedEntries.skippedValueCount()
                    );
                }

                if (!CACHE_CHARSET.equals(loadedEntries.sourceCharset())) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Loaded scoreboard cache using fallback charset {}. It will be rewritten as UTF-8.",
                        loadedEntries.sourceCharset().displayName()
                    );
                }
            } catch (IOException | RuntimeException e) {
                Translate_AllinOne.LOGGER.error("Failed to load scoreboard translation cache. Using empty in-memory cache for this session.", e);
            }
        } else {
            Translate_AllinOne.LOGGER.info("Scoreboard translation cache file not found, a new one will be created upon saving.");
        }

        persistence.finishLoad(shouldRewriteCacheFile, this::scheduleSave);
    }

    public synchronized void save() {
        if (!persistence.beginSave()) {
            return;
        }

        try {
            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(tempPath, CACHE_CHARSET)) {
                GSON.toJson(runtimeState.templateCache(), writer);
            }

            try {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            Translate_AllinOne.LOGGER.info("Successfully saved {} scoreboard translation cache entries.", runtimeState.templateCache().size());
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(cacheFilePath, "scoreboard translation");
            }
            persistence.finishSave();
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.error("Failed to save scoreboard translation cache", e);
        }
    }

    private LoadedEntries loadEntriesWithBestCharset() throws IOException {
        Charset defaultCharset = Charset.defaultCharset();
        if (CACHE_CHARSET.equals(defaultCharset)) {
            return loadEntriesWithCharset(CACHE_CHARSET);
        }

        LoadedEntries utf8Entries = null;
        Exception utf8Failure = null;
        try {
            utf8Entries = loadEntriesWithCharset(CACHE_CHARSET);
        } catch (IOException | RuntimeException e) {
            utf8Failure = e;
        }

        LoadedEntries defaultCharsetEntries = null;
        Exception defaultCharsetFailure = null;
        try {
            defaultCharsetEntries = loadEntriesWithCharset(defaultCharset);
        } catch (IOException | RuntimeException e) {
            defaultCharsetFailure = e;
        }

        if (utf8Entries != null && defaultCharsetEntries != null) {
            return chooseBetterEntries(utf8Entries, defaultCharsetEntries);
        }

        if (utf8Entries != null) {
            if (defaultCharsetFailure != null) {
                Translate_AllinOne.LOGGER.warn(
                        "Failed to load scoreboard cache with fallback charset {}. Using UTF-8 result.",
                        defaultCharset.displayName(),
                        defaultCharsetFailure
                );
            }
            return utf8Entries;
        }

        if (defaultCharsetEntries != null) {
            Translate_AllinOne.LOGGER.warn(
                    "Failed to load scoreboard cache with UTF-8. Falling back to charset {}.",
                    defaultCharset.displayName(),
                    utf8Failure
            );
            return defaultCharsetEntries;
        }

        if (utf8Failure instanceof IOException utf8IoException) {
            if (defaultCharsetFailure != null) {
                utf8IoException.addSuppressed(defaultCharsetFailure);
            }
            throw utf8IoException;
        }

        RuntimeException combinedException = new RuntimeException(
                "Failed to load scoreboard cache with both UTF-8 and fallback charset.",
                utf8Failure
        );
        if (defaultCharsetFailure != null) {
            combinedException.addSuppressed(defaultCharsetFailure);
        }
        throw combinedException;
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final ScoreboardTextCache INSTANCE = new ScoreboardTextCache();
    }

    private LoadedEntries chooseBetterEntries(LoadedEntries utf8Entries, LoadedEntries defaultCharsetEntries) {
        if (defaultCharsetEntries.replacementCharCount() < utf8Entries.replacementCharCount()) {
            return defaultCharsetEntries;
        }

        if (defaultCharsetEntries.replacementCharCount() > utf8Entries.replacementCharCount()) {
            return utf8Entries;
        }

        if (defaultCharsetEntries.entries().size() > utf8Entries.entries().size()) {
            return defaultCharsetEntries;
        }

        if (defaultCharsetEntries.entries().size() < utf8Entries.entries().size()) {
            return utf8Entries;
        }

        if (defaultCharsetEntries.skippedValueCount() < utf8Entries.skippedValueCount()) {
            return defaultCharsetEntries;
        }

        if (defaultCharsetEntries.skippedValueCount() > utf8Entries.skippedValueCount()) {
            return utf8Entries;
        }

        return utf8Entries;
    }

    private LoadedEntries loadEntriesWithCharset(Charset charset) throws IOException {
        Map<String, String> loadedCache = new ConcurrentHashMap<>();
        int duplicateKeyCount = 0;
        int skippedValueCount = 0;
        int replacementCharCount = 0;

        try (JsonReader reader = new JsonReader(Files.newBufferedReader(cacheFilePath, charset))) {
            reader.setStrictness(Strictness.LENIENT);
            JsonToken rootToken = reader.peek();

            if (rootToken == JsonToken.NULL) {
                reader.nextNull();
                return new LoadedEntries(loadedCache, 0, 0, 0, charset);
            }

            if (rootToken != JsonToken.BEGIN_OBJECT) {
                throw new JsonParseException("Scoreboard cache root is not a JSON object.");
            }

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                replacementCharCount += countReplacementChars(key);

                JsonToken valueToken = reader.peek();
                if (valueToken == JsonToken.NULL) {
                    reader.nextNull();
                    skippedValueCount++;
                    continue;
                }

                if (valueToken == JsonToken.BEGIN_ARRAY || valueToken == JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    skippedValueCount++;
                    continue;
                }

                String value;
                if (valueToken == JsonToken.BOOLEAN) {
                    value = Boolean.toString(reader.nextBoolean());
                } else {
                    value = reader.nextString();
                }
                replacementCharCount += countReplacementChars(value);

                if (loadedCache.put(key, value) != null) {
                    duplicateKeyCount++;
                }
            }
            reader.endObject();
        }

        return new LoadedEntries(loadedCache, duplicateKeyCount, skippedValueCount, replacementCharCount, charset);
    }

    private int countReplacementChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\uFFFD') {
                count++;
            }
        }
        return count;
    }

    public String getOrQueue(String originalTemplate) {
        return lookupOrQueue(originalTemplate).translation();
    }

    public LookupResult lookupOrQueue(String originalTemplate) {
        return toLookupResult(runtimeState.lookupOrQueue(originalTemplate));
    }

    public LookupResult peek(String originalTemplate) {
        return toLookupResult(runtimeState.peek(originalTemplate));
    }

    public TranslationStatus getTemplateStatus(String templateKey) {
        return peek(templateKey).status();
    }

    public synchronized CacheStats getCacheStats() {
        return new CacheStats((int) runtimeState.translatedCount(), runtimeState.totalCount());
    }

    public Set<String> getErroredKeys() {
        return runtimeState.copyErroredKeys();
    }

    public String getError(String templateKey) {
        return errorCache.get(templateKey);
    }

    public List<String> drainAllPendingItems() {
        return runtimeState.queues().drainAllPendingItems();
    }

    public void submitBatchForTranslation(List<String> batch) {
        if (batch != null && !batch.isEmpty()) {
            runtimeState.queues().submitBatch(batch);
        }
    }

    public List<String> takeBatchForTranslation() throws InterruptedException {
        return runtimeState.queues().takeBatch();
    }
    
    public void markAsInProgress(List<String> batch) {
        runtimeState.queues().markAsInProgress(batch);
    }

    public synchronized void requeueFromError(String key) {
        runtimeState.queues().requeueFromError(key);
    }

    public synchronized void releaseInProgress(Set<String> keys) {
        runtimeState.queues().releaseInProgress(keys);
    }

    public synchronized void updateTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }
        runtimeState.updateTranslations(translations);
        persistence.markDirty();
        Translate_AllinOne.LOGGER.info("Updated {} scoreboard translations in the cache.", translations.size());

        scheduleSave();
    }

    private synchronized void scheduleSave() {
        CachePersistenceSupport.SaveSchedulePlan schedulePlan = persistence.planSave(false);
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SAVE_NOW) {
            save();
            return;
        }
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SCHEDULE_ASYNC) {
            saveExecutor.schedule(this::save, schedulePlan.delayMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void requeueFailed(Set<String> failedKeys, String errorMessage) {
        if (failedKeys == null || failedKeys.isEmpty()) {
            return;
        }
        runtimeState.queues().markErrored(failedKeys, errorMessage, errorMessage);
        Translate_AllinOne.LOGGER.warn("Marked {} scoreboard keys as errored. They will be retried later.", failedKeys.size());
    }

    private LookupResult toLookupResult(CacheRuntimeStateSupport.LookupState lookupState) {
        return new LookupResult(
                toTranslationStatus(lookupState.status()),
                lookupState.translation(),
                lookupState.errorMessage()
        );
    }

    private TranslationStatus toTranslationStatus(CacheRuntimeStateSupport.LookupStatus lookupStatus) {
        return switch (lookupStatus) {
            case TRANSLATED -> TranslationStatus.TRANSLATED;
            case IN_PROGRESS -> TranslationStatus.IN_PROGRESS;
            case PENDING -> TranslationStatus.PENDING;
            case ERROR -> TranslationStatus.ERROR;
            case NOT_CACHED -> TranslationStatus.NOT_CACHED;
        };
    }
}
