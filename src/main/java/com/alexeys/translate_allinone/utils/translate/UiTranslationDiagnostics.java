package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class UiTranslationDiagnostics {
    private static final int EVENT_LIMIT = 2048;
    private static final int PREVIEW_LIMIT = 160;
    private static final Set<String> LOGGED_SCREENS = boundedSet();
    private static final Set<String> LOGGED_TEXT_EVENTS = boundedSet();

    private UiTranslationDiagnostics() {
    }

    static void recordScreen(String className, UiScreenAdapter adapter) {
        if (!isEnabled()) {
            return;
        }
        String resolvedClassName = className == null ? "" : className;
        String modId = adapter == null ? "unmatched" : adapter.modId();
        String backend = adapter == null ? "none" : adapter.backend().name().toLowerCase(Locale.ROOT);
        String key = resolvedClassName + '\u0000' + modId + '\u0000' + backend;
        if (LOGGED_SCREENS.add(key)) {
            Translate_AllinOne.LOGGER.info(
                    "[ScreenTranslate] screen class={} mod={} backend={}",
                    resolvedClassName,
                    modId,
                    backend
            );
        }
    }

    static void recordText(
            UiScreenAdapter adapter,
            UiTextRole role,
            String source,
            UiTextFilter.Decision decision,
            UiTranslationStatus status
    ) {
        if (!isEnabled() || adapter == null) {
            return;
        }
        String reason = decision == null || decision.reason() == null
                ? "none"
                : decision.reason().name().toLowerCase(Locale.ROOT);
        String preview = decision != null && decision.reason() == UiTextFilter.Reason.USER_INPUT
                ? "<user-input>"
                : preview(source);
        String roleName = role == null ? "option" : role.wireName();
        String statusName = status == null ? "unknown" : status.name().toLowerCase(Locale.ROOT);
        String key = adapter.screenId() + '\u0000' + roleName + '\u0000' + reason + '\u0000'
                + statusName + '\u0000' + preview;
        if (LOGGED_TEXT_EVENTS.add(key)) {
            Translate_AllinOne.LOGGER.info(
                    "[ScreenTranslate] text mod={} screen={} backend={} role={} reason={} status={} source={}",
                    adapter.modId(),
                    adapter.screenId(),
                    adapter.backend().name().toLowerCase(Locale.ROOT),
                    roleName,
                    reason,
                    statusName,
                    preview
            );
        }
    }

    static void reset() {
        LOGGED_SCREENS.clear();
        LOGGED_TEXT_EVENTS.clear();
    }

    private static boolean isEnabled() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            OtherTranslationsConfig otherTranslations = config == null ? null : config.otherTranslations;
            return otherTranslations != null
                    && otherTranslations.debug != null
                    && otherTranslations.debug.enabled;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static String preview(String source) {
        if (source == null || source.isBlank()) {
            return "<empty>";
        }
        String normalized = source.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LIMIT
                ? normalized
                : normalized.substring(0, PREVIEW_LIMIT) + "…";
    }

    private static Set<String> boundedSet() {
        Map<String, Boolean> values = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > EVENT_LIMIT;
            }
        };
        return Collections.synchronizedSet(Collections.newSetFromMap(values));
    }
}
