package com.cedarxuesong.translate_allinone.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TranslateStringUtils {

    private TranslateStringUtils() {}

    public static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    public static final int MAX_KEY_MISMATCH_BATCH_RETRIES = 1;

    public static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String truncateForLog(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = normalizeWhitespace(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public static String formatDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
    }

    public static String summarizeKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "[]";
        }
        final int limit = 8;
        List<String> sample = new ArrayList<>(limit);
        int count = 0;
        for (String key : keys) {
            if (count++ >= limit) {
                break;
            }
            sample.add(key);
        }
        if (keys.size() <= limit) {
            return sample.toString();
        }
        return sample + "...(+" + (keys.size() - limit) + ")";
    }
}
