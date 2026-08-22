package com.alexeys.translate_allinone.mixin.mixinChatHud;

import com.alexeys.translate_allinone.utils.translate.ChatHudStyleCapture;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Interactable")
public abstract class ChatHudInteractableMixin {
    @Inject(
            method = "accept(Lnet/minecraft/text/Style;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$captureChatStyle(Style style, CallbackInfo ci) {
        if (style != null) {
            ChatHudStyleCapture.set(style);
        }
    }
}