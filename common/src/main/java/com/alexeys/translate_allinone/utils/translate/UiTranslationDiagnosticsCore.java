package com.alexeys.translate_allinone.utils.translate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UiTranslationDiagnosticsCore {
    private static final int EVENT_LIMIT = 2048;
    private static final int PREVIEW_LIMIT = 160;

    private final Set<String> loggedScreens = boundedSet();
    private final Set<String> loggedTextEvents = boundedSet();

    public ScreenEvent recordScreen(String className, String modId, String backend) {
        String resolvedClassName = className == null ? "" : className;
        String resolvedModId = modId == null ? "" : modId;
        String resolvedBackend = backend == null ? "" : backend;
        String key = resolvedClassName + '\u0000' + resolvedModId + '\u0000' + resolvedBackend;
        if (!loggedScreens.add(key)) {
            return null;
        }
        return new ScreenEvent(resolvedClassName, resolvedModId, resolvedBackend);
    }

    public TextEvent recordText(
            String modId,
            String screenId,
            String backend,
            UiTextRole role,
            UiTextFilter.Decision decision,
            UiTranslationStatus status,
            String source
    ) {
        String resolvedModId = modId == null ? "" : modId;
        String resolvedScreenId = screenId == null ? "" : screenId;
        String resolvedBackend = backend == null ? "" : backend;
        String reason = decision == null || decision.reason() == null
                ? "none"
                : decision.reason().name().toLowerCase(Locale.ROOT);
        String roleName = role == null ? "option" : role.wireName();
        String statusName = status == null ? "unknown" : status.name().toLowerCase(Locale.ROOT);
        String preview = decision != null && decision.reason() == UiTextFilter.Reason.USER_INPUT
                ? "<user-input>"
                : preview(source);
        String key = resolvedScreenId + '\u0000' + roleName + '\u0000' + reason + '\u0000'
                + statusName + '\u0000' + preview;
        if (!loggedTextEvents.add(key)) {
            return null;
        }
        return new TextEvent(
                resolvedModId,
                resolvedScreenId,
                resolvedBackend,
                roleName,
                reason,
                statusName,
                preview
        );
    }

    public void reset() {
        loggedScreens.clear();
        loggedTextEvents.clear();
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

    public record ScreenEvent(String className, String modId, String backend) {
    }

    public record TextEvent(
            String modId,
            String screenId,
            String backend,
            String role,
            String reason,
            String status,
            String preview
    ) {
    }
}
