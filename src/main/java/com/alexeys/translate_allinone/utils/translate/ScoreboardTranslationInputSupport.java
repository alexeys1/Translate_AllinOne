package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import java.util.HashSet;
import java.util.Set;
public final class ScoreboardTranslationInputSupport {
    private static final long REFRESH_RELEASE_GRACE_MILLIS = 1_000L;
    private static final Set<String> REFRESHED_IDENTITIES_THIS_HOLD = new HashSet<>();
    private static boolean refreshPhysicalHoldActive;
    private static long refreshReleaseGraceExpiresAtMillis;

    private ScoreboardTranslationInputSupport() {
    }

    public static boolean shouldShowOriginal(ScoreboardConfig config) {
        boolean isPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.binding);
        return shouldShowOriginal(config, isPressed);
    }

    static boolean shouldShowOriginal(ScoreboardConfig config, boolean isPressed) {
        ScoreboardConfig.KeybindingMode mode = config == null
                || config.keybinding == null
                || config.keybinding.mode == null
                ? ScoreboardConfig.KeybindingMode.DISABLED
                : config.keybinding.mode;
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> !isPressed;
            case HOLD_TO_SEE_ORIGINAL -> isPressed;
            case DISABLED -> false;
        };
    }

    public static boolean isRefreshPressed(ScoreboardConfig config) {
        boolean physicallyPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
        return updateRefreshState(physicallyPressed, System.currentTimeMillis());
    }

    public static void tick(ScoreboardConfig config) {
        isRefreshPressed(config);
    }

    static boolean updateRefreshState(boolean physicallyPressed, long nowMillis) {
        synchronized (REFRESHED_IDENTITIES_THIS_HOLD) {
            if (physicallyPressed) {
                if (!refreshPhysicalHoldActive) {
                    REFRESHED_IDENTITIES_THIS_HOLD.clear();
                }
                refreshPhysicalHoldActive = true;
                refreshReleaseGraceExpiresAtMillis = 0L;
                return true;
            }

            if (refreshPhysicalHoldActive) {
                refreshPhysicalHoldActive = false;
                refreshReleaseGraceExpiresAtMillis = nowMillis + REFRESH_RELEASE_GRACE_MILLIS;
            }
            if (refreshReleaseGraceExpiresAtMillis > 0L
                    && nowMillis <= refreshReleaseGraceExpiresAtMillis) {
                return true;
            }
            if (refreshReleaseGraceExpiresAtMillis > 0L) {
                refreshReleaseGraceExpiresAtMillis = 0L;
            }
            return false;
        }
    }

    static long refreshReleaseGraceMillis() {
        return REFRESH_RELEASE_GRACE_MILLIS;
    }

    static boolean claimRefreshIdentity(String identity, boolean isPressed) {
        synchronized (REFRESHED_IDENTITIES_THIS_HOLD) {
            if (!isPressed) {
                REFRESHED_IDENTITIES_THIS_HOLD.clear();
                refreshPhysicalHoldActive = false;
                refreshReleaseGraceExpiresAtMillis = 0L;
                return false;
            }
            return identity != null
                    && !identity.isBlank()
                    && REFRESHED_IDENTITIES_THIS_HOLD.add(identity);
        }
    }

    public static boolean claimRefreshIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return false;
        }
        synchronized (REFRESHED_IDENTITIES_THIS_HOLD) {
            return REFRESHED_IDENTITIES_THIS_HOLD.add(identity);
        }
    }

    public static void reset() {
        synchronized (REFRESHED_IDENTITIES_THIS_HOLD) {
            REFRESHED_IDENTITIES_THIS_HOLD.clear();
            refreshPhysicalHoldActive = false;
            refreshReleaseGraceExpiresAtMillis = 0L;
        }
    }
}
