package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class UiTranslationChatComponentMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$chatScope;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterChat(
            GuiGraphicsExtractor context,
            Font font,
            int mouseX,
            int mouseY,
            int delta,
            ChatComponent.DisplayMode displayMode,
            boolean bl,
            CallbackInfo ci
    ) {
        translate_allinone$chatScope = UiTranslationScope.enterInternal();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveChat(
            GuiGraphicsExtractor context,
            Font font,
            int mouseX,
            int mouseY,
            int delta,
            ChatComponent.DisplayMode displayMode,
            boolean bl,
            CallbackInfo ci
    ) {
        if (translate_allinone$chatScope != null) {
            translate_allinone$chatScope.close();
            translate_allinone$chatScope = null;
        }
    }
}
