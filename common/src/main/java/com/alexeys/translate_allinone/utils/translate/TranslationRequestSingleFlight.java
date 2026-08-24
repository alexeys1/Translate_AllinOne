package com.alexeys.translate_allinone.utils.translate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TranslationRequestSingleFlight<K, V> {
    private final ConcurrentMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    public Claim<V> acquire(K key) {
        Objects.requireNonNull(key, "key");
        CompletableFuture<V> created = new CompletableFuture<>();
        CompletableFuture<V> existing = inFlight.putIfAbsent(key, created);
        return existing == null ? new Claim<>(created, true) : new Claim<>(existing, false);
    }

    public boolean complete(K key, Claim<V> claim, V value) {
        if (claim == null || !claim.owner()) {
            return false;
        }
        inFlight.remove(key, claim.future());
        return claim.future().complete(value);
    }

    public boolean fail(K key, Claim<V> claim, Throwable error) {
        if (claim == null || !claim.owner()) {
            return false;
        }
        inFlight.remove(key, claim.future());
        return claim.future().completeExceptionally(Objects.requireNonNull(error, "error"));
    }

    public void cancelAll() {
        List<CompletableFuture<V>> requests = List.copyOf(inFlight.values());
        inFlight.clear();
        requests.forEach(request -> request.cancel(true));
    }

    public record Claim<V>(CompletableFuture<V> future, boolean owner) {
        public Claim {
            Objects.requireNonNull(future, "future");
        }
    }
}
