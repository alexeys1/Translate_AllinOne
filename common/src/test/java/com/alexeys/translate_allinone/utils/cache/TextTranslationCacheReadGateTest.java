package com.alexeys.translate_allinone.utils.cache;

import com.alexeys.translate_allinone.utils.translate.TranslationCacheReadGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTranslationCacheReadGateTest {

    @TempDir
    Path tempDir;

    private TestTextCache cache;

    @BeforeEach
    void setUp() {
        cache = new TestTextCache(tempDir);
    }

    @Test
    void acceptsGoodCachedTranslation() {
        String source = "Hello world, this is a test";
        String translation = "你好世界，这是一个测试";
        cache.updateTranslations(Map.of(source, translation));

        LookupResult result = cache.peek(source);

        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals(translation, result.translation());
        assertEquals(0, cache.readGate().invalidationCount());
    }

    @Test
    void rejectsSourceEchoOnReadAndInvalidatesKey() {
        String source = "Hello world, this is a test";
        cache.updateTranslations(Map.of(source, source));

        LookupResult first = cache.peek(source);

        assertEquals(TranslationStatus.ERROR, first.status());
        assertTrue(first.errorMessage().contains("SOURCE_COPY"));
        assertEquals(1, cache.readGate().invalidationCount());
        assertFalse(cache.snapshotTranslations().containsKey(source));

        LookupResult second = cache.peek(source);

        assertEquals(TranslationStatus.ERROR, second.status());
        assertEquals(1, cache.readGate().invalidationCount());
    }

    @Test
    void allowsFreshTranslationAfterInvalidation() {
        String source = "Hello world, this is a test";
        cache.updateTranslations(Map.of(source, source));
        assertEquals(TranslationStatus.ERROR, cache.peek(source).status());

        cache.updateTranslations(Map.of(source, "你好世界，这是一个测试"));

        assertEquals(TranslationStatus.TRANSLATED, cache.peek(source).status());
    }

    @Test
    void concurrentReadersInvalidateOnlyOnce() throws Exception {
        String source = "Hello world, this is a long source sentence";
        cache.updateTranslations(Map.of(source, source));
        int readers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(readers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers);
        for (int index = 0; index < readers; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                cache.peek(source);
                done.countDown();
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(1, cache.readGate().invalidationCount());
        assertEquals(TranslationStatus.ERROR, cache.peek(source).status());
    }

    @Test
    void gateIsDisabledWhenTargetLanguageIsUnavailable() {
        TestTextCache withoutTarget = new TestTextCache(tempDir.resolve("sub")) {
            @Override
            protected String readGateTargetLanguage() {
                return null;
            }
        };
        String source = "Hello world, this is a test";
        withoutTarget.updateTranslations(Map.of(source, source));

        assertEquals(TranslationStatus.TRANSLATED, withoutTarget.peek(source).status());
    }

    private static class TestTextCache extends TextTranslationCacheService {
        TestTextCache(Path directory) {
            super(
                    directory.resolve("cache.json"),
                    false,
                    "test",
                    List.of(),
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
    }
}