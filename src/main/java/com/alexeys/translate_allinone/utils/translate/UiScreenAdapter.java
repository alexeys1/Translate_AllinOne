package com.alexeys.translate_allinone.utils.translate;

import java.util.Locale;
import java.util.Set;

public record UiScreenAdapter(
        String modId,
        String screenId,
        Backend backend,
        Set<UiTextRole> roles
) {
    public UiScreenAdapter {
        modId = normalize(modId);
        screenId = normalize(screenId);
        backend = backend == null ? Backend.COMPONENT : backend;
        roles = roles == null || roles.isEmpty() ? Set.of(UiTextRole.values()) : Set.copyOf(roles);
    }

    public boolean supports(UiTextRole role) {
        return role == null || roles.contains(role);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum Backend {
        COMPONENT,
        MINECRAFT_FONT,
        NANOVG
    }
}
