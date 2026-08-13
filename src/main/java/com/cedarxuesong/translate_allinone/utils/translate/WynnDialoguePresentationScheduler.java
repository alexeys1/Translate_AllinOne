package com.cedarxuesong.translate_allinone.utils.translate;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;

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
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            task.run();
            return;
        }
        client.execute(task);
    }
}
