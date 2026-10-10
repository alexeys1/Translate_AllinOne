package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Pseudo
@Mixin(targets = "com.github.noamm9.ui.clickgui.components.CategoryPanel", remap = false)
public abstract class UiTranslationNoammSortingMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$layoutProbe;

    @Inject(method = "getSorting", at = @At("HEAD"), require = 0, remap = false)
    private void translate_allinone$enterLayoutProbe(CallbackInfoReturnable<Collection<?>> cir) {
        translate_allinone$layoutProbe = UiTranslationScope.enterInternal();
    }

    @Inject(method = "getSorting", at = @At("RETURN"), require = 0, remap = false)
    private void translate_allinone$leaveLayoutProbe(CallbackInfoReturnable<Collection<?>> cir) {
        if (translate_allinone$layoutProbe != null) {
            translate_allinone$layoutProbe.close();
            translate_allinone$layoutProbe = null;
        }
    }
}
