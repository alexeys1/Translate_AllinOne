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

    public record LookupResult(TranslationStatus status, String translation, String errorMessage) {}

    private static final WynntilsTaskTrackerTextCache INSTANCE = new WynntilsTaskTrackerTextCache();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE_NAME = CACHE_LABEL;
    private static final long SAVE_DEBOUNCE_MILLIS = 1500L;

    private final Path cacheFilePath;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingDeque<String> pendingQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingQueue<List<String>> batchWorkQueue = new LinkedBlockingQueue<>();
    private final Set<String> allQueuedOrInProgressKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> errorCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "translate_allinone-" + CACHE_LABEL + "-save");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean isDirty = false;
    private volatile long lastSaveAtMillis = 0L;
    private volatile boolean saveScheduled = false;

    private WynntilsTaskTrackerTextCache() {
        this.cacheFilePath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve(CACHE_FILE_NAME);
    }

    public static WynntilsTaskTrackerTextCache getInstance() {
        return INSTANCE;
    }

    public synchronized void load() {
        templateCache.clear();
        pendingQueue.clear();
        inProgress.clear();
        batchWorkQueue.clear();
        allQueuedOrInProgressKeys.clear();
        errorCache.clear();
        isDirty = false;
        saveScheduled = false;

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
                        templateCache.put(key, value);
                    }
                });
            }
            Translate_AllinOne.LOGGER.info(
                    "Successfully loaded {} {} entries.",
                    templateCache.size(),
                    CACHE_LABEL);
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.error("Failed to load {}.", CACHE_LABEL, e);
        }
    }

    public synchronized void save() {
        saveScheduled = false;
        if (!isDirty) {
            return;
        }

        try {
            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(templateCache, writer);
            }

            try {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            CacheBackupManager.maybeBackup(cacheFilePath, CACHE_LABEL);
            isDirty = false;
            lastSaveAtMillis = System.currentTimeMillis();
            Translate_AllinOne.LOGGER.info(
                    "Successfully saved {} {} entries.",
                    templateCache.size(),
                    CACHE_LABEL);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.error("Failed to save {}.", CACHE_LABEL, e);
        }
    }

    public LookupResult lookupOrQueue(String originalTemplate) {
        if (originalTemplate == null || originalTemplate.isBlank()) {
            return new LookupResult(TranslationStatus.NOT_CACHED, "", null);
        }

        String translation = templateCache.get(originalTemplate);
        if (translation != null && !translation.isEmpty()) {
            return new LookupResult(TranslationStatus.TRANSLATED, translation, null);
        }

        String errorMessage = errorCache.get(originalTemplate);
        if (errorMessage != null) {
            return new LookupResult(TranslationStatus.ERROR, "", errorMessage);
        }

        if (inProgress.contains(originalTemplate)) {
            return new LookupResult(TranslationStatus.IN_PROGRESS, "", null);
        }

        if (allQueuedOrInProgressKeys.add(originalTemplate)) {
            pendingQueue.offerLast(originalTemplate);
        }

        return new LookupResult(TranslationStatus.PENDING, "", null);
    }

    public List<String> drainAllPendingItems() {
        List<String> items = new ArrayList<>();
        pendingQueue.drainTo(items);
        return items;
    }

    public void submitBatchForTranslation(List<String> batch) {
        if (batch != null && !batch.isEmpty()) {
            batchWorkQueue.offer(batch);
        }
    }

    public List<String> takeBatchForTranslation() throws InterruptedException {
        return batchWorkQueue.take();
    }

    public void markAsInProgress(List<String> batch) {
        inProgress.addAll(batch);
    }

    public synchronized void releaseInProgress(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        inProgress.removeAll(keys);
        allQueuedOrInProgressKeys.removeAll(keys);
    }

    public synchronized void updateTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        templateCache.putAll(translations);
        Set<String> finishedKeys = new HashSet<>(translations.keySet());
        inProgress.removeAll(finishedKeys);
        allQueuedOrInProgressKeys.removeAll(finishedKeys);
        finishedKeys.forEach(errorCache::remove);

        isDirty = true;
        scheduleSave();
    }

    public synchronized int forceRefresh(Iterable<String> originalTemplates) {
        if (originalTemplates == null) {
            return 0;
        }

        int refreshedCount = 0;
        for (String originalTemplate : originalTemplates) {
            if (originalTemplate == null || originalTemplate.isBlank()) {
                continue;
            }

            templateCache.remove(originalTemplate);
            errorCache.remove(originalTemplate);
            inProgress.remove(originalTemplate);
            allQueuedOrInProgressKeys.remove(originalTemplate);
            while (pendingQueue.remove(originalTemplate)) {
                // Remove stale queued copies before pushing this refresh request to the front.
            }

            allQueuedOrInProgressKeys.add(originalTemplate);
            pendingQueue.offerFirst(originalTemplate);
            refreshedCount++;
        }

        if (refreshedCount > 0) {
            isDirty = true;
            scheduleSave();
        }

        return refreshedCount;
    }

    public Set<String> getErroredKeys() {
        return new HashSet<>(errorCache.keySet());
    }

    public synchronized void requeueFromError(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (errorCache.remove(key) != null) {
            allQueuedOrInProgressKeys.add(key);
            pendingQueue.offerFirst(key);
        }
    }

    public synchronized void requeueFailed(Set<String> failedKeys, String errorMessage) {
        if (failedKeys == null || failedKeys.isEmpty()) {
            return;
        }
        inProgress.removeAll(failedKeys);
        failedKeys.forEach(key -> errorCache.put(key, errorMessage == null ? "Unknown translation error" : errorMessage));
    }

    private synchronized void scheduleSave() {
        long elapsed = System.currentTimeMillis() - lastSaveAtMillis;
        if (elapsed >= SAVE_DEBOUNCE_MILLIS) {
            save();
            return;
        }

        if (saveScheduled) {
            return;
        }

        long delayMillis = Math.max(0L, SAVE_DEBOUNCE_MILLIS - elapsed);
        saveScheduled = true;
        saveExecutor.schedule(this::save, delayMillis, TimeUnit.MILLISECONDS);
    }
}
