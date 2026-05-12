package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

public abstract class AbstractTranslateCache<B> {

    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    protected static final long SAVE_DEBOUNCE_MILLIS = 1500L;
    protected static final String MOD_ID = "translate_allinone";

    protected final Path cacheFilePath;
    protected final boolean passiveBackupEnabled;

    protected final Map<String, String> templateCache = new ConcurrentHashMap<>();
    protected final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    protected final LinkedBlockingDeque<String> pendingQueue = new LinkedBlockingDeque<>();
    protected final LinkedBlockingQueue<B> batchWorkQueue = new LinkedBlockingQueue<>();
    protected final Set<String> allQueuedOrInProgressKeys = ConcurrentHashMap.newKeySet();
    protected final Map<String, String> errorCache = new ConcurrentHashMap<>();

    protected final CacheRuntimeStateSupport<String, B> runtimeState;
    protected final CachePersistenceSupport persistence;
    protected final ScheduledExecutorService saveExecutor;

    protected AbstractTranslateCache(Path cacheFilePath, boolean passiveBackupEnabled, String saveThreadName) {
        this.cacheFilePath = cacheFilePath;
        this.passiveBackupEnabled = passiveBackupEnabled;
        this.runtimeState = new CacheRuntimeStateSupport<>(
                templateCache,
                new CacheKeyQueueSupport<>(pendingQueue, inProgress, batchWorkQueue, allQueuedOrInProgressKeys, errorCache)
        );
        this.persistence = new CachePersistenceSupport(SAVE_DEBOUNCE_MILLIS);
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "translate_allinone-" + saveThreadName);
            t.setDaemon(true);
            return t;
        });
    }

    protected abstract Map<String, String> loadEntries() throws IOException;

    protected Map<String, String> prepareEntriesForSave(Map<String, String> entries) {
        return entries;
    }

    protected boolean isRenderThreadAware() {
        return false;
    }

    public LookupResult lookupOrQueue(String originalTemplate) {
        if (originalTemplate == null || originalTemplate.isBlank()) {
            return new LookupResult(TranslationStatus.NOT_CACHED, "", null);
        }
        CacheRuntimeStateSupport.LookupState state = runtimeState.lookupOrQueue(originalTemplate);
        return toLookupResult(state);
    }

    public LookupResult peek(String originalTemplate) {
        if (originalTemplate == null || originalTemplate.isBlank()) {
            return new LookupResult(TranslationStatus.NOT_CACHED, "", null);
        }
        CacheRuntimeStateSupport.LookupState state = runtimeState.peek(originalTemplate);
        return toLookupResult(state);
    }

    public int forceRefresh(Iterable<String> originalTemplates) {
        return runtimeState.forceRefresh(originalTemplates);
    }

    public CacheStats getCacheStats() {
        long translated = runtimeState.translatedCount();
        int total = runtimeState.totalCount();
        return new CacheStats((int) translated, total);
    }

    public Set<String> getErroredKeys() {
        return runtimeState.copyErroredKeys();
    }

    public List<String> drainAllPendingItems() {
        return runtimeState.queues().drainAllPendingItems();
    }

    public void submitBatchForTranslation(List<String> batch) {
        @SuppressWarnings("unchecked")
        B typedBatch = (B) batch;
        runtimeState.queues().submitBatch(typedBatch);
    }

    public B takeBatchForTranslation() throws InterruptedException {
        return runtimeState.queues().takeBatch();
    }

    public void markAsInProgress(List<String> batch) {
        runtimeState.queues().markAsInProgress(batch);
    }

    public void requeueFromError(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeState.queues().requeueFromError(key);
    }

    public void releaseInProgress(Set<String> keys) {
        runtimeState.queues().releaseInProgress(keys);
    }

    public void updateTranslations(Map<String, String> translations) {
        runtimeState.updateTranslations(translations);
    }

    public void requeueFailed(Set<String> failedKeys, String errorMessage) {
        runtimeState.queues().markErrored(failedKeys, errorMessage, errorMessage);
    }

    public void load() {
        runtimeState.resetForLoad();
        persistence.resetForLoad();

        try {
            Map<String, String> entries = loadEntries();
            if (entries != null && !entries.isEmpty()) {
                runtimeState.putLoadedEntries(entries);
            }
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Failed to load cache from {}", cacheFilePath, e);
        }
    }

    public void save() {
        if (!persistence.beginSave()) {
            return;
        }

        try {
            Map<String, String> snapshot = new ConcurrentHashMap<>(templateCache);
            Map<String, String> prepared = prepareEntriesForSave(snapshot);

            Files.createDirectories(cacheFilePath.getParent());
            Path tempPath = cacheFilePath.resolveSibling(cacheFilePath.getFileName() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(prepared, writer);
            }

            try {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            persistence.finishSave();
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.error("Failed to save cache to {}", cacheFilePath, e);
        }
    }

    protected void scheduleSave() {
        CachePersistenceSupport.SaveSchedulePlan schedulePlan = persistence.planSave(isRenderThreadAware());
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SKIP_ALREADY_SCHEDULED) {
            return;
        }
        if (schedulePlan.action() == CachePersistenceSupport.SaveScheduleAction.SAVE_NOW) {
            save();
            return;
        }
        saveExecutor.schedule(this::save, schedulePlan.delayMillis(), TimeUnit.MILLISECONDS);
    }

    protected LookupResult toLookupResult(CacheRuntimeStateSupport.LookupState state) {
        return new LookupResult(toTranslationStatus(state.status()), state.translation(), state.errorMessage());
    }

    protected static TranslationStatus toTranslationStatus(CacheRuntimeStateSupport.LookupStatus status) {
        return switch (status) {
            case TRANSLATED -> TranslationStatus.TRANSLATED;
            case IN_PROGRESS -> TranslationStatus.IN_PROGRESS;
            case PENDING -> TranslationStatus.PENDING;
            case ERROR -> TranslationStatus.ERROR;
            case NOT_CACHED -> TranslationStatus.NOT_CACHED;
        };
    }

    protected static Path resolveDefaultCachePath(String cacheFileName) {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve(cacheFileName);
    }
}
