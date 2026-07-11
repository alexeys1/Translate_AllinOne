package com.cedarxuesong.translate_allinone.utils.cache;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

final class CacheFileSaveSupport {
    static final int MOVE_ATTEMPT_LIMIT = 5;
    static final long RETRY_DELAY_BASE_MILLIS = 50L;

    private CacheFileSaveSupport() {
    }

    static void replaceWithRetry(Path tempPath, Path targetPath) throws IOException {
        Objects.requireNonNull(tempPath, "tempPath");
        Objects.requireNonNull(targetPath, "targetPath");

        boolean useAtomicMove = true;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPT_LIMIT; attempt++) {
            try {
                moveReplacing(tempPath, targetPath, useAtomicMove);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                useAtomicMove = false;
                try {
                    moveReplacing(tempPath, targetPath, false);
                    return;
                } catch (IOException fallbackFailure) {
                    fallbackFailure.addSuppressed(e);
                    lastFailure = fallbackFailure;
                }
            } catch (IOException e) {
                lastFailure = e;
            }

            if (attempt < MOVE_ATTEMPT_LIMIT && isRetryableMoveFailure(lastFailure)) {
                sleepBeforeRetry(retryDelayMillis(attempt), lastFailure);
                continue;
            }
            throw lastFailure;
        }

        throw lastFailure;
    }

    private static void moveReplacing(Path tempPath, Path targetPath, boolean atomic) throws IOException {
        if (atomic) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isRetryableMoveFailure(IOException failure) {
        return failure instanceof AccessDeniedException;
    }

    private static long retryDelayMillis(int completedAttempt) {
        return RETRY_DELAY_BASE_MILLIS << (completedAttempt - 1);
    }

    private static void sleepBeforeRetry(
            long delayMillis,
            IOException retryCause
    ) throws IOException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while retrying cache file replacement.", e);
            interrupted.addSuppressed(retryCause);
            throw interrupted;
        }
    }
}
