package com.cedarxuesong.translate_allinone.utils.translate;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Objective-scoped prepared-document cache for the HUD render path. Entries are
 * invalidated when the objective identity changes and bounded for mutable teams.
 */
public final class ScoreboardPreparedDocumentCache {
    private static final int DEFAULT_CAPACITY = 128;

    private final int capacity;
    private final Map<EntryKey, ScoreboardEntryTemplate.Prepared> entries;
    private Object activeObjective;

    public ScoreboardPreparedDocumentCache() {
        this(DEFAULT_CAPACITY);
    }

    ScoreboardPreparedDocumentCache(int capacity) {
        this.capacity = Math.max(15, capacity);
        this.entries = new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<EntryKey, ScoreboardEntryTemplate.Prepared> eldest) {
                return size() > ScoreboardPreparedDocumentCache.this.capacity;
            }
        };
    }

    public synchronized ScoreboardEntryTemplate.Prepared prepare(
            Object objective,
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
        beginObjective(objective);
        EntryKey key = new EntryKey(
                copyOrEmpty(prefix),
                copyOrEmpty(owner),
                translateOwner,
                protectOwner,
                copyOrEmpty(suffix)
        );
        ScoreboardEntryTemplate.Prepared prepared = entries.get(key);
        if (prepared != null) {
            return prepared;
        }
        ScoreboardEntryTemplate.Prepared created = ScoreboardEntryTemplate.prepare(
                key.prefix(),
                key.owner(),
                key.translateOwner(),
                key.protectOwner(),
                key.suffix()
        );
        entries.put(key, created);
        return created;
    }

    public synchronized void beginObjective(Object objective) {
        if (activeObjective != objective) {
            activeObjective = objective;
            entries.clear();
        }
    }

    public synchronized void clear() {
        activeObjective = null;
        entries.clear();
    }

    int size() {
        synchronized (this) {
            return entries.size();
        }
    }

    private static Component copyOrEmpty(Component component) {
        return component == null ? Component.empty() : component.copy();
    }

    private record EntryKey(
            Component prefix,
            Component owner,
            boolean translateOwner,
            boolean protectOwner,
            Component suffix
    ) {
    }
}
