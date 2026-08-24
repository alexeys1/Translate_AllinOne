package com.alexeys.translate_allinone.utils;

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

}
