package com.alexeys.translate_allinone.mixin.mixinAdvancement;

import com.alexeys.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancements/DisplayInfo;getTitle()Lnet/minecraft/network/chat/Component;"
            ),
            require = 0
    )
    private Component translate_allinone$translateTitle(DisplayInfo displayInfo) {
        return VanillaAdvancementTranslationSupport.translateQueuedTitle(advancement, displayInfo.getTitle());
    }
}
