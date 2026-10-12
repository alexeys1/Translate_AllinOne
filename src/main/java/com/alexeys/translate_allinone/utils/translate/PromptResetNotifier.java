package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.PromptOverrideStore;
import com.alexeys.translate_allinone.utils.config.PromptOverrideStore.PromptProviderRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static void tryNotifyOnJoin(MinecraftClient client) {
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

        MutableText message = Text.translatable(MESSAGE_KEY).formatted(Formatting.GOLD);
        message.append(Text.literal(" "));
        message.append(affectedProviders(store.resetNoticeProviders()));

        CompletableFuture.runAsync(store::markResetNoticeSent);
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(message, false);
            }
        });
    }

    public static void resetSession() {
        JOIN_NOTIFICATION_SENT.set(false);
    }

    private static MutableText affectedProviders(List<PromptProviderRef> providers) {
        if (providers.isEmpty()) {
            return link(Text.translatable(LINK_KEY), CONFIG_COMMAND, HOVER_KEY);
        }

        MutableText list = Text.empty();
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) {
                list.append(Text.literal("、"));
            }
            list.append(providerLink(providers.get(i)));
        }
        return list;
    }

    private static MutableText providerLink(PromptProviderRef provider) {
        MutableText label = Text.literal(provider.name());
        if (provider.id().isBlank()) {
            return label;
        }
        return link(label, PROMPT_EDITOR_COMMAND + " " + provider.id(), PROVIDER_HOVER_KEY);
    }

    private static MutableText link(Text label, String command, String hoverKey) {
        return label.copy().setStyle(Style.EMPTY
                .withColor(Formatting.AQUA)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(hoverKey))));
    }
}
