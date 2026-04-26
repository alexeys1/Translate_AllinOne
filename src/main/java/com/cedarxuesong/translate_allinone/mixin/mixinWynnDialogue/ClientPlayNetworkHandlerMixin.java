package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslationSupport;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameMessage", at = @At("TAIL"))
    private void translate_allinone$handleWynnDialogueSystemChat(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            return;
        }
        WynnDialogueTranslationSupport.traceOverlayEntry(packet.content());
        WynnDialogueTranslationSupport.handleOverlayMessage(packet.content());
    }

    @Inject(method = "onOverlayMessage", at = @At("TAIL"))
    private void translate_allinone$handleWynnDialogueOverlay(OverlayMessageS2CPacket packet, CallbackInfo ci) {
        WynnDialogueTranslationSupport.traceOverlayEntry(packet.text());
        WynnDialogueTranslationSupport.handleOverlayMessage(packet.text());
    }
}
