package com.cedarxuesong.translate_allinone.utils.translate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class ScoreboardOverhaulReflectionSupport {
    private static final String CUSTOM_SCOREBOARD_RENDERER =
            "me.owdding.customscoreboard.feature.customscoreboard.CustomScoreboardRenderer";
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "Translate_AllinOne/ScoreboardOverhaulCompat"
    );
    private static final Set<FailureKey> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean BRIDGE_FAILURE_LOGGED = new AtomicBoolean();
    private static final ClassValue<ScoreAccessors> SCORE_ACCESSORS = new ClassValue<>() {
        @Override
        protected ScoreAccessors computeValue(Class<?> type) {
            return discoverScoreAccessors(type);
        }
    };
    private static volatile CustomScoreboardBridgeAccessors customScoreboardBridgeAccessors;

    private ScoreboardOverhaulReflectionSupport() {
    }

    public static List<?> translateScores(List<?> originalScores, boolean hidesVanillaScoreboard) {
        if (originalScores == null || originalScores.isEmpty()) {
            return originalScores;
        }
        if (!shouldTranslateForBridgeState(customScoreboardBridgeState())) {
            return originalScores;
        }

        List<Object> translatedScores = new ArrayList<>(originalScores.size());
        boolean changed = false;
        for (Object score : originalScores) {
            Object translated = translateScore(score, hidesVanillaScoreboard);
            translatedScores.add(translated);
            changed |= translated != score;
        }
        return changed ? Collections.unmodifiableList(translatedScores) : originalScores;
    }

    static Object copyWithTranslatedComponent(Object originalScore, Component translatedComponent) {
        if (originalScore == null || translatedComponent == null) {
            return originalScore;
        }
        ScoreAccessors accessors = SCORE_ACCESSORS.get(originalScore.getClass());
        if (!accessors.available()) {
            logFailureOnce(
                    originalScore.getClass(),
                    "Scoreboard Overhaul ScoreInfo API is incompatible.",
                    null
            );
            return originalScore;
        }

        try {
            Object id = accessors.getId().invoke(originalScore);
            Object value = accessors.getValue().invoke(originalScore);
            Object updatedAt = accessors.getUpdatedAt().invoke(originalScore);
            return accessors.copy().invoke(
                    originalScore,
                    id,
                    translatedComponent,
                    value,
                    updatedAt
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    originalScore.getClass(),
                    "Failed to copy a translated Scoreboard Overhaul score; preserving the original.",
                    e
            );
            return originalScore;
        }
    }

    static boolean shouldTranslateForBridgeState(CustomScoreboardBridgeState state) {
        return state == CustomScoreboardBridgeState.NOT_LOADED
                || state == CustomScoreboardBridgeState.INACTIVE;
    }

    private static Object translateScore(Object score, boolean hidesVanillaScoreboard) {
        if (score == null) {
            return null;
        }
        ScoreAccessors accessors = SCORE_ACCESSORS.get(score.getClass());
        if (!accessors.available()) {
            logFailureOnce(score.getClass(), "Scoreboard Overhaul ScoreInfo API is incompatible.", null);
            return score;
        }

        try {
            Object rawDisplayName = accessors.getDisplayName().invoke(score);
            if (!(rawDisplayName instanceof Component displayName)) {
                return score;
            }
            ExternalScoreboardTranslationSupport.Result result =
                    ExternalScoreboardTranslationSupport.translate(
                            displayName,
                            ExternalScoreboardTranslationSupport.Source.SCOREBOARD_OVERHAUL,
                            hidesVanillaScoreboard
                    );
            if (result.component() == displayName) {
                return score;
            }
            return copyWithTranslatedComponent(score, result.component());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(
                    score.getClass(),
                    "Failed to translate a Scoreboard Overhaul score; preserving the original.",
                    e
            );
            return score;
        }
    }

    private static ScoreAccessors discoverScoreAccessors(Class<?> type) {
        try {
            Method getId = type.getMethod("getId");
            Method getDisplayName = type.getMethod("getDisplayName");
            Method getValue = type.getMethod("getValue");
            Method getUpdatedAt = type.getMethod("getUpdatedAt");
            Method copy = null;
            for (Method method : type.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("copy")
                        && parameters.length == 4
                        && parameters[0] == String.class
                        && Component.class.isAssignableFrom(parameters[1])
                        && parameters[2] == int.class
                        && parameters[3] == Instant.class) {
                    copy = method;
                    break;
                }
            }
            return new ScoreAccessors(getId, getDisplayName, getValue, getUpdatedAt, copy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(type, "Failed to inspect Scoreboard Overhaul ScoreInfo.", e);
            return ScoreAccessors.unavailableAccessors();
        }
    }

    private static CustomScoreboardBridgeState customScoreboardBridgeState() {
        if (!FabricLoader.getInstance().isModLoaded("customscoreboard")) {
            return CustomScoreboardBridgeState.NOT_LOADED;
        }

        try {
            CustomScoreboardBridgeAccessors accessors = customScoreboardBridgeAccessors;
            if (accessors == null) {
                accessors = discoverCustomScoreboardBridge();
                customScoreboardBridgeAccessors = accessors;
            }
            if (!accessors.available()) {
                return CustomScoreboardBridgeState.UNKNOWN;
            }

            boolean rendersOverhaul = Boolean.TRUE.equals(
                    accessors.renderScoreboardOverhaul().invoke(accessors.instance())
            );
            boolean usesCustomLines = Boolean.TRUE.equals(
                    accessors.shouldUseCustomLines().invoke(accessors.instance())
            );
            Object rawLines = accessors.getLines().invoke(accessors.instance());
            int lineCount = rawLines instanceof List<?> lines ? lines.size() : 0;
            return rendersOverhaul && usesCustomLines && lineCount > 1
                    ? CustomScoreboardBridgeState.ACTIVE
                    : CustomScoreboardBridgeState.INACTIVE;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            customScoreboardBridgeAccessors = CustomScoreboardBridgeAccessors.unavailableAccessors();
            if (BRIDGE_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn(
                        "Unable to inspect CustomScoreboard's Overhaul bridge; "
                                + "preserving Overhaul rows to avoid double translation.",
                        e
                );
            }
            return CustomScoreboardBridgeState.UNKNOWN;
        }
    }

    private static CustomScoreboardBridgeAccessors discoverCustomScoreboardBridge()
            throws ReflectiveOperationException {
        Class<?> rendererClass = Class.forName(CUSTOM_SCOREBOARD_RENDERER);
        Field instanceField = rendererClass.getField("INSTANCE");
        Object instance = instanceField.get(null);
        Method renderScoreboardOverhaul = rendererClass.getMethod("renderScoreboardOverhaul");
        Method shouldUseCustomLines = rendererClass.getMethod("shouldUseCustomLines");
        Method getLines = rendererClass.getMethod("getLines");
        return new CustomScoreboardBridgeAccessors(
                instance,
                renderScoreboardOverhaul,
                shouldUseCustomLines,
                getLines
        );
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

    enum CustomScoreboardBridgeState {
        NOT_LOADED,
        INACTIVE,
        ACTIVE,
        UNKNOWN
    }

    private record ScoreAccessors(
            Method getId,
            Method getDisplayName,
            Method getValue,
            Method getUpdatedAt,
            Method copy
    ) {
        private boolean available() {
            return getId != null
                    && getDisplayName != null
                    && getValue != null
                    && getUpdatedAt != null
                    && copy != null;
        }

        private static ScoreAccessors unavailableAccessors() {
            return new ScoreAccessors(null, null, null, null, null);
        }
    }

    private record CustomScoreboardBridgeAccessors(
            Object instance,
            Method renderScoreboardOverhaul,
            Method shouldUseCustomLines,
            Method getLines
    ) {
        private boolean available() {
            return instance != null
                    && renderScoreboardOverhaul != null
                    && shouldUseCustomLines != null
                    && getLines != null;
        }

        private static CustomScoreboardBridgeAccessors unavailableAccessors() {
            return new CustomScoreboardBridgeAccessors(null, null, null, null);
        }
    }

    private record FailureKey(Class<?> type, String category) {
    }
}
