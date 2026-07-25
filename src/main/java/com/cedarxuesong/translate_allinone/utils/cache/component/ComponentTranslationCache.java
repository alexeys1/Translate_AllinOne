package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentJsonException;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationCacheKey;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationValidator;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ComponentTranslationCache {
    private static final int METADATA_CACHE_LIMIT = 1024;
    public static final String ITEM_CACHE_FILE = "component_v1_item_translate_cache.json";
    public static final String OTHER_TRANSLATIONS_CACHE_FILE = "component_v1_other_translations_translate_cache.json";
    public static final String SCOREBOARD_CACHE_FILE = "component_v1_scoreboard_translate_cache.json";

    private final Map<Namespace, ComponentTranslationCacheFile> files;
    private final ComponentTranslationJobRegistry jobs = new ComponentTranslationJobRegistry();
    private final ComponentTranslationValidator validator = new ComponentTranslationValidator();
    private final AtomicLong sessionEpoch = new AtomicLong();
    private final Map<MetadataCacheKey, ComponentTranslationCacheKey.Metadata> metadataCache =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<MetadataCacheKey, ComponentTranslationCacheKey.Metadata> eldest
                ) {
                    return size() > METADATA_CACHE_LIMIT;
                }
            });

    private ComponentTranslationCache() {
        this(CacheBackupManager.getCacheDirectory(), true);
    }

    ComponentTranslationCache(Path cacheDirectory) {
        this(cacheDirectory, false);
    }

    private ComponentTranslationCache(Path cacheDirectory, boolean passiveBackupEnabled) {
        Map<Namespace, ComponentTranslationCacheFile> configuredFiles = new EnumMap<>(Namespace.class);
        configuredFiles.put(
                Namespace.ITEM,
                new ComponentTranslationCacheFile(cacheDirectory.resolve(ITEM_CACHE_FILE), "item", passiveBackupEnabled)
        );
        configuredFiles.put(
                Namespace.OTHER_TRANSLATIONS,
                new ComponentTranslationCacheFile(cacheDirectory.resolve(OTHER_TRANSLATIONS_CACHE_FILE), "other-translations", passiveBackupEnabled)
        );
        configuredFiles.put(
                Namespace.SCOREBOARD,
                new ComponentTranslationCacheFile(cacheDirectory.resolve(SCOREBOARD_CACHE_FILE), "scoreboard", passiveBackupEnabled)
        );
        files = Map.copyOf(configuredFiles);
    }

    public static ComponentTranslationCache getInstance() {
        return Holder.INSTANCE;
    }

    public long beginSession() {
        jobs.clear();
        metadataCache.clear();
        return sessionEpoch.incrementAndGet();
    }

    public long endSession() {
        jobs.clear();
        metadataCache.clear();
        return sessionEpoch.incrementAndGet();
    }

    public long currentSessionEpoch() {
        return sessionEpoch.get();
    }

    public void load() {
        files.values().forEach(ComponentTranslationCacheFile::load);
    }

    public void save() {
        files.values().forEach(ComponentTranslationCacheFile::save);
    }

    public Lookup lookup(ComponentTranslationDocument document, String targetLanguage) {
        ComponentTranslationCacheFile file = fileFor(document.route());
        ComponentTranslationCacheKey.Metadata metadata = metadataFor(document, targetLanguage);
        if (file == null) {
            return new Lookup(Status.UNSUPPORTED, metadata.key(), null);
        }

        ComponentTranslationCacheEntry entry = file.get(metadata.key());
        if (entry == null) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.CACHE_MISS);
            return new Lookup(Status.MISS, metadata.key(), null);
        }
        if (!entry.matches(document, targetLanguage, metadata)) {
            file.remove(metadata.key());
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.CACHE_MISS);
            return new Lookup(Status.INVALID, metadata.key(), null);
        }

        try {
            ComponentTranslationResponse response = validator.validate(document, entry.toResponse());
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.CACHE_HIT);
            return new Lookup(Status.HIT, metadata.key(), response);
        } catch (ComponentJsonException e) {
            file.remove(metadata.key());
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.CACHE_MISS);
            ComponentTranslationDebugLogger.error(
                    document.route(),
                    "cache entry rejected: route={} key={} reason={}",
                    document.route().wireName(),
                    metadata.key(),
                    e.getMessage(),
                    e
            );
            return new Lookup(Status.INVALID, metadata.key(), null);
        }
    }

    public boolean put(
            ComponentTranslationDocument document,
            String targetLanguage,
            ComponentTranslationResponse response,
            long callbackSessionEpoch
    ) {
        if (callbackSessionEpoch != sessionEpoch.get()) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.STALE_SESSION);
            return false;
        }
        ComponentTranslationCacheFile file = fileFor(document.route());
        if (file == null) {
            return false;
        }
        validator.validate(document, response);
        ComponentTranslationCacheKey.Metadata metadata = metadataFor(document, targetLanguage);
        file.put(
                metadata.key(),
                ComponentTranslationCacheEntry.create(document, targetLanguage, response, metadata)
        );
        return true;
    }

    public boolean queueJob(
            ComponentTranslationDocument document,
            String targetLanguage,
            String legacyKey,
            long callbackSessionEpoch
    ) {
        if (callbackSessionEpoch != sessionEpoch.get()) {
            return false;
        }
        String key = cacheKey(document, targetLanguage);
        return jobs.registerQueued(
                key,
                new ComponentTranslationJob(document, document.route(), legacyKey, callbackSessionEpoch)
        );
    }

    public boolean markJobInFlight(String cacheKey, long callbackSessionEpoch) {
        return callbackSessionEpoch == sessionEpoch.get() && jobs.markInFlight(cacheKey, callbackSessionEpoch);
    }

    public Optional<ComponentTranslationJob> completeJob(String cacheKey, long callbackSessionEpoch) {
        if (callbackSessionEpoch != sessionEpoch.get()) {
            jobs.fail(cacheKey, callbackSessionEpoch);
            return Optional.empty();
        }
        return jobs.complete(cacheKey, callbackSessionEpoch);
    }

    public void failJob(String cacheKey, long callbackSessionEpoch) {
        jobs.fail(cacheKey, callbackSessionEpoch);
    }

    public boolean forceRefresh(ComponentTranslationDocument document, String targetLanguage) {
        ComponentTranslationCacheFile file = fileFor(document.route());
        String key = cacheKey(document, targetLanguage);
        boolean removed = file != null && file.remove(key);
        boolean markedForRequeue = jobs.requestRefresh(key);
        return removed || markedForRequeue;
    }

    public <T> DualReadResult<T> lookupWithLegacy(
            ComponentTranslationDocument document,
            String targetLanguage,
            Function<ComponentTranslationResponse, T> v1Renderer,
            Supplier<T> legacyLookup
    ) {
        Lookup lookup = lookup(document, targetLanguage);
        if (lookup.status() == Status.HIT) {
            return new DualReadResult<>(Source.V1, v1Renderer.apply(lookup.response()), lookup.cacheKey());
        }
        T legacyValue = legacyLookup == null ? null : legacyLookup.get();
        if (legacyValue != null) {
            ComponentTranslationMetrics.record(document.route(), ComponentTranslationMetrics.Outcome.LEGACY_HIT);
            return new DualReadResult<>(Source.LEGACY, legacyValue, lookup.cacheKey());
        }
        return new DualReadResult<>(Source.MISS, null, lookup.cacheKey());
    }

    public int jobCount() {
        return jobs.size();
    }

    public String cacheKey(ComponentTranslationDocument document, String targetLanguage) {
        return metadataFor(document, targetLanguage).key();
    }

    int entryCount(ComponentTranslationRoute route) {
        ComponentTranslationCacheFile file = fileFor(route);
        return file == null ? 0 : file.size();
    }

    boolean isWriteProtected(ComponentTranslationRoute route) {
        ComponentTranslationCacheFile file = fileFor(route);
        return file != null && file.isWriteProtected();
    }

    private ComponentTranslationCacheFile fileFor(ComponentTranslationRoute route) {
        Namespace namespace = namespaceFor(route);
        return namespace == null ? null : files.get(namespace);
    }

    private ComponentTranslationCacheKey.Metadata metadataFor(
            ComponentTranslationDocument document,
            String targetLanguage
    ) {
        if (document == null || targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("Document and target language are required for component cache metadata.");
        }
        MetadataCacheKey key = new MetadataCacheKey(document, targetLanguage.trim());
        ComponentTranslationCacheKey.Metadata metadata = metadataCache.get(key);
        if (metadata != null) {
            return metadata;
        }
        ComponentTranslationCacheKey.Metadata created = ComponentTranslationCacheKey.metadata(
                document,
                targetLanguage
        );
        metadataCache.put(key, created);
        return created;
    }

    private static Namespace namespaceFor(ComponentTranslationRoute route) {
        return switch (route) {
            case TOOLTIP_LINE, TOOLTIP_STRUCTURED, TOOLTIP_PARAGRAPH -> Namespace.ITEM;
            case ADVANCEMENT -> Namespace.OTHER_TRANSLATIONS;
            case SCOREBOARD -> Namespace.SCOREBOARD;
            case CHAT_OUTPUT -> null;
        };
    }

    public record Lookup(Status status, String cacheKey, ComponentTranslationResponse response) {
    }

    public record DualReadResult<T>(Source source, T value, String cacheKey) {
    }

    public enum Status {
        HIT,
        MISS,
        INVALID,
        UNSUPPORTED
    }

    public enum Source {
        V1,
        LEGACY,
        MISS
    }

    private enum Namespace {
        ITEM,
        OTHER_TRANSLATIONS,
        SCOREBOARD
    }

    private static final class MetadataCacheKey {
        private final ComponentTranslationDocument document;
        private final String targetLanguage;
        private final int hashCode;

        private MetadataCacheKey(ComponentTranslationDocument document, String targetLanguage) {
            this.document = document;
            this.targetLanguage = targetLanguage;
            this.hashCode = 31 * System.identityHashCode(document) + targetLanguage.hashCode();
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof MetadataCacheKey other
                    && document == other.document
                    && targetLanguage.equals(other.targetLanguage);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class Holder {
        private static final ComponentTranslationCache INSTANCE = new ComponentTranslationCache();
    }
}
