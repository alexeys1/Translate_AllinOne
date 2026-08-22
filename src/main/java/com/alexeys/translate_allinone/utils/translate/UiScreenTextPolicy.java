package com.alexeys.translate_allinone.utils.translate;

import java.util.LinkedHashSet;
import java.util.Set;

final class UiScreenTextPolicy {
    private UiScreenTextPolicy() {
    }

    static UiTextFilter.Decision evaluate(String value, UiTextRole role, boolean userInput) {
        if (userInput) {
            return UiTextFilter.evaluate(value, role, true);
        }
        String filterValue = withoutDecorativeGlyphs(value);
        UiTextFilter.Decision decision = UiTextFilter.evaluate(filterValue, role, false);
        if (decision.eligible()) {
            return new UiTextFilter.Decision(
                    true,
                    value.trim(),
                    role == null ? UiTextRole.OPTION : role,
                    null
            );
        }
        if (decision.reason() == UiTextFilter.Reason.EMPTY
                && value != null
                && !value.isBlank()
                && !decorativeGlyphs(value).isEmpty()) {
            return new UiTextFilter.Decision(false, "", UiTextRole.OPTION, UiTextFilter.Reason.NO_LETTERS);
        }
        return decision;
    }

    static Set<String> decorativeGlyphs(String value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        Set<String> glyphs = new LinkedHashSet<>();
        value.codePoints()
                .filter(UiScreenTextPolicy::isPrivateUseCodePoint)
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .forEach(glyphs::add);
        return glyphs.isEmpty() ? Set.of() : Set.copyOf(glyphs);
    }

    private static String withoutDecorativeGlyphs(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder filtered = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (isPrivateUseCodePoint(codePoint)) {
                filtered.append(' ');
            } else {
                filtered.appendCodePoint(codePoint);
            }
        });
        return filtered.toString();
    }

    private static boolean isPrivateUseCodePoint(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }
}
