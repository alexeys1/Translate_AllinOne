package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class UiTranslationChatComponentMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$chatScope;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;III)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterChat(
            DrawContext context,
            int mouseX,
            int mouseY,
            int delta,
            CallbackInfo ci
    ) {
        translate_allinone$chatScope = UiTranslationScope.enterInternal();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;III)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveChat(
            DrawContext context,
            int mouseX,
            int mouseY,
            int delta,
            CallbackInfo ci
    ) {
        if (translate_allinone$chatScope != null) {
            translate_allinone$chatScope.close();
            translate_allinone$chatScope = null;
        }
    }
}
