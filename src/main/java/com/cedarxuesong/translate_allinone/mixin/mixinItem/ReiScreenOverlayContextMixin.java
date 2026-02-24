package com.cedarxuesong.translate_allinone.mixin.mixinItem;

import com.cedarxuesong.translate_allinone.utils.translate.TooltipTranslationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.gui.fabric.ScreenOverlayImplFabric", remap = false)
public abstract class ReiScreenOverlayContextMixin {
    @Inject(
            method = {
                    "renderTooltipInner(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/gui/DrawContext;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;II)V",
                    "renderTooltipInner(Lnet/minecraft/class_437;Lnet/minecraft/class_332;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;II)V"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$pushReiTooltipContext(CallbackInfo ci) {
        TooltipTranslationContext.pushReiTooltipRender();
    }

    @Inject(
            method = {
                    "renderTooltipInner(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/gui/DrawContext;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;II)V",
                    "renderTooltipInner(Lnet/minecraft/class_437;Lnet/minecraft/class_332;Lme/shedaniel/rei/api/client/gui/widgets/Tooltip;II)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$popReiTooltipContext(CallbackInfo ci) {
        TooltipTranslationContext.popReiTooltipRender();
    }
}
