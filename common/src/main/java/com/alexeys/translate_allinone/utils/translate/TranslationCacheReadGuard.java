package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TranslationCacheReadGuard {

    private static final int MAX_REMEMBERED_KEYS = 8192;

    private final Set<String> acceptedKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong invalidationCount = new AtomicLong();

    public LookupResult check(
            String identityKey,
            String sourceText,
            String targetLanguage,
            LookupResult lookup,
            Supplier<Boolean> stillTranslated,
            Consumer<String> invalidator
    ) {
        if (lookup == null || lookup.status() != TranslationStatus.TRANSLATED) {
            return lookup;
        }
        if (targetLanguage == null || targetLanguage.isBlank()) {
            return lookup;
        }
        String guardKey = identityKey == null || identityKey.isBlank() ? sourceText : identityKey;
        if (acceptedKeys.contains(guardKey)) {
            return lookup;
        }
        String candidate = lookup.translation();
        TranslationContentVerdict verdict = TranslationContentGate.evaluate(
                TranslationMode.TRANSLATE,
                sourceText == null ? "" : sourceText,
                candidate,
                targetLanguage
        );
        if (verdict.accepted()) {
            remember(guardKey);
            return lookup;
        }
        synchronized (this) {
            if (acceptedKeys.contains(guardKey)) {
                return lookup;
            }
            if (stillTranslated != null && !stillTranslated.get()) {
                return new LookupResult(
                        TranslationStatus.ERROR,
                        null,
                        "Cached translation rejected: " + verdict.reason()
                );
            }
            invalidationCount.incrementAndGet();
            if (invalidator != null) {
                invalidator.accept(guardKey);
            }
            return new LookupResult(
                    TranslationStatus.ERROR,
                    null,
                    "Cached translation rejected: " + verdict.reason()
            );
        }
    }

    public long invalidationCount() {
        return invalidationCount.get();
    }

    public void remember(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (acceptedKeys.size() >= MAX_REMEMBERED_KEYS) {
            acceptedKeys.clear();
        }
        acceptedKeys.add(key);
    }

    public void reset() {
        acceptedKeys.clear();
        invalidationCount.set(0L);
    }
}