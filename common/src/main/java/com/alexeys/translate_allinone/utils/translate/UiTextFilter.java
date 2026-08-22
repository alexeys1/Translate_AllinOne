package com.alexeys.translate_allinone.utils.translate;

import java.util.regex.Pattern;

public final class UiTextFilter {
    private static final Pattern URL = Pattern.compile("(?i)^(?:https?://|www\\.)\\S+$");
    private static final Pattern NUMBER_OR_UNIT = Pattern.compile(
            "^[+-]?\\d+(?:[.,]\\d+)?\\s*(?:%|ms|s|min|h|d|px|秒|分钟|小时|天|格|级|点|次|个|块|米)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONFIG_KEY = Pattern.compile("^[A-Za-z0-9_.:-]+$");
    private static final Pattern KEY_BINDING = Pattern.compile(
            "^(?:CTRL|SHIFT|ALT|META|NONE|UNKNOWN|KEY_[A-Z0-9_]+|MOUSE\\d+)(?:[+ _-].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private UiTextFilter() {
    }

    public static Decision evaluate(String value, UiTextRole role, boolean userInput) {
        if (value == null || value.isBlank()) {
            return Decision.skip(Reason.EMPTY);
        }
        if (userInput) {
            return Decision.skip(Reason.USER_INPUT);
        }
        String text = value.trim();
        if (text.length() > 2048) {
            return Decision.skip(Reason.TOO_LONG);
        }
        if (containsPrivateUseCodePoint(text)) {
            return Decision.skip(Reason.DECORATIVE_GLYPH);
        }
        if (text.startsWith("/")) {
            return Decision.skip(Reason.COMMAND);
        }
        if (URL.matcher(text).matches()) {
            return Decision.skip(Reason.URL);
        }
        if (text.contains("\\")) {
            return Decision.skip(Reason.PATH);
        }
        if (NUMBER_OR_UNIT.matcher(text).matches()) {
            return Decision.skip(Reason.NUMBER);
        }
        if (KEY_BINDING.matcher(text).matches()) {
            return Decision.skip(Reason.KEY_BINDING);
        }
        if (CONFIG_KEY.matcher(text).matches()
                && (text.indexOf('.') >= 0 || text.indexOf('_') >= 0 || text.indexOf(':') >= 0)) {
            return Decision.skip(Reason.CONFIG_KEY);
        }
        if (text.codePoints().noneMatch(Character::isLetter)) {
            return Decision.skip(Reason.NO_LETTERS);
        }
        return Decision.accept(text, role == null ? UiTextRole.OPTION : role);
    }

    public static boolean containsPrivateUseCodePoint(String value) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint ->
                Character.getType(codePoint) == Character.PRIVATE_USE
                        || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                        || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                        || (codePoint >= 0x100000 && codePoint <= 0x10FFFD)
        );
    }

    public enum Reason {
        EMPTY,
        USER_INPUT,
        TOO_LONG,
        DECORATIVE_GLYPH,
        COMMAND,
        URL,
        PATH,
        NUMBER,
        KEY_BINDING,
        CONFIG_KEY,
        NO_LETTERS
    }

    public record Decision(boolean eligible, String text, UiTextRole role, Reason reason) {
        private static Decision accept(String text, UiTextRole role) {
            return new Decision(true, text, role, null);
        }

        private static Decision skip(Reason reason) {
            return new Decision(false, "", UiTextRole.OPTION, reason);
        }
    }
}

