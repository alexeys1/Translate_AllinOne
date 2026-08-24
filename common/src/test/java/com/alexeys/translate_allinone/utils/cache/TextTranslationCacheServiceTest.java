package com.alexeys.translate_allinone.utils.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTranslationCacheServiceTest {
    @TempDir
    Path cacheRoot;

    @Test
    void migratesLegacyCacheAndPreservesQueueBehavior() throws Exception {
        Path legacyPath = cacheRoot.resolve("legacy_dialogue_cache.json");
        Path cachePath = cacheRoot.resolve("translate_cache").resolve("dialogue_cache.json");
        Files.writeString(legacyPath, "{\"cached\":\"translated\"}");
        AtomicInteger backupCalls = new AtomicInteger();

        TextTranslationCacheService cache = new TextTranslationCacheService(
                cachePath,
                true,
                "dialogue_cache.json",
                List.of("legacy_dialogue_cache.json"),
                "text-cache-test-save",
                "Wynn dialogue",
                (path, label) -> backupCalls.incrementAndGet()
        );

        cache.load();

        assertFalse(Files.exists(legacyPath));
        assertTrue(Files.isRegularFile(cachePath));
        assertEquals("translated", cache.peek("cached").translation());

        assertEquals(TranslationStatus.PENDING, cache.lookupOrQueue("pending").status());
        List<String> pending = cache.drainAllPendingItems();
        cache.submitBatchForTranslation(pending);
        assertEquals(pending, cache.takeBatchForTranslation());

        cache.updateTranslations(Map.of("new", "value"));

        assertEquals(1, backupCalls.get());
        assertEquals("value", cache.snapshotTranslations().get("new"));
    }

    @Test
    void keepsFailuresStickyUntilForceRefresh() {
        TextTranslationCacheService cache = new TextTranslationCacheService(
                cacheRoot.resolve("translate_cache").resolve("sticky.json"),
                false,
                "sticky.json",
                List.of(),
                "sticky-cache-test-save",
                "sticky translation",
                (path, label) -> {
                }
        );

        assertEquals(TranslationStatus.PENDING, cache.lookupOrQueue("key").status());
        cache.markAsInProgress(cache.drainAllPendingItems());
        cache.requeueFailed(java.util.Set.of("key"), "failed");

        assertEquals(TranslationStatus.ERROR, cache.lookupOrQueue("key").status());
        assertEquals(1, cache.forceRefresh(List.of("key")));
        assertEquals(TranslationStatus.PENDING, cache.lookupOrQueue("key").status());
    }
}
