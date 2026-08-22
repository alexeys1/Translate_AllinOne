package com.alexeys.translate_allinone.utils.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationQueueCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void clearsTranslationWorkWithoutDeletingCachedTranslations() {
        ChatOutputTranslationCache cache = new ChatOutputTranslationCache(
                tempDir.resolve("chat_output_translate_cache.json")
        );
        cache.load();
        cache.updateTranslations(Map.of("cached", "stored"));
        cache.lookupOrQueue("failed");
        List<String> batch = cache.drainAllPendingItems();
        cache.submitBatchForTranslation(batch);
        cache.markAsInProgress(batch);
        cache.requeueFailed(Set.copyOf(batch), "failure");
        cache.lookupOrQueue("pending");

        cache.clearTranslationQueue();

        assertTrue(cache.getErroredKeys().isEmpty());
        assertTrue(cache.drainAllPendingItems().isEmpty());
        assertEquals(TranslationStatus.NOT_CACHED, cache.peek("failed").status());
        assertEquals(TranslationStatus.NOT_CACHED, cache.peek("pending").status());
        assertEquals(TranslationStatus.TRANSLATED, cache.peek("cached").status());
    }
}
