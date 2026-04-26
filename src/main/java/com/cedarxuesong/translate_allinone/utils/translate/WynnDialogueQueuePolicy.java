package com.cedarxuesong.translate_allinone.utils.translate;

final class WynnDialogueQueuePolicy {
    private WynnDialogueQueuePolicy() {
    }

    static boolean shouldAllowDisplayQueue(boolean overlaySource, boolean shouldRequestTranslations) {
        return shouldRequestTranslations && !overlaySource;
    }

    static long resolveOverlayChangedAt(boolean sameDialogue, long previousChangedAt, long now) {
        if (sameDialogue && previousChangedAt > 0L) {
            return previousChangedAt;
        }
        return now;
    }

    static boolean hasMetOverlayStableDelay(long lastChangedAt, long now, long stableDelayMillis) {
        return lastChangedAt > 0L && now - lastChangedAt >= stableDelayMillis;
    }
}
