package com.cedarxuesong.translate_allinone.mixin.mixinChatHud;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.MessageUtils;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.translate.ChatOutputTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.TranslationFeatureGate;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslationSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @Unique
    private static final long AUTO_TRANSLATE_COMMAND_DELAY_MS = 25L;

    @Unique
    private static final ThreadLocal<Boolean> isModifyingMessage = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    private Component onAddMessage(Component message) {
        if (isModifyingMessage.get()
                || !LifecycleEventManager.isReadyForTranslation
                || !TranslationFeatureGate.isEnabled()) {
            return message;
        }

        try {
            isModifyingMessage.set(true);

            ModConfig config = Translate_AllinOne.getConfig();
            WynnDialogueTranslationSupport.traceChatEntry(message);
            WynnDialogueTranslationSupport.handleChatMessage(message);
            if (config.chatTranslate.output.enabled) {
                String plainText = AnimationManager.stripFormatting(message.getString()).trim();
                if (plainText.isEmpty()) {
                    return message;
                }

                UUID messageId = UUID.randomUUID();
                boolean autoTranslate = config.chatTranslate.output.auto_translate
                        || (config.chatTranslate.output.skyblock_npc_auto_translate
                        && ChatOutputTranslateManager.isSkyblockNpcMessage(message));
                ChatOutputTranslateManager.logInterceptedMessage(messageId, message, plainText, autoTranslate);
                MessageUtils.putTrackedMessage(messageId, message);

                if (autoTranslate) {
                    queueAutoTranslateCommand(messageId);
                    return message;
                } else {
                    return ChatOutputTranslateManager.buildOriginalMessageWithToggle(messageId, message);
                }
            }
            return message;
        } finally {
            isModifyingMessage.set(false);
        }
    }

    @Unique
    private static void queueAutoTranslateCommand(UUID messageId) {
        CompletableFuture.delayedExecutor(AUTO_TRANSLATE_COMMAND_DELAY_MS, TimeUnit.MILLISECONDS).execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand("translate_allinone translatechatline " + messageId);
                }
            });
        });
    }
}
