package com.cedarxuesong.translate_allinone.mixin.mixinChatHud;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.LifecycleEventManager;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.MessageUtils;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.translate.ChatOutputTranslateManager;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslationSupport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Unique
    private static final long AUTO_TRANSLATE_COMMAND_DELAY_MS = 25L;

    @Unique
    private static final ThreadLocal<Boolean> isModifyingMessage = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true)
    private Text onAddMessage(Text message) {
        if (isModifyingMessage.get() || !LifecycleEventManager.isReadyForTranslation) {
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
                ChatOutputTranslateManager.logInterceptedMessage(messageId, message, plainText, config.chatTranslate.output.auto_translate);
                MessageUtils.putTrackedMessage(messageId, message);

                if (config.chatTranslate.output.auto_translate) {
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatCommand("translate_allinone translatechatline " + messageId);
                }
            });
        });
    }
}
