package com.alexeys.translate_allinone.utils.llmapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmRequestLifecycleTest {
    @AfterEach
    void cancelRequests() {
        LlmRequestLifecycle.cancelActiveRequests();
        Thread.interrupted();
    }

    @Test
    void deadlineCancelsNonStreamingTransport() {
        CompletableFuture<String> transport = new CompletableFuture<>();
        List<Runnable> deadlines = new ArrayList<>();
        CompletableFuture<Integer> result = LlmRequestLifecycle.track(
                transport,
                String::length,
                deadlines::add
        );

        deadlines.get(0).run();

        CompletionException error = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(LLMApiException.class, error.getCause());
        assertTrue(transport.isCancelled());
    }

    @Test
    void globalCancellationClosesStreamingTransport() {
        AtomicBoolean closed = new AtomicBoolean();
        List<Runnable> deadlines = new ArrayList<>();
        LlmRequestLifecycle.StreamingRequest request = LlmRequestLifecycle.startStreamingRequest(deadlines::add);
        Stream<String> stream = request.attach(Stream.of("chunk").onClose(() -> closed.set(true)));

        LlmRequestLifecycle.cancelActiveRequests();

        assertTrue(closed.get());
        assertThrows(java.util.concurrent.CancellationException.class, () -> stream.iterator().hasNext());
    }

    @Test
    void cancellingResultCancelsNonStreamingTransport() {
        CompletableFuture<String> transport = new CompletableFuture<>();
        List<Runnable> deadlines = new ArrayList<>();
        CompletableFuture<Integer> result = LlmRequestLifecycle.track(
                transport,
                String::length,
                deadlines::add
        );

        result.cancel(true);

        assertTrue(transport.isCancelled());
    }

    @Test
    void cancellingMappedFutureCancelsSource() {
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<Integer> mapped = LlmRequestLifecycle.map(source, String::length);

        mapped.cancel(true);

        assertTrue(source.isCancelled());
    }

    @Test
    void normalStreamingExhaustionClosesTransportWithoutInterruptingConsumer() {
        AtomicBoolean closed = new AtomicBoolean();
        List<Runnable> deadlines = new ArrayList<>();
        LlmRequestLifecycle.StreamingRequest request = LlmRequestLifecycle.startStreamingRequest(deadlines::add);
        Stream<String> stream = request.attach(Stream.of("chunk").onClose(() -> closed.set(true)));

        assertEquals(List.of("chunk"), stream.toList());

        assertTrue(closed.get());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void guardedStreamClosesSourceWhenMappingFails() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<Integer> stream = LlmRequestLifecycle.guard(
                Stream.of("not-a-number")
                        .map(Integer::parseInt)
                        .onClose(() -> closed.set(true))
        );

        assertThrows(NumberFormatException.class, stream::toList);

        assertTrue(closed.get());
    }

    @Test
    void closingAfterConsumerFailureClosesStreamingTransport() {
        AtomicBoolean closed = new AtomicBoolean();
        List<Runnable> deadlines = new ArrayList<>();
        LlmRequestLifecycle.StreamingRequest request = LlmRequestLifecycle.startStreamingRequest(deadlines::add);

        try (Stream<String> stream = request.attach(Stream.of("chunk").onClose(() -> closed.set(true)))) {
            assertThrows(IllegalStateException.class, () -> stream.forEach(chunk -> {
                throw new IllegalStateException("consumer failed");
            }));
        }

        assertTrue(closed.get());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void globalCancellationInvalidatesCapturedEpoch() {
        long requestEpoch = LlmRequestLifecycle.currentEpoch();
        AtomicBoolean invoked = new AtomicBoolean();

        LlmRequestLifecycle.cancelActiveRequests();

        assertThrows(java.util.concurrent.CancellationException.class, () ->
                LlmRequestLifecycle.invoke(requestEpoch, () -> {
                    invoked.set(true);
                    return null;
                })
        );
        assertFalse(invoked.get());
    }

    @Test
    void streamingDeadlineBeforeAttachClosesLateBody() {
        AtomicBoolean closed = new AtomicBoolean();
        List<Runnable> deadlines = new ArrayList<>();
        LlmRequestLifecycle.StreamingRequest request = LlmRequestLifecycle.startStreamingRequest(deadlines::add);

        deadlines.get(0).run();
        Stream<String> stream = request.attach(Stream.of("chunk").onClose(() -> closed.set(true)));

        assertTrue(closed.get());
        assertThrows(LLMApiException.class, () -> stream.iterator().hasNext());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
