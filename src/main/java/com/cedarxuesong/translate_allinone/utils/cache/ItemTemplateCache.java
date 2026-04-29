package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTextMatcherSupport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ItemTemplateCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/ItemTemplateCache");
    private static final long DEV_CACHE_HOTSPOT_THRESHOLD_NANOS = 500_000L;

    public enum TranslationStatus {
        TRANSLATED,
        IN_PROGRESS,
        PENDING,
        ERROR,
        NOT_CACHED
    }

    public record CacheStats(int translated, int total) {}

    public record LookupResult(TranslationStatus status, String translation, String errorMessage) {}

    public record QueueSnapshot(
            int pending,
            int batchQueue,
            int inProgress,
            int errored,
            int queuedOrInProgress
    ) {}

    public record BatchWorkItem(
            long batchId,
            List<String> items,
            long collectedAtNanos,
            long enqueuedAtNanos
    ) {}

    private record LoadedEntries(
            Map<String, String> entries,
            int duplicateKeyCount,
            int skippedValueCount,
            int replacementCharCount,
            Charset sourceCharset
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CACHE_FILE_NAME = "item_translate_cache.json";
    private static final Charset CACHE_CHARSET = StandardCharsets.UTF_8;
    private static final long SAVE_DEBOUNCE_MILLIS = 1500L;
    private final Path cacheFilePath;
    private final boolean passiveBackupEnabled;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingDeque<String> pendingQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingQueue<BatchWorkItem> batchWorkQueue = new LinkedBlockingQueue<>();
    private final Set<String> allQueuedOrInProgressKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> errorCache = new ConcurrentHashMap<>();
    private final CacheRuntimeStateSupport<String, BatchWorkItem> runtimeState = new CacheRuntimeStateSupport<>(
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
        Thread thread = new Thread(r, "translate_allinone-item-cache-save");
        thread.setDaemon(true);
        return thread;
    });

    private ItemTemplateCache() {
        this(resolveDefaultCachePath(), true);
    }

    ItemTemplateCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    ItemTemplateCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        this.cacheFilePath = cacheFilePath;
        this.passiveBackupEnabled = passiveBackupEnabled;
    }

    public static ItemTemplateCache getInstance() {
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
                        "Successfully loaded {} item translation cache entries (in-memory total: {}).",
                        loadedCache.size(),
                        runtimeState.templateCache().size()
                );

                if (loadedEntries.duplicateKeyCount() > 0) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Detected {} duplicate item cache keys while loading. Last value was kept for each duplicate key.",
                            loadedEntries.duplicateKeyCount()
                    );
                }

                if (loadedEntries.skippedValueCount() > 0) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Skipped {} non-primitive item cache values while loading.",
                            loadedEntries.skippedValueCount()
                    );
                }

                if (!CACHE_CHARSET.equals(loadedEntries.sourceCharset())) {
                    shouldRewriteCacheFile = true;
                    Translate_AllinOne.LOGGER.warn(
                            "Loaded item cache using fallback charset {}. It will be rewritten as UTF-8.",
                        loadedEntries.sourceCharset().displayName()
                    );
                }
            } catch (IOException | RuntimeException e) {
                Translate_AllinOne.LOGGER.error("Failed to load item translation cache. Using empty in-memory cache for this session.", e);
            }
        } else {
            Translate_AllinOne.LOGGER.info("Item translation cache file not found, a new one will be created upon saving.");
        }

        persistence.finishLoad(shouldRewriteCacheFile, this::scheduleSave);
    }

    public synchronized void save() {
        long saveStartedAtNanos = System.nanoTime();
        if (!persistence.beginSave()) {
            logCacheHotspotIfDev("save-skip-clean", saveStartedAtNanos, "dirty=false");
            return;
        }

        try {
            long sanitizeStartedAtNanos = System.nanoTime();
            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");
            SanitizedCacheSnapshot sanitizedSnapshot = sanitizeForPersistence();
            long sanitizeElapsedNanos = System.nanoTime() - sanitizeStartedAtNanos;

            long writeStartedAtNanos = System.nanoTime();
            try (var writer = Files.newBufferedWriter(tempPath, CACHE_CHARSET)) {
                GSON.toJson(sanitizedSnapshot.entries(), writer);
            }
            long writeElapsedNanos = System.nanoTime() - writeStartedAtNanos;

            long moveStartedAtNanos = System.nanoTime();
            try {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            long moveElapsedNanos = System.nanoTime() - moveStartedAtNanos;

            if (sanitizedSnapshot.modifiedEntryCount() > 0) {
                runtimeState.templateCache().clear();
                runtimeState.templateCache().putAll(sanitizedSnapshot.entries());
                LOGGER.warn(
                        "Sanitized {} item cache entrie(s) containing invalid UTF-16 before saving.",
                        sanitizedSnapshot.modifiedEntryCount()
                );
            }

            long backupStartedAtNanos = System.nanoTime();
            LOGGER.info("Successfully saved {} item translation cache entries.", templateCache.size());
            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(cacheFilePath, "item translation");
            }
            long backupElapsedNanos = System.nanoTime() - backupStartedAtNanos;
            persistence.finishSave();
            logCacheSaveBreakdownIfDev(
                    saveStartedAtNanos,
                    sanitizeElapsedNanos,
                    writeElapsedNanos,
                    moveElapsedNanos,
                    backupElapsedNanos,
                    sanitizedSnapshot.modifiedEntryCount(),
                    sanitizedSnapshot.entries().size()
            );
        } catch (IOException e) {
            logCacheHotspotIfDev("save-failed", saveStartedAtNanos, "path=" + cacheFilePath);
            LOGGER.error("Failed to save item translation cache", e);
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
                        "Failed to load item cache with fallback charset {}. Using UTF-8 result.",
                        defaultCharset.displayName(),
                        defaultCharsetFailure
                );
            }
            return utf8Entries;
        }

        if (defaultCharsetEntries != null) {
            Translate_AllinOne.LOGGER.warn(
                    "Failed to load item cache with UTF-8. Falling back to charset {}.",
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
                "Failed to load item cache with both UTF-8 and fallback charset.",
                utf8Failure
        );
        if (defaultCharsetFailure != null) {
            combinedException.addSuppressed(defaultCharsetFailure);
        }
        throw combinedException;
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
                throw new JsonParseException("Item cache root is not a JSON object.");
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

    private SanitizedCacheSnapshot sanitizeForPersistence() {
        Map<String, String> sanitizedEntries = new LinkedHashMap<>(runtimeState.templateCache().size());
        int modifiedEntryCount = 0;

        for (Map.Entry<String, String> entry : runtimeState.templateCache().entrySet()) {
            String originalKey = entry.getKey();
            String originalValue = entry.getValue();
            String sanitizedKey = sanitizeUtf16(originalKey);
            String sanitizedValue = sanitizeUtf16(originalValue);
            if (!stringEquals(originalKey, sanitizedKey) || !stringEquals(originalValue, sanitizedValue)) {
                modifiedEntryCount++;
            }
            sanitizedEntries.put(sanitizedKey, sanitizedValue);
        }

        return new SanitizedCacheSnapshot(sanitizedEntries, modifiedEntryCount);
    }

    private String sanitizeUtf16(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sanitized = null;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1))) {
                    if (sanitized != null) {
                        sanitized.append(current).append(text.charAt(index + 1));
                    }
                    index += 2;
                    continue;
                }
                if (sanitized == null) {
                    sanitized = new StringBuilder(text.length());
                    sanitized.append(text, 0, index);
                }
                index++;
                continue;
            }

            if (Character.isLowSurrogate(current)) {
                if (sanitized == null) {
                    sanitized = new StringBuilder(text.length());
                    sanitized.append(text, 0, index);
                }
                index++;
                continue;
            }

            if (sanitized != null) {
                sanitized.append(current);
            }
            index++;
        }

        return sanitized == null ? text : sanitized.toString();
    }

    private boolean stringEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private record SanitizedCacheSnapshot(Map<String, String> entries, int modifiedEntryCount) {
    }

    public LookupResult lookupOrQueue(String originalTemplate) {
        return toLookupResult(runtimeState.lookupOrQueue(originalTemplate));
    }

    public LookupResult peek(String originalTemplate) {
        return toLookupResult(runtimeState.peek(originalTemplate));
    }

    public synchronized void promoteTranslation(String originalTemplate, String translation) {
        long promoteStartedAtNanos = System.nanoTime();
        if (originalTemplate == null || originalTemplate.isBlank() || translation == null || translation.isBlank()) {
            return;
        }

        int removedPendingCount = (int) pendingQueue.stream()
                .filter(originalTemplate::equals)
                .count();
        runtimeState.promoteTranslation(originalTemplate, translation);
        persistence.markDirty();
        scheduleSave();
        logCacheHotspotIfDev(
                "promoteTranslation",
                promoteStartedAtNanos,
                "key=" + truncateForLog(originalTemplate, 160)
                        + ", removedPending=" + removedPendingCount
                        + ", cacheSize=" + templateCache.size()
        );
    }

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final ItemTemplateCache INSTANCE = new ItemTemplateCache();
    }

    public synchronized int forceRefresh(Iterable<String> originalTemplates) {
        if (originalTemplates == null) {
            return 0;
        }

        int refreshedCount = 0;
        List<String> refreshKeys = new ArrayList<>();
        for (String originalTemplate : originalTemplates) {
            if (originalTemplate == null || originalTemplate.isBlank()) {
                continue;
            }
            refreshKeys.add(originalTemplate);
        }
        refreshedCount = runtimeState.forceRefresh(refreshKeys);

        if (refreshedCount > 0) {
            persistence.markDirty();
            Translate_AllinOne.LOGGER.info("Force-refreshed {} item translation cache entrie(s).", refreshedCount);
            scheduleSave();
        }

        return refreshedCount;
    }

    public synchronized CacheStats getCacheStats() {
        return new CacheStats((int) runtimeState.translatedCount(), runtimeState.totalCount());
    }

    public synchronized QueueSnapshot snapshotQueues() {
        return snapshotQueuesUnsafe();
    }

    public Set<String> getErroredKeys() {
        return runtimeState.copyErroredKeys();
    }

    public List<String> drainAllPendingItems() {
        return runtimeState.queues().drainAllPendingItems();
    }

    public void submitBatchForTranslation(long batchId, List<String> batch, long collectedAtNanos) {
        if (batch != null && !batch.isEmpty()) {
            runtimeState.queues().submitBatch(new BatchWorkItem(
                    batchId,
                    new ArrayList<>(batch),
                    collectedAtNanos,
                    System.nanoTime()
            ));
        }
    }

    public BatchWorkItem takeBatchForTranslation() throws InterruptedException {
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
        Translate_AllinOne.LOGGER.info(
                "Updated {} translations in the cache. queue={}",
                translations.size(),
                formatQueueSnapshot(snapshotQueuesUnsafe())
        );

        scheduleSave();
    }

    private synchronized void scheduleSave() {
        CachePersistenceSupport.SaveSchedulePlan schedulePlan = persistence.planSave(isRenderThread());
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SKIP_ALREADY_SCHEDULED) {
            logCacheHotspotIfDev(
                    "scheduleSave",
                    0L,
                    "mode=skip-already-scheduled, lastSaveAgoMs=" + schedulePlan.elapsedMillis()
            );
            return;
        }
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SAVE_NOW) {
            logCacheHotspotIfDev(
                    "scheduleSave",
                    0L,
                    "mode=immediate, lastSaveAgoMs=" + schedulePlan.elapsedMillis()
            );
            save();
            return;
        }
        logCacheHotspotIfDev(
                "scheduleSave",
                0L,
                "mode=async, lastSaveAgoMs=" + schedulePlan.elapsedMillis() + ", delayMs=" + schedulePlan.delayMillis()
        );
        saveExecutor.schedule(this::save, schedulePlan.delayMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void requeueFailed(Set<String> failedKeys, String errorMessage) {
        if (failedKeys == null || failedKeys.isEmpty()) {
            return;
        }
        runtimeState.queues().markErrored(failedKeys, errorMessage, errorMessage);
        LOGGER.warn(
                "Marked {} keys as errored. They will be retried later. queue={} error=\"{}\"",
                failedKeys.size(),
                formatQueueSnapshot(snapshotQueuesUnsafe()),
                truncateForLog(errorMessage, 220)
        );
    }

    private QueueSnapshot snapshotQueuesUnsafe() {
        return new QueueSnapshot(
                runtimeState.queues().pendingSize(),
                runtimeState.queues().batchQueueSize(),
                runtimeState.queues().inProgressSize(),
                runtimeState.queues().erroredSize(),
                runtimeState.queues().queuedOrInProgressSize()
        );
    }

    public static String formatQueueSnapshot(QueueSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return "pending=" + snapshot.pending()
                + ", batchQueue=" + snapshot.batchQueue()
                + ", inProgress=" + snapshot.inProgress()
                + ", errored=" + snapshot.errored()
                + ", queuedOrInProgress=" + snapshot.queuedOrInProgress();
    }

    private void logCacheSaveBreakdownIfDev(
            long saveStartedAtNanos,
            long sanitizeElapsedNanos,
            long writeElapsedNanos,
            long moveElapsedNanos,
            long backupElapsedNanos,
            int modifiedEntryCount,
            int persistedEntryCount
    ) {
        if (!shouldLogCacheTimingDev()) {
            return;
        }

        long totalElapsedNanos = System.nanoTime() - saveStartedAtNanos;
        if (!isRenderThread() && totalElapsedNanos < DEV_CACHE_HOTSPOT_THRESHOLD_NANOS) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-save] thread=\"{}\" renderThread={} totalMs={} sanitizeMs={} writeMs={} moveMs={} backupMs={} modifiedEntries={} persistedEntries={}",
                Thread.currentThread().getName(),
                isRenderThread(),
                formatDurationMillis(totalElapsedNanos),
                formatDurationMillis(sanitizeElapsedNanos),
                formatDurationMillis(writeElapsedNanos),
                formatDurationMillis(moveElapsedNanos),
                formatDurationMillis(backupElapsedNanos),
                modifiedEntryCount,
                persistedEntryCount
        );
    }

    private void logCacheHotspotIfDev(String op, long startedAtNanos, String detail) {
        if (!shouldLogCacheTimingDev()) {
            return;
        }

        long elapsedNanos = startedAtNanos <= 0L ? 0L : System.nanoTime() - startedAtNanos;
        if (!isRenderThread() && elapsedNanos < DEV_CACHE_HOTSPOT_THRESHOLD_NANOS) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-hotspot] op={} thread=\"{}\" renderThread={} elapsedMs={} detail=\"{}\"",
                op,
                Thread.currentThread().getName(),
                isRenderThread(),
                formatDurationMillis(elapsedNanos),
                truncateForLog(detail, 220)
        );
    }

    private boolean shouldLogCacheTimingDev() {
        try {
            return TooltipTextMatcherSupport.shouldLogItemCacheMigration(Translate_AllinOne.getConfig().itemTranslate);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isRenderThread() {
        return Thread.currentThread().getName().contains("Render thread");
    }

    private static String formatDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
    }

    private static String truncateForLog(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
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
