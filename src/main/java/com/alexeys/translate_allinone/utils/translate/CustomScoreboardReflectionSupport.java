package com.alexeys.translate_allinone.utils.translate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class CustomScoreboardReflectionSupport {
    private static final String NON_TEXT_FALLBACK = "fail";
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "Translate_AllinOne/CustomScoreboardCompat"
    );
    private static final Set<FailureKey> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            return discover(type);
        }
    };

    private CustomScoreboardReflectionSupport() {
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
            logFailureOnce(originalLine.getClass(), "CustomScoreboard ScoreboardLine API is incompatible.", null);
            return originalLine;
        }
        try {
            Object alignment = accessors.getAlignment().invoke(originalLine);
            Object rawBlank = accessors.isBlank().invoke(originalLine);
            boolean isBlank = rawBlank instanceof Boolean value && value;
            Object actions = accessors.getActions().invoke(originalLine);
            Object translatedLine = accessors.componentConstructor().newInstance(
                    translatedComponent,
                    alignment,
                    isBlank
            );
            return accessors.copyDefault().invoke(
                    null,
                    translatedLine,
                    null,
                    null,
                    false,
                    actions,
                    0b0111,
                    null
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    originalLine.getClass(),
                    "Failed to copy a translated CustomScoreboard line; preserving the original.",
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
            logFailureOnce(line.getClass(), "CustomScoreboard ScoreboardLine API is incompatible.", null);
            return line;
        }

        try {
            Object rawComponent = accessors.getComponent().invoke(line);
            if (!(rawComponent instanceof Component component)
                    || NON_TEXT_FALLBACK.equals(component.getString())) {
                return line;
            }
            ExternalScoreboardTranslationSupport.Result result =
                    ExternalScoreboardTranslationSupport.translate(
                            component,
                            ExternalScoreboardTranslationSupport.Source.CUSTOM_SCOREBOARD,
                            hidesVanillaScoreboard
                    );
            if (result.component() == component) {
                return line;
            }
            return copyWithTranslatedComponent(line, result.component());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    line.getClass(),
                    "Failed to translate a CustomScoreboard line; preserving the original.",
                    e
            );
            return line;
        }
    }

    private static Accessors discover(Class<?> type) {
        try {
            Method getComponent = type.getMethod("getComponent");
            Method getAlignment = type.getMethod("getAlignment");
            Method isBlank = type.getMethod("isBlank");
            Method getActions = type.getMethod("getActions");

            Constructor<?> componentConstructor = null;
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 3
                        && Component.class.isAssignableFrom(parameters[0])
                        && parameters[2] == boolean.class) {
                    componentConstructor = constructor;
                    break;
                }
            }

            Method copyDefault = null;
            for (Method method : type.getMethods()) {
                if (method.getName().equals("copy$default")
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 7) {
                    copyDefault = method;
                    break;
                }
            }
            return new Accessors(
                    getComponent,
                    getAlignment,
                    isBlank,
                    getActions,
                    componentConstructor,
                    copyDefault
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(type, "Failed to inspect CustomScoreboard ScoreboardLine.", e);
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

    private record Accessors(
            Method getComponent,
            Method getAlignment,
            Method isBlank,
            Method getActions,
            Constructor<?> componentConstructor,
            Method copyDefault
    ) {
        private boolean available() {
            return getComponent != null
                    && getAlignment != null
                    && isBlank != null
                    && getActions != null
                    && componentConstructor != null
                    && copyDefault != null;
        }

        private static Accessors unavailableAccessors() {
            return new Accessors(null, null, null, null, null, null);
        }
    }

    private record FailureKey(Class<?> type, String category) {
    }
}
