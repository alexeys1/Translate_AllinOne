package com.alexeys.translate_allinone.utils.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class NumericVersion implements Comparable<NumericVersion> {
    private static final Pattern NUMERIC_VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+)*$");

    private final String raw;
    private final List<Integer> segments;

    private NumericVersion(String raw, List<Integer> segments) {
        this.raw = raw;
        this.segments = segments;
    }

    public static NumericVersion parse(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }

        int suffixIdx = firstSuffixIndex(normalized);
        if (suffixIdx > 0) {
            normalized = normalized.substring(0, suffixIdx);
        }

        if (!NUMERIC_VERSION_PATTERN.matcher(normalized).matches()) {
            return null;
        }

        String[] parts = normalized.split("\\.");
        List<Integer> parsed = new ArrayList<>(parts.length);
        try {
            for (String part : parts) {
                parsed.add(Integer.parseInt(part));
            }
        } catch (NumberFormatException ignored) {
            return null;
        }

        return new NumericVersion(normalized, Collections.unmodifiableList(parsed));
    }

    private static int firstSuffixIndex(String value) {
        int dashIdx = value.indexOf('-');
        int plusIdx = value.indexOf('+');
        if (dashIdx < 0) {
            return plusIdx;
        }
        if (plusIdx < 0) {
            return dashIdx;
        }
        return Math.min(dashIdx, plusIdx);
    }

    public String raw() {
        return raw;
    }

    @Override
    public int compareTo(NumericVersion other) {
        int max = Math.max(this.segments.size(), other.segments.size());
        for (int i = 0; i < max; i++) {
            int left = i < this.segments.size() ? this.segments.get(i) : 0;
            int right = i < other.segments.size() ? other.segments.get(i) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return raw;
    }
}
