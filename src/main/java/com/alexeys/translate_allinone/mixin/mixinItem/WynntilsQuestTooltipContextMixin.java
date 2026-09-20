package com.alexeys.translate_allinone.mixin.mixinItem;

import com.alexeys.translate_allinone.utils.translate.TooltipTranslationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.wynntils.screens.activities.WynntilsContentBookScreen", remap = false)
public abstract class WynntilsQuestTooltipContextMixin {
    @Inject(
            method = {
                    "renderTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
                    "renderTooltips(Lnet/minecraft/class_332;II)V"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$pushQuestTooltipContext(CallbackInfo ci) {
        TooltipTranslationContext.pushWynntilsQuestTooltipRender();
    }

    @Inject(
            method = {
                    "renderTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
                    "renderTooltips(Lnet/minecraft/class_332;II)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$popQuestTooltipContext(CallbackInfo ci) {
        TooltipTranslationContext.popWynntilsQuestTooltipRender();
    }
}
