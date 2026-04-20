package com.cedarxuesong.translate_allinone.mixin.mixinWynntils;

import com.cedarxuesong.translate_allinone.utils.translate.WynntilsTaskTrackerTranslationSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wynntils.functions.ActivityFunctions$ActivityTypeFunction", remap = false)
public abstract class ActivityTypeFunctionMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void translate_allinone$translateTrackedActivityType(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(WynntilsTaskTrackerTranslationSupport.translateTitle(cir.getReturnValue()));
    }
}
