package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueTranslationSupport;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void translate_allinone$handleWynnDialogueSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            return;
        }
        WynnDialogueTranslationSupport.traceOverlayEntry(packet.content());
        WynnDialogueTranslationSupport.handleOverlayMessage(packet.content());
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void translate_allinone$handleWynnDialogueOverlay(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        WynnDialogueTranslationSupport.traceOverlayEntry(packet.text());
        WynnDialogueTranslationSupport.handleOverlayMessage(packet.text());
    }
}
