package com.cedarxuesong.translate_allinone.utils;

import java.util.Locale;
import java.util.concurrent.CompletionException;

public final class TranslateExceptionUtils {

    private TranslateExceptionUtils() {}

    public static Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static boolean isInternalPostprocessError(Throwable throwable) {
        Throwable root = unwrapThrowable(throwable);
        if (root == null || root.getMessage() == null) {
            return false;
        }
        String message = root.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("internalpostprocesserror")
                || message.contains("internal error during model post-process")
                || message.contains("translation failed due to internal error");
    }
}
