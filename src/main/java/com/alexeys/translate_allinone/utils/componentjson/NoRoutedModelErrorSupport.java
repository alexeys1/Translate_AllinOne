package com.alexeys.translate_allinone.utils.componentjson;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NoRoutedModelErrorSupport {
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model";
    private static final long CHAT_NOTIFY_COOLDOWN_MS = 60_000L;
    private static final Map<Surface, Long> LAST_CHAT_NOTIFY = new ConcurrentHashMap<>();

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