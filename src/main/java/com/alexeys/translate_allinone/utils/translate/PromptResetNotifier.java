package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.PromptOverrideStore;
import com.alexeys.translate_allinone.utils.config.PromptOverrideStore.PromptProviderRef;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PromptResetNotifier {
    private static final String MESSAGE_KEY = "text.translate_allinone.chat.prompts_reset";
    private static final String LINK_KEY = "text.translate_allinone.chat.prompts_reset.link";
    private static final String HOVER_KEY = "text.translate_allinone.chat.prompts_reset.hover";
    private static final String PROVIDER_HOVER_KEY = "text.translate_allinone.chat.prompts_reset.provider_hover";
    private static final String CONFIG_COMMAND = "/taio";
    private static final String PROMPT_EDITOR_COMMAND = "/taio prompts";
    private static final AtomicBoolean JOIN_NOTIFICATION_SENT = new AtomicBoolean(false);

    private PromptResetNotifier() {
    }

    public static void tryNotifyOnJoin(Minecraft client) {
        if (client == null || client.player == null || JOIN_NOTIFICATION_SENT.get()) {
            return;
        }

        PromptOverrideStore store = PromptOverrideStore.getInstance();
        if (!store.isResetNoticePending()) {
            return;
        }
        if (!JOIN_NOTIFICATION_SENT.compareAndSet(false, true)) {
            return;
        }

        MutableComponent message = Component.translatable(MESSAGE_KEY).withStyle(ChatFormatting.GOLD);
        message.append(Component.literal(" "));
        message.append(affectedProviders(store.resetNoticeProviders()));

        CompletableFuture.runAsync(store::markResetNoticeSent);
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }

    public static void resetSession() {
        JOIN_NOTIFICATION_SENT.set(false);
    }

    private static MutableComponent affectedProviders(List<PromptProviderRef> providers) {
        if (providers.isEmpty()) {
            return link(Component.translatable(LINK_KEY), CONFIG_COMMAND, HOVER_KEY);
        }

        MutableComponent list = Component.empty();
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) {
                list.append(Component.literal("、"));
            }
            list.append(providerLink(providers.get(i)));
        }
        return list;
    }

    private static MutableComponent providerLink(PromptProviderRef provider) {
        Component label = Component.literal(provider.name());
        if (provider.id().isBlank()) {
            return label.copy();
        }
        return link(label, PROMPT_EDITOR_COMMAND + " " + provider.id(), PROVIDER_HOVER_KEY);
    }

    private static MutableComponent link(Component label, String command, String hoverKey) {
        return label.copy().withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
    }
}
