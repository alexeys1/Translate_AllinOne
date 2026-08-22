package com.alexeys.translate_allinone.mixin.mixinAdvancement;

import com.alexeys.translate_allinone.utils.translate.VanillaAdvancementTranslationSupport;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.network.chat.Component;
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
    private AdvancementNode rootNode;

    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void translate_allinone$translateTitle(CallbackInfoReturnable<Component> cir) {
        Component originalTitle = cir.getReturnValue();
        Component translatedTitle = VanillaAdvancementTranslationSupport.translateTitle(
                rootNode == null ? null : rootNode.holder(),
                originalTitle
        );
        if (translatedTitle != originalTitle) {
            cir.setReturnValue(translatedTitle);
        }
    }
}
