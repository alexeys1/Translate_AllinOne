package com.cedarxuesong.translate_allinone.utils.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 只接受纯数字版本（例如 2 / 2.0 / 2.1.3）。
 */
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

        // Strip leading 'v'/'V' prefix (e.g. "v2.5.3" → "2.5.3")
        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }

        // Strip pre-release suffix (e.g. "2.6.1-beta" → "2.6.1")
        int dashIdx = normalized.indexOf('-');
        if (dashIdx > 0) {
            normalized = normalized.substring(0, dashIdx);
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
