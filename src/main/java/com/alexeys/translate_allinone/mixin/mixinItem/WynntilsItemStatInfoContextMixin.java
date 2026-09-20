package com.alexeys.translate_allinone.mixin.mixinItem;

import com.alexeys.translate_allinone.utils.translate.TooltipTranslationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.wynntils.features.tooltips.ItemStatInfoFeature", remap = false)
public abstract class WynntilsItemStatInfoContextMixin {
    @Inject(
            method = "onTooltipPre(Lcom/wynntils/mc/event/ItemTooltipRenderEvent$Pre;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/mc/event/ItemTooltipRenderEvent$Pre;setTooltips(Ljava/util/List;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void translate_allinone$markItemStatInfoTooltipContext(CallbackInfo ci) {
        TooltipTranslationContext.markWynntilsItemStatTooltipRender();
        TooltipTranslationContext.setSkipDrawContextTranslation(false);
    }
}
