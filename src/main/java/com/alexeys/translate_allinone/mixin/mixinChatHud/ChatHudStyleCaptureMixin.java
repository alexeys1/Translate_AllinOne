package com.alexeys.translate_allinone.mixin.mixinChatHud;

import com.alexeys.translate_allinone.utils.translate.ChatHudStyleCapture;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudStyleCaptureMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$resetCapturedStyle(CallbackInfo ci) {
        ChatHudStyleCapture.reset();
    }
}