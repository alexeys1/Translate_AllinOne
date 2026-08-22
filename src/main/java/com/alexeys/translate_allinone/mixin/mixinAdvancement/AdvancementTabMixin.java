package com.alexeys.translate_allinone.mixin.mixinAdvancement;

import com.alexeys.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementTab.class)
public class AdvancementTabMixin {
    @Shadow
    @Final
    private PlacedAdvancement root;

    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void translate_allinone$translateTitle(CallbackInfoReturnable<Text> cir) {
        Text originalTitle = cir.getReturnValue();
        Text translatedTitle = VanillaAdvancementTranslationSupport.translateTitle(
                root == null ? null : root.getAdvancementEntry(),
                originalTitle
        );
        if (translatedTitle != originalTitle) {
            cir.setReturnValue(translatedTitle);
        }
    }
}
