package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class SkyHanniScoreboardReflectionSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "Translate_AllinOne/SkyHanniScoreboardCompat"
    );
    private static final Set<FailureKey> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            return discover(type);
        }
    };

    private SkyHanniScoreboardReflectionSupport() {
    }

    public static List<?> translateLines(List<?> originalLines, boolean hidesVanillaScoreboard) {
        if (originalLines == null || originalLines.isEmpty()) {
            return originalLines;
        }

        List<Object> translatedLines = new ArrayList<>(originalLines.size());
        boolean changed = false;
        for (Object line : originalLines) {
            Object translated = translateLine(line, hidesVanillaScoreboard);
            translatedLines.add(translated);
            changed |= translated != line;
        }
        return changed ? Collections.unmodifiableList(translatedLines) : originalLines;
    }

    static Object copyWithTranslatedComponent(Object originalLine, Component translatedComponent) {
        if (originalLine == null || translatedComponent == null) {
            return originalLine;
        }
        Accessors accessors = ACCESSORS.get(originalLine.getClass());
        if (!accessors.available()) {
            logFailureOnce(originalLine.getClass(), "SkyHanni ScoreboardLine API is incompatible.", null);
            return originalLine;
        }
        try {
            String translatedDisplay = LegacyComponentTextCodec.encode(translatedComponent);
            Object alignment = accessors.getAlignment().invoke(originalLine);
            return accessors.copy().invoke(originalLine, translatedDisplay, alignment);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    originalLine.getClass(),
                    "Failed to copy a translated SkyHanni scoreboard line; preserving the original.",
                    e
            );
            return originalLine;
        }
    }

    private static Object translateLine(Object line, boolean hidesVanillaScoreboard) {
        if (line == null) {
            return null;
        }
        Accessors accessors = ACCESSORS.get(line.getClass());
        if (!accessors.available()) {
            logFailureOnce(line.getClass(), "SkyHanni ScoreboardLine API is incompatible.", null);
            return line;
        }

        try {
            Object rawDisplay = accessors.getDisplay().invoke(line);
            if (!(rawDisplay instanceof String display)) {
                return line;
            }
            Component source = LegacyComponentTextCodec.decode(display);
            ExternalScoreboardTranslationSupport.Result result =
                    ExternalScoreboardTranslationSupport.translate(
                            source,
                            ExternalScoreboardTranslationSupport.Source.SKYHANNI,
                            hidesVanillaScoreboard
                    );
            if (result.component() == source) {
                return line;
            }
            return copyWithTranslatedComponent(line, result.component());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    line.getClass(),
                    "Failed to translate a SkyHanni scoreboard line; preserving the original.",
                    e
            );
            return line;
        }
    }

    private static Accessors discover(Class<?> type) {
        try {
            Method getDisplay = type.getMethod("getDisplay");
            Method getAlignment = type.getMethod("getAlignment");
            Method copy = null;
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("copy")
                        && parameters.length == 2
                        && parameters[0] == String.class) {
                    copy = method;
                    break;
                }
            }
            return new Accessors(getDisplay, getAlignment, copy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(type, "Failed to inspect SkyHanni ScoreboardLine.", e);
            return Accessors.unavailableAccessors();
        }
    }

    private static void logFailureOnce(Class<?> type, String message, Throwable error) {
        if (!LOGGED_FAILURES.add(new FailureKey(type, message))) {
            return;
        }
        if (error == null) {
            LOGGER.warn("{} class={}", message, type.getName());
        } else {
            LOGGER.warn("{} class={}", message, type.getName(), error);
        }
    }

    private record Accessors(Method getDisplay, Method getAlignment, Method copy) {
        private boolean available() {
            return getDisplay != null && getAlignment != null && copy != null;
        }

        private static Accessors unavailableAccessors() {
            return new Accessors(null, null, null);
        }
    }

    private record FailureKey(Class<?> type, String category) {
    }
}
