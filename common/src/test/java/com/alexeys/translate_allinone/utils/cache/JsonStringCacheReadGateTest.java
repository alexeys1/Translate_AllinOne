package com.alexeys.translate_allinone.utils.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStringCacheReadGateTest {

    private static final String KEY = "target=Chinese:Hello world, this is a test";

    @TempDir
    Path tempDir;

    private TestJsonCache cache;

    @BeforeEach
    void setUp() {
        cache = new TestJsonCache(tempDir);
    }

    @Test
    void acceptsGoodCachedTranslationForPrefixedKey() {
        cache.updateTranslations(Map.of(KEY, "你好世界，这是一个测试"));

        LookupResult result = cache.peek(KEY);

        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals("你好世界，这是一个测试", result.translation());
    }

    @Test
    void rejectsSourceEchoUsingPrefixedKeySourceExtraction() {
        cache.updateTranslations(Map.of(KEY, "Hello world, this is a test"));

        LookupResult result = cache.peek(KEY);

        assertEquals(TranslationStatus.ERROR, result.status());
        assertTrue(result.errorMessage().contains("SOURCE_COPY"));
        assertEquals(1, cache.readGate().invalidationCount());
    }

    private static class TestJsonCache extends JsonStringTranslationCacheService {
        TestJsonCache(Path directory) {
            super(
                    directory.resolve("cache.json"),
                    false,
                    "test-save",
                    "test",
                    (path, label) -> {
                    }
            );
        }

        @Override
        protected String readGateTargetLanguage() {
            return "Chinese";
        }

        @Override
        protected String readGateSourceText(String key) {
            int separator = key.indexOf(':');
            return separator >= 0 && separator + 1 < key.length() ? key.substring(separator + 1) : key;
        }
    }
}