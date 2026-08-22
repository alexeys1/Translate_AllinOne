package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

final class WynnDialoguePresentationScheduler {
    private final Executor executor;
    private final AtomicLong sessionEpoch = new AtomicLong();

    WynnDialoguePresentationScheduler() {
        this(WynnDialoguePresentationScheduler::executeOnClient);
    }

    WynnDialoguePresentationScheduler(Executor executor) {
        this.executor = executor;
    }

    void executeForCurrentSession(Runnable task) {
        long capturedEpoch = sessionEpoch.get();
        executor.execute(() -> {
            if (capturedEpoch == sessionEpoch.get()) {
                task.run();
            }
        });
    }

    void invalidateSession() {
        sessionEpoch.incrementAndGet();
    }

    private static void executeOnClient(Runnable task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            task.run();
            return;
        }
        client.execute(task);
    }
}
