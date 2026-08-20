package com.cedarxuesong.translate_allinone.utils.componentjson;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NoRoutedModelErrorSupport {
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";
    private static final String TOOLTIP_ERROR_MESSAGE = "No routed model selected";
    private static final long TOOLTIP_ERROR_DISPLAY_MS = 3_000L;
    private static final long TOOLTIP_ERROR_QUIET_MS = 5_000L;
    private static final int TOOLTIP_ERROR_ENTRY_LIMIT = 128;
    private static final long CHAT_NOTIFY_COOLDOWN_MS = 60_000L;
    private static final Map<Surface, Long> LAST_CHAT_NOTIFY = new ConcurrentHashMap<>();
    private static final Map<String, Long> TOOLTIP_ERROR_SINCE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > TOOLTIP_ERROR_ENTRY_LIMIT;
        }
    };

    private NoRoutedModelErrorSupport() {
    }

    public enum Surface {
        ITEM_TOOLTIP,
        OTHER_TRANSLATIONS,
        SCOREBOARD
    }

    public static void onNoRoutedModel(Surface surface) {
        if (surface == null) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (surface) {
            case ITEM_TOOLTIP -> {
            }
            case OTHER_TRANSLATIONS, SCOREBOARD -> notifyChat(surface, now);
        }
    }

    public static boolean isTooltipNoRoutedError(String errorMessage) {
        return errorMessage != null && errorMessage.contains(TOOLTIP_ERROR_MESSAGE);
    }

    public static boolean shouldShowTooltipError(String tooltipFingerprint) {
        if (tooltipFingerprint == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long since = TOOLTIP_ERROR_SINCE.get(tooltipFingerprint);
        if (since == null || now - since >= TOOLTIP_ERROR_DISPLAY_MS + TOOLTIP_ERROR_QUIET_MS) {
            since = now;
            TOOLTIP_ERROR_SINCE.put(tooltipFingerprint, now);
        }
        return now - since < TOOLTIP_ERROR_DISPLAY_MS;
    }

    public static String tooltipErrorMessage() {
        return TOOLTIP_ERROR_MESSAGE;
    }

    private static void notifyChat(Surface surface, long now) {
        Long previous = LAST_CHAT_NOTIFY.get(surface);
        if (previous != null && now - previous < CHAT_NOTIFY_COOLDOWN_MS) {
            return;
        }
        LAST_CHAT_NOTIFY.put(surface, now);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        Text message = Text.translatable(NO_ROUTED_MODEL_ERROR_KEY).formatted(Formatting.RED);
        client.execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(message, false);
            }
        });
    }
}
