package com.alexeys.translate_allinone.mixin.mixinAdvancement;

import com.alexeys.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
    @Shadow
    @Final
    private AdvancementEntry advancement;

    @Redirect(
            method = "draw(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/toast/ToastManager;J)Lnet/minecraft/client/toast/Toast$Visibility;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancement/AdvancementDisplay;getTitle()Lnet/minecraft/text/Text;"
            ),
            require = 0
    )
    private Text translate_allinone$translateTitle(AdvancementDisplay displayInfo) {
        return VanillaAdvancementTranslationSupport.translateQueuedTitle(advancement, displayInfo.getTitle());
    }
}
