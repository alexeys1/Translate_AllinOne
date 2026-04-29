package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class WynntilsTaskTrackerTextCache {
    private static final String CACHE_LABEL = "wynncraft_quest_translate_cache.json";

    public enum TranslationStatus {
        TRANSLATED,
        IN_PROGRESS,
        PENDING,
        ERROR,
        NOT_CACHED
    }

    public record CacheStats(int translated, int total) {}

    public record LookupResult(TranslationStatus status, String translation, String errorMessage) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CACHE_FILE_NAME = CACHE_LABEL;
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
        Thread thread = new Thread(r, "translate_allinone-" + CACHE_LABEL + "-save");
        thread.setDaemon(true);
        return thread;
    });

    private WynntilsTaskTrackerTextCache() {
        this(resolveDefaultCachePath(), true);
    }

    WynntilsTaskTrackerTextCache(Path cacheFilePath) {
        this(cacheFilePath, false);
    }

    WynntilsTaskTrackerTextCache(Path cacheFilePath, boolean passiveBackupEnabled) {
        this.cacheFilePath = cacheFilePath;
        this.passiveBackupEnabled = passiveBackupEnabled;
    }

    public static WynntilsTaskTrackerTextCache getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void load() {
        runtimeState.resetForLoad();
        persistence.resetForLoad();

        if (!Files.exists(cacheFilePath)) {
            Translate_AllinOne.LOGGER.info("{} file not found, a new one will be created upon saving.", CACHE_LABEL);
            return;
        }

        try (var reader = Files.newBufferedReader(cacheFilePath, StandardCharsets.UTF_8)) {
            @SuppressWarnings("unchecked")
            Map<String, String> loaded = GSON.fromJson(reader, ConcurrentHashMap.class);
            if (loaded != null) {
                loaded.forEach((key, value) -> {
                    if (key != null && value != null && !value.isEmpty()) {
                        runtimeState.templateCache().put(key, value);
                    }
                });
            }
            Translate_AllinOne.LOGGER.info(
                    "Successfully loaded {} {} entries.",
                    runtimeState.templateCache().size(),
                    CACHE_LABEL);
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.error("Failed to load {}.", CACHE_LABEL, e);
        }
    }

    public synchronized void save() {
        if (!persistence.beginSave()) {
            return;
        }

        try {
            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(runtimeState.templateCache(), writer);
            }

            try {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (passiveBackupEnabled) {
                CacheBackupManager.maybeBackup(cacheFilePath, CACHE_LABEL);
            }
            persistence.finishSave();
            Translate_AllinOne.LOGGER.info(
                    "Successfully saved {} {} entries.",
                    runtimeState.templateCache().size(),
                    CACHE_LABEL);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.error("Failed to save {}.", CACHE_LABEL, e);
        }
    }

    public LookupResult lookupOrQueue(String originalTemplate) {
        if (originalTemplate == null || originalTemplate.isBlank()) {
            return new LookupResult(TranslationStatus.NOT_CACHED, "", null);
        }
        return toLookupResult(runtimeState.lookupOrQueue(originalTemplate));
    }

    public LookupResult peek(String originalTemplate) {
        if (originalTemplate == null || originalTemplate.isBlank()) {
            return new LookupResult(TranslationStatus.NOT_CACHED, "", null);
        }
        return toLookupResult(runtimeState.peek(originalTemplate));
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

    public synchronized void releaseInProgress(Set<String> keys) {
        runtimeState.queues().releaseInProgress(keys);
    }

    public synchronized void updateTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        runtimeState.updateTranslations(translations);
        persistence.markDirty();
        scheduleSave();
    }

    public synchronized int forceRefresh(Iterable<String> originalTemplates) {
        if (originalTemplates == null) {
            return 0;
        }

        List<String> refreshKeys = new ArrayList<>();
        for (String originalTemplate : originalTemplates) {
            if (originalTemplate != null && !originalTemplate.isBlank()) {
                refreshKeys.add(originalTemplate);
            }
        }
        int refreshedCount = runtimeState.forceRefresh(refreshKeys);

        if (refreshedCount > 0) {
            persistence.markDirty();
            scheduleSave();
        }

        return refreshedCount;
    }

    public Set<String> getErroredKeys() {
        return runtimeState.copyErroredKeys();
    }

    public synchronized CacheStats getCacheStats() {
        return new CacheStats((int) runtimeState.translatedCount(), runtimeState.totalCount());
    }

    public synchronized void requeueFromError(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeState.queues().requeueFromError(key);
    }

    public synchronized void requeueFailed(Set<String> failedKeys, String errorMessage) {
        if (failedKeys == null || failedKeys.isEmpty()) {
            return;
        }
        runtimeState.queues().markErrored(failedKeys, errorMessage, "Unknown translation error");
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

    private static Path resolveDefaultCachePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_FILE_NAME);
    }

    private static final class Holder {
        private static final WynntilsTaskTrackerTextCache INSTANCE = new WynntilsTaskTrackerTextCache();
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
