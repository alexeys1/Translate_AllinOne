package com.alexeys.translate_allinone.utils.cache;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyblockNpcTranslationCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsSkyblockNpcTranslations() {
        Path cachePath = tempDir.resolve("skyblock_npc_translate_cache.json");
        String key = "target=chinese\u001f<s0>Hello</s0>";

        SkyblockNpcTranslationCache cache = new SkyblockNpcTranslationCache(cachePath);
        cache.load();
        cache.updateTranslations(Map.of(key, "<s0>你好</s0>"));
        cache.save();

        SkyblockNpcTranslationCache reloaded = new SkyblockNpcTranslationCache(cachePath);
        reloaded.load();

        LookupResult result = reloaded.peek(key);
        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals("<s0>你好</s0>", result.translation());
        assertTrue(reloaded.getCacheStats().translated() > 0);
    }
}
