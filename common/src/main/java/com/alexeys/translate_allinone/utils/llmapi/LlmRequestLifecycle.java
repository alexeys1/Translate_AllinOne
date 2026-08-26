package com.alexeys.translate_allinone.utils.llmapi;

import java.time.Duration;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class LlmRequestLifecycle {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final Set<ActiveRequest> ACTIVE_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong REQUEST_EPOCH = new AtomicLong();
    private static final ThreadLocal<Long> REQUEST_CONTEXT_EPOCH = new ThreadLocal<>();
    private static final Consumer<Runnable> DEFAULT_DEADLINE_SCHEDULER = action ->
            CompletableFuture.delayedExecutor(REQUEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(action);

    private LlmRequestLifecycle() {
    }

    public static Duration requestTimeout() {
        return REQUEST_TIMEOUT;
    }

    public static long currentEpoch() {
        return REQUEST_EPOCH.get();
    }

    public static boolean isCurrent(long epoch) {
        return REQUEST_EPOCH.get() == epoch;
    }

    public static <T> T invoke(long epoch, Supplier<T> supplier) {
        java.util.Objects.requireNonNull(supplier);
        if (!isCurrent(epoch)) {
            throw new CancellationException("Translation request was cancelled");
        }
        Long previous = REQUEST_CONTEXT_EPOCH.get();
        REQUEST_CONTEXT_EPOCH.set(epoch);
        try {
            if (!isCurrent(epoch)) {
                throw new CancellationException("Translation request was cancelled");
            }
            return supplier.get();
        } finally {
            if (previous == null) {
                REQUEST_CONTEXT_EPOCH.remove();
            } else {
                REQUEST_CONTEXT_EPOCH.set(previous);
            }
        }
    }

    public static <T, R> CompletableFuture<R> track(
            CompletableFuture<T> transportFuture,
            Function<T, R> responseMapper
    ) {
        return track(transportFuture, responseMapper, DEFAULT_DEADLINE_SCHEDULER);
    }

    static <T, R> CompletableFuture<R> track(
            CompletableFuture<T> transportFuture,
            Function<T, R> responseMapper,
            Consumer<Runnable> deadlineScheduler
    ) {
        AsyncRequest<T, R> request = new AsyncRequest<>(transportFuture, responseMapper, expectedEpoch());
        request.start(deadlineScheduler);
        return request.result();
    }

    public static <T, R> CompletableFuture<R> map(
            CompletableFuture<T> source,
            Function<T, R> mapper
    ) {
        java.util.Objects.requireNonNull(source);
        java.util.Objects.requireNonNull(mapper);
        CompletableFuture<R> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (result.isDone()) {
                return;
            }
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            try {
                result.complete(mapper.apply(value));
            } catch (Throwable mappingError) {
                result.completeExceptionally(mappingError);
            }
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                source.cancel(true);
            }
        });
        return result;
    }

    public static <T> Stream<T> guard(Stream<T> source) {
        java.util.Objects.requireNonNull(source);
        Iterator<T> delegate = source.iterator();
        Iterator<T> managed = new Iterator<>() {
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (finished) {
                    return false;
                }
                try {
                    boolean hasNext = delegate.hasNext();
                    if (!hasNext) {
                        finished = true;
                    }
                    return hasNext;
                } catch (RuntimeException | Error error) {
                    source.close();
                    throw error;
                }
            }

            @Override
            public T next() {
                if (finished) {
                    throw new java.util.NoSuchElementException();
                }
                try {
                    return delegate.next();
                } catch (RuntimeException | Error error) {
                    source.close();
                    throw error;
                }
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(managed, 0), false)
                .onClose(source::close);
    }

    public static <T> void consume(Stream<T> source, Consumer<T> consumer) {
        java.util.Objects.requireNonNull(source);
        java.util.Objects.requireNonNull(consumer);
        try (source) {
            source.forEach(consumer);
        }
    }

    public static StreamingRequest startStreamingRequest() {
        return startStreamingRequest(DEFAULT_DEADLINE_SCHEDULER);
    }

    static StreamingRequest startStreamingRequest(Consumer<Runnable> deadlineScheduler) {
        StreamingRequest request = new StreamingRequest(Thread.currentThread(), expectedEpoch());
        request.start(deadlineScheduler);
        request.throwIfStopped(null);
        return request;
    }

    public static void cancelActiveRequests() {
        REQUEST_EPOCH.incrementAndGet();
        for (ActiveRequest request : Set.copyOf(ACTIVE_REQUESTS)) {
            request.cancel();
        }
    }

    private interface ActiveRequest {
        void cancel();
    }

    private static long expectedEpoch() {
        Long epoch = REQUEST_CONTEXT_EPOCH.get();
        return epoch == null ? currentEpoch() : epoch;
    }

    private enum State {
        ACTIVE,
        COMPLETED,
        TIMED_OUT,
        CANCELLED
    }

    private static final class AsyncRequest<T, R> implements ActiveRequest {
        private final CompletableFuture<T> transportFuture;
        private final Function<T, R> responseMapper;
        private final long requestEpoch;
        private final CompletableFuture<R> result = new CompletableFuture<>();
        private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

        private AsyncRequest(CompletableFuture<T> transportFuture, Function<T, R> responseMapper, long requestEpoch) {
            this.transportFuture = java.util.Objects.requireNonNull(transportFuture);
            this.responseMapper = java.util.Objects.requireNonNull(responseMapper);
            this.requestEpoch = requestEpoch;
        }

        private void start(Consumer<Runnable> deadlineScheduler) {
            ACTIVE_REQUESTS.add(this);
            if (!isCurrent(requestEpoch)) {
                cancel();
                return;
            }
            java.util.Objects.requireNonNull(deadlineScheduler).accept(this::timeout);
            transportFuture.whenComplete((value, error) -> {
                if (!state.compareAndSet(State.ACTIVE, State.COMPLETED)) {
                    return;
                }
                ACTIVE_REQUESTS.remove(this);
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                try {
                    result.complete(responseMapper.apply(value));
                } catch (Throwable mappingError) {
                    result.completeExceptionally(mappingError);
                }
            });
            result.whenComplete((value, error) -> {
                if (result.isCancelled()) {
                    cancel();
                }
            });
        }

        private CompletableFuture<R> result() {
            return result;
        }

        private void timeout() {
            if (!state.compareAndSet(State.ACTIVE, State.TIMED_OUT)) {
                return;
            }
            ACTIVE_REQUESTS.remove(this);
            transportFuture.cancel(true);
            result.completeExceptionally(new LLMApiException("Translation request timed out"));
        }

        @Override
        public void cancel() {
            if (!state.compareAndSet(State.ACTIVE, State.CANCELLED)) {
                return;
            }
            ACTIVE_REQUESTS.remove(this);
            transportFuture.cancel(true);
            result.cancel(true);
        }
    }

    public static final class StreamingRequest implements ActiveRequest {
        private final AtomicReference<Thread> consumerThread;
        private final long requestEpoch;
        private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
        private final AtomicReference<Stream<?>> responseBody = new AtomicReference<>();

        private StreamingRequest(Thread ownerThread, long requestEpoch) {
            this.consumerThread = new AtomicReference<>(ownerThread);
            this.requestEpoch = requestEpoch;
        }

        private void start(Consumer<Runnable> deadlineScheduler) {
            ACTIVE_REQUESTS.add(this);
            if (!isCurrent(requestEpoch)) {
                cancel();
                return;
            }
            java.util.Objects.requireNonNull(deadlineScheduler).accept(this::timeout);
        }

        public <T> Stream<T> attach(Stream<T> body) {
            java.util.Objects.requireNonNull(body);
            if (!responseBody.compareAndSet(null, body)) {
                throw new IllegalStateException("Streaming response body is already attached");
            }
            if (state.get() != State.ACTIVE) {
                body.close();
            }

            Iterator<T> delegate = state.get() == State.ACTIVE
                    ? body.iterator()
                    : java.util.Collections.emptyIterator();
            Iterator<T> managed = new Iterator<>() {
                private boolean finished;

                @Override
                public boolean hasNext() {
                    if (finished) {
                        return false;
                    }
                    consumerThread.set(Thread.currentThread());
                    throwIfStopped(null);
                    try {
                        boolean hasNext = delegate.hasNext();
                        if (!hasNext) {
                            finished = true;
                            throwIfStopped(null);
                        }
                        return hasNext;
                    } catch (RuntimeException error) {
                        throw finishFailure(error);
                    }
                }

                @Override
                public T next() {
                    if (finished) {
                        throw new java.util.NoSuchElementException();
                    }
                    consumerThread.set(Thread.currentThread());
                    throwIfStopped(null);
                    try {
                        return delegate.next();
                    } catch (RuntimeException error) {
                        throw finishFailure(error);
                    }
                }
            };

            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(managed, 0), false)
                    .onClose(this::complete);
        }

        public RuntimeException cancellationFailure(Throwable cause) {
            State current = state.get();
            if (current == State.TIMED_OUT || current == State.CANCELLED) {
                return stoppedFailure(current, cause);
            }
            complete();
            return null;
        }

        private void timeout() {
            if (!state.compareAndSet(State.ACTIVE, State.TIMED_OUT)) {
                return;
            }
            ACTIVE_REQUESTS.remove(this);
            closeResponseBody();
            interruptConsumer();
        }

        @Override
        public void cancel() {
            if (!state.compareAndSet(State.ACTIVE, State.CANCELLED)) {
                return;
            }
            ACTIVE_REQUESTS.remove(this);
            closeResponseBody();
            interruptConsumer();
        }

        private void complete() {
            if (state.compareAndSet(State.ACTIVE, State.COMPLETED)) {
                ACTIVE_REQUESTS.remove(this);
                closeResponseBody();
            }
        }

        private void throwIfStopped(Throwable cause) {
            State current = state.get();
            if (current == State.TIMED_OUT || current == State.CANCELLED) {
                throw stoppedFailure(current, cause);
            }
        }

        private RuntimeException finishFailure(RuntimeException error) {
            State current = state.get();
            if (current == State.TIMED_OUT || current == State.CANCELLED) {
                return stoppedFailure(current, error);
            }
            complete();
            return error;
        }

        private RuntimeException stoppedFailure(State current, Throwable cause) {
            if (Thread.currentThread() == consumerThread.get()) {
                Thread.interrupted();
            }
            if (current == State.TIMED_OUT) {
                return new LLMApiException("Translation streaming request timed out", cause);
            }
            CancellationException cancellation = new CancellationException("Translation streaming request was cancelled");
            if (cause != null) {
                cancellation.initCause(cause);
            }
            return cancellation;
        }

        private void closeResponseBody() {
            Stream<?> body = responseBody.get();
            if (body != null) {
                body.close();
            }
        }

        private void interruptConsumer() {
            Thread thread = consumerThread.get();
            if (thread != null && thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
    }
}
