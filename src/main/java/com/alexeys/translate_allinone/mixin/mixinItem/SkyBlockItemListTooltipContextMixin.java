package com.alexeys.translate_allinone.mixin.mixinItem;

import com.alexeys.translate_allinone.utils.translate.TooltipTranslationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.operationpotato.itemlist.gui.StackDisplay",
        "com.operationpotato.itemlist.gui.CollapsibleStackDisplay",
        "com.operationpotato.itemlist.gui.recipe.IngredientDisplay"
}, remap = false)
public abstract class SkyBlockItemListTooltipContextMixin {

    @Inject(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$pushSkyBlockItemListTooltipContext(
            @Coerce Object graphics,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        TooltipTranslationContext.pushSkyBlockItemListTooltipRender();
    }

    @Inject(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$popSkyBlockItemListTooltipContext(
            @Coerce Object graphics,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        TooltipTranslationContext.popSkyBlockItemListTooltipRender();
    }
}