package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DictionaryHotReloadManager {
    private static final long RELOAD_DEBOUNCE_MILLIS = 750L;
    private static final Object LOCK = new Object();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static WatchService watchService;
    private static Thread watchThread;
    private static ScheduledExecutorService reloadExecutor;
    private static ScheduledFuture<?> scheduledReload;

    private DictionaryHotReloadManager() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        Path dictionaryDirectory = WynncraftDictionaryInstaller.resolveConfigDictionaryDirectory();
        if (dictionaryDirectory == null) {
            STARTED.set(false);
            Translate_AllinOne.LOGGER.warn("Dictionary hot reload directory is unavailable.");
            return;
        }

        WatchService createdWatchService = null;
        try {
            Files.createDirectories(dictionaryDirectory);
            createdWatchService = FileSystems.getDefault().newWatchService();
            dictionaryDirectory.register(
                    createdWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            WatchService activeWatchService = createdWatchService;
            synchronized (LOCK) {
                watchService = activeWatchService;
                reloadExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "translate_allinone-dictionary-hot-reload");
                    thread.setDaemon(true);
                    return thread;
                });
                watchThread = new Thread(
                        () -> watchDictionaryDirectory(activeWatchService),
                        "translate_allinone-dictionary-watch"
                );
                watchThread.setDaemon(true);
                watchThread.start();
            }

            Runtime.getRuntime().addShutdownHook(new Thread(
                    DictionaryHotReloadManager::stop,
                    "translate_allinone-dictionary-hot-reload-shutdown"
            ));
            Translate_AllinOne.LOGGER.info("Dictionary hot reload is watching {}", dictionaryDirectory);
        } catch (IOException | RuntimeException e) {
            STARTED.set(false);
            closeQuietly(createdWatchService);
            Translate_AllinOne.LOGGER.warn(
                    "Failed to start dictionary hot reload for {}",
                    dictionaryDirectory,
                    e
            );
        }
    }

    public static void stop() {
        if (!STARTED.getAndSet(false)) {
            return;
        }

        WatchService serviceToClose;
        ScheduledExecutorService executorToStop;
        Thread threadToInterrupt;
        synchronized (LOCK) {
            if (scheduledReload != null) {
                scheduledReload.cancel(false);
                scheduledReload = null;
            }
            serviceToClose = watchService;
            executorToStop = reloadExecutor;
            threadToInterrupt = watchThread;
            watchService = null;
            reloadExecutor = null;
            watchThread = null;
        }

        closeQuietly(serviceToClose);
        if (executorToStop != null) {
            executorToStop.shutdownNow();
        }
        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }
    }

    private static void watchDictionaryDirectory(WatchService service) {
        while (STARTED.get() && !Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (RuntimeException e) {
                Translate_AllinOne.LOGGER.warn("Dictionary hot reload watcher failed.", e);
                break;
            }

            boolean shouldReload = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    shouldReload = true;
                    continue;
                }
                Object context = event.context();
                if (context instanceof Path changedPath && isJsonDictionaryFile(changedPath)) {
                    shouldReload = true;
                }
            }

            boolean valid = key.reset();
            if (shouldReload) {
                requestReload();
            }
            if (!valid) {
                Translate_AllinOne.LOGGER.warn("Dictionary hot reload watch key is no longer valid.");
                break;
            }
        }
    }

    static void requestReload() {
        ScheduledExecutorService executor;
        synchronized (LOCK) {
            executor = reloadExecutor;
            if (!STARTED.get() || executor == null || executor.isShutdown()) {
                return;
            }
            if (scheduledReload != null) {
                scheduledReload.cancel(false);
            }
            scheduledReload = executor.schedule(
                    DictionaryHotReloadManager::reloadDictionaries,
                    RELOAD_DEBOUNCE_MILLIS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static void reloadDictionaries() {
        if (!STARTED.get()) {
            return;
        }

        try {
            WynnSharedDictionaryService.getInstance().loadAll();
            Translate_AllinOne.LOGGER.info("Dictionary files changed, reloaded local dictionaries.");
        } catch (RuntimeException e) {
            Translate_AllinOne.LOGGER.error("Failed to hot reload dictionary files.", e);
        }
    }

    private static boolean isJsonDictionaryFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static void closeQuietly(WatchService service) {
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (IOException ignored) {
        }
    }
}
