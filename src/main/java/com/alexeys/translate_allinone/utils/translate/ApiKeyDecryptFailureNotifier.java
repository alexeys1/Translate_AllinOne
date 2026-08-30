package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ApiKeyDecryptFailureNotifier {
    private static final String MESSAGE_KEY = "text.translate_allinone.chat.api_key_decrypt_failed";
    private static final String HOVER_KEY = "text.translate_allinone.chat.api_key_decrypt_failed.hover";
    private static final long RUNTIME_NOTIFY_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(2);
    private static final AtomicBoolean JOIN_NOTIFICATION_SENT = new AtomicBoolean(false);
    private static final AtomicLong LAST_RUNTIME_NOTIFY_AT = new AtomicLong(0L);

    private ApiKeyDecryptFailureNotifier() {
    }

    public static void tryNotifyOnJoin(Minecraft client) {
        if (client == null || client.player == null || JOIN_NOTIFICATION_SENT.get()) {
            return;
        }
        List<ApiProviderProfile> failedProviders = collectFailedProviders();
        if (failedProviders.isEmpty()) {
            return;
        }
        if (JOIN_NOTIFICATION_SENT.compareAndSet(false, true)) {
            send(client, failedProviders);
        }
    }

    public static void notifyRuntimeIfPresent() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        List<ApiProviderProfile> failedProviders = collectFailedProviders();
        if (failedProviders.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = LAST_RUNTIME_NOTIFY_AT.get();
        if (now - previous < RUNTIME_NOTIFY_COOLDOWN_MS) {
            return;
        }
        if (LAST_RUNTIME_NOTIFY_AT.compareAndSet(previous, now)) {
            send(client, failedProviders);
        }
    }

    public static void resetSession() {
        JOIN_NOTIFICATION_SENT.set(false);
        LAST_RUNTIME_NOTIFY_AT.set(0L);
    }

    private static List<ApiProviderProfile> collectFailedProviders() {
        List<ApiProviderProfile> failedProviders = new ArrayList<>();
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.providerManager == null) {
            return failedProviders;
        }
        for (ApiProviderProfile provider : config.providerManager.providers) {
            if (provider != null && provider.hasApiKeyDecryptFailure()) {
                failedProviders.add(provider);
            }
        }
        return failedProviders;
    }

    private static void send(Minecraft client, List<ApiProviderProfile> failedProviders) {
        MutableComponent message = Component.translatable(MESSAGE_KEY, "").withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < failedProviders.size(); i++) {
            if (i > 0) {
                message.append(Component.literal("、"));
            }
            ApiProviderProfile provider = failedProviders.get(i);
            String command = "/taio " + provider.id;
            Style providerStyle = Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.translatable(HOVER_KEY)
                    ));
            message.append(Component.literal(displayName(provider)).setStyle(providerStyle));
        }
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }

    private static String displayName(ApiProviderProfile provider) {
        return provider.name == null || provider.name.isBlank() ? provider.id : provider.name;
    }
}
