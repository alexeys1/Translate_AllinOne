package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

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
        long startedAt = System.nanoTime();
        try {
            ScoreboardEntryTemplate.Prepared created = ScoreboardEntryTemplate.prepare(
                    key.prefix(),
                    key.owner(),
                    key.translateOwner(),
                    key.protectOwner(),
                    key.suffix()
            );
            ComponentTranslationMetrics.record(
                    created.document(),
                    ComponentTranslationMetrics.Outcome.DOCUMENT_BUILT
            );
            ComponentTranslationMetrics.recordValue(
                    created.document(),
                    ComponentTranslationMetrics.Measurement.TEXT_UNITS,
                    created.document().units().size()
            );
            if (created.document().units().isEmpty()) {
                ComponentTranslationMetrics.record(
                        created.document(),
                        ComponentTranslationMetrics.Outcome.NO_TEXT
                );
            }
            entries.put(key, created);
            return created;
        } catch (RuntimeException e) {
            ComponentTranslationMetrics.record(
                    ComponentTranslationRoute.SCOREBOARD,
                    com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy.CURRENT_VERSION,
                    ComponentTranslationMetrics.Outcome.DOCUMENT_FAILED
            );
            if (e instanceof ComponentJsonException componentError
                    && componentError.kind() == ComponentJsonException.Kind.CODEC) {
                ComponentTranslationMetrics.record(
                        ComponentTranslationRoute.SCOREBOARD,
                        com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy.CURRENT_VERSION,
                        ComponentTranslationMetrics.Outcome.CODEC_ENCODE_FAILURE
                );
            }
            throw e;
        } finally {
            ComponentTranslationMetrics.recordNanos(
                    ComponentTranslationRoute.SCOREBOARD,
                    com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy.CURRENT_VERSION,
                    ComponentTranslationMetrics.Timing.DOCUMENT_BUILD,
                    System.nanoTime() - startedAt
            );
        }
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
