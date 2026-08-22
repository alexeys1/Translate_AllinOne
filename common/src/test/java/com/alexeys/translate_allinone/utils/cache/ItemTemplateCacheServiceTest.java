package com.alexeys.translate_allinone.utils.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTemplateCacheServiceTest {
    @TempDir
    Path cacheRoot;

    @Test
    void persistsReloadsAndInvokesBackup() {
        Path cachePath = cacheRoot.resolve("translate_cache").resolve("item_translate_cache.json");
        AtomicInteger backupCalls = new AtomicInteger();
        ItemTemplateCacheService cache = new ItemTemplateCacheService(
                cachePath,
                true,
                (path, label) -> {
                    assertEquals(cachePath, path);
                    assertEquals("item translation", label);
                    backupCalls.incrementAndGet();
                },
                () -> false
        );

        cache.load();
        cache.updateTranslations(Map.of("item template", "translated template"));

        assertTrue(Files.isRegularFile(cachePath));
        assertEquals(1, backupCalls.get());

        ItemTemplateCacheService reloaded = new ItemTemplateCacheService(
                cachePath,
                false,
                (path, label) -> {
                },
                () -> false
        );
        reloaded.load();

        LookupResult result = reloaded.peek("item template");
        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals("translated template", result.translation());
    }
}
