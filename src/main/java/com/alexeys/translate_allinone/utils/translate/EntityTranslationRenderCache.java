package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

final class EntityTranslationRenderCache {
    private static final long DEFAULT_TTL_MILLIS = 1_000L;
    private static final int DEFAULT_MAX_ENTRIES = 1_024;

    private final long ttlMillis;
    private final int maxEntries;
    private final Map<Key, Entry> cache;

    EntityTranslationRenderCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_MILLIS);
    }

    EntityTranslationRenderCache(int maxEntries, long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, EntityTranslationRenderCache.Entry> eldest) {
                return size() > EntityTranslationRenderCache.this.maxEntries;
            }
        };
    }

    synchronized ComponentRenderTranslationSupport.TranslationResult get(Key key) {
        if (key == null) {
            return null;
        }
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAtMillis()) {
            cache.remove(key);
            return null;
        }
        return entry.result();
    }

    synchronized void put(Key key, ComponentRenderTranslationSupport.TranslationResult result) {
        put(key, result, ttlMillis);
    }

    synchronized void put(Key key, ComponentRenderTranslationSupport.TranslationResult result, long ttlMillis) {
        if (key == null || result == null) {
            return;
        }
        cache.put(key, new Entry(result, System.currentTimeMillis() + ttlMillis));
    }

    synchronized void remove(Key key) {
        if (key != null) {
            cache.remove(key);
        }
    }

    synchronized void clear() {
        cache.clear();
    }

    synchronized int size() {
        return cache.size();
    }

    record Key(
            Component component,
            ComponentTranslationRoute route,
            String context,
            String targetLanguage,
            String policyVersion
    ) {
        Key {
            context = context == null || context.isBlank() ? route.wireName() : context.trim();
            targetLanguage = targetLanguage == null ? "" : targetLanguage.trim();
            policyVersion = policyVersion == null || policyVersion.isBlank() ? "1" : policyVersion.trim();
        }
    }

    private record Entry(ComponentRenderTranslationSupport.TranslationResult result, long expiresAtMillis) {
    }
}
