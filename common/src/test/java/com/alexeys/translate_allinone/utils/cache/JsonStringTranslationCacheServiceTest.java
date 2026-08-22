package com.alexeys.translate_allinone.utils.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStringTranslationCacheServiceTest {
    @TempDir
    Path cacheRoot;

    @Test
    void persistsReloadsAndInvokesBackup() {
        Path cachePath = cacheRoot.resolve("translate_cache").resolve("chat_output_translate_cache.json");
        AtomicInteger backupCalls = new AtomicInteger();
        AtomicReference<String> backupLabel = new AtomicReference<>();
        JsonStringTranslationCacheService cache = new JsonStringTranslationCacheService(
                cachePath,
                true,
                "json-cache-test-save",
                "chat output translation",
                (path, label) -> {
                    assertEquals(cachePath, path);
                    backupLabel.set(label);
                    backupCalls.incrementAndGet();
                }
        );

        cache.load();
        cache.updateTranslations(Map.of("source", "translation"));

        assertTrue(cachePath.toFile().isFile());
        assertEquals(1, backupCalls.get());
        assertEquals("chat output translation", backupLabel.get());

        JsonStringTranslationCacheService reloaded = new JsonStringTranslationCacheService(
                cachePath,
                false,
                "json-cache-reload-test-save",
                "chat output translation",
                (path, label) -> {
                }
        );
        reloaded.load();

        assertEquals(TranslationStatus.TRANSLATED, reloaded.peek("source").status());
        assertEquals("translation", reloaded.peek("source").translation());
    }
}
