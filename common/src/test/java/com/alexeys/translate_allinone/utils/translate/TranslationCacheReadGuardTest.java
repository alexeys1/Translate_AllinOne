package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCacheReadGuardTest {

    private TranslationCacheReadGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TranslationCacheReadGuard();
    }

    @Test
    void acceptsGoodCachedTranslationAndRemembersIt() {
        LookupResult translated = translated("你好世界，这是一个测试");
        AtomicInteger invalidations = new AtomicInteger();
        AtomicBoolean stillTranslated = new AtomicBoolean(true);

        LookupResult first = guard.check(
                "key", "Hello world, this is a test", "Chinese", translated,
                stillTranslated::get, key -> invalidations.incrementAndGet()
        );
        LookupResult second = guard.check(
                "key", "Hello world, this is a test", "Chinese", translated,
                stillTranslated::get, key -> invalidations.incrementAndGet()
        );

        assertEquals(TranslationStatus.TRANSLATED, first.status());
        assertEquals(TranslationStatus.TRANSLATED, second.status());
        assertEquals(0, invalidations.get());
    }

    @Test
    void invalidatesRejectedCachedTranslationAndReturnsError() {
        LookupResult translated = translated("Hello world, this is a test");
        AtomicInteger invalidations = new AtomicInteger();
        AtomicBoolean stillTranslated = new AtomicBoolean(true);

        LookupResult rejected = guard.check(
                "key", "Hello world, this is a test", "Chinese", translated,
                stillTranslated::get, key -> {
                    stillTranslated.set(false);
                    invalidations.incrementAndGet();
                }
        );

        assertEquals(TranslationStatus.ERROR, rejected.status());
        assertTrue(rejected.errorMessage().contains("SOURCE_COPY"));
        assertEquals(1, invalidations.get());
        assertEquals(1, guard.invalidationCount());
    }

    @Test
    void concurrentReadersInvalidateOnlyOnce() throws Exception {
        String source = "Hello world, this is a long source sentence";
        LookupResult translated = translated(source);
        AtomicInteger invalidations = new AtomicInteger();
        AtomicInteger staleReads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        AtomicBoolean stillTranslated = new AtomicBoolean(true);
        for (int index = 0; index < 8; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                LookupResult result = guard.check(
                        "key", source, "Chinese", translated,
                        stillTranslated::get,
                        key -> {
                            stillTranslated.set(false);
                            invalidations.incrementAndGet();
                        }
                );
                if (result.status() == TranslationStatus.ERROR) {
                    staleReads.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(1, invalidations.get());
        assertEquals(8, staleReads.get());
    }

    @Test
    void acceptsTemplateOnlyCachedValuesWithoutInvalidation() {
        LookupResult translated = translated("3/10");
        AtomicInteger invalidations = new AtomicInteger();

        LookupResult result = guard.check(
                "key", "3/10", "Chinese", translated,
                () -> true, key -> invalidations.incrementAndGet()
        );

        assertEquals(TranslationStatus.TRANSLATED, result.status());
        assertEquals(0, invalidations.get());
    }

    @Test
    void ignoresNonTranslatedLookups() {
        LookupResult error = new LookupResult(TranslationStatus.ERROR, null, "previous");
        LookupResult pending = new LookupResult(TranslationStatus.PENDING, null, null);
        AtomicInteger invalidations = new AtomicInteger();

        LookupResult errorResult = guard.check(
                "key", "Hello world", "Chinese", error,
                () -> true, key -> invalidations.incrementAndGet()
        );
        LookupResult pendingResult = guard.check(
                "key", "Hello world", "Chinese", pending,
                () -> true, key -> invalidations.incrementAndGet()
        );

        assertEquals(TranslationStatus.ERROR, errorResult.status());
        assertEquals(TranslationStatus.PENDING, pendingResult.status());
        assertEquals(0, invalidations.get());
    }

    @Test
    void allowsFreshTranslationAfterInvalidationAndReEvaluation() {
        AtomicBoolean stillTranslated = new AtomicBoolean(true);
        LookupResult rejected = guard.check(
                "key", "Hello world, this is a test", "Chinese",
                translated("Hello world, this is a test"),
                stillTranslated::get,
                key -> stillTranslated.set(false)
        );
        assertEquals(TranslationStatus.ERROR, rejected.status());

        stillTranslated.set(true);
        LookupResult fresh = guard.check(
                "key", "Hello world, this is a test", "Chinese",
                translated("你好世界，这是一个测试"),
                stillTranslated::get,
                key -> stillTranslated.set(false)
        );

        assertEquals(TranslationStatus.TRANSLATED, fresh.status());
        assertEquals("你好世界，这是一个测试", fresh.translation());
    }

    @Test
    void resetsStateAndCounters() {
        AtomicBoolean stillTranslated = new AtomicBoolean(true);
        guard.check(
                "key", "Hello world, this is a test", "Chinese",
                translated("Hello world, this is a test"),
                stillTranslated::get,
                key -> stillTranslated.set(false)
        );
        assertEquals(1, guard.invalidationCount());

        guard.reset();
        assertEquals(0, guard.invalidationCount());
    }

    private static LookupResult translated(String translation) {
        return new LookupResult(TranslationStatus.TRANSLATED, translation, null);
    }
}