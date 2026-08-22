package com.alexeys.translate_allinone.utils.cache;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOutputTranslationCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsChatOutputTranslations() {
        Path cachePath = tempDir.resolve("chat_output_translate_cache.json");
        String key = "target=chinese\u001f<s0>Hello</s0>";

        ChatOutputTranslationCache cache = new ChatOutputTranslationCache(cachePath);
        cache.load();
        cache.updateTranslations(Map.of(key, "<s0>你好</s0>"));
        cache.save();

        ChatOutputTranslationCache reloaded = new ChatOutputTranslationCache(cachePath);
        reloaded.load();

        LookupResult result = reloaded.peek(key);
        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals("<s0>你好</s0>", result.translation());
        assertTrue(reloaded.getCacheStats().translated() > 0);
    }
}
