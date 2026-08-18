package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "foo.starred.athen.config.ui.ClickGUI",
        remap = false
)
public abstract class UiTranslationAthenScreenMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$athenScope;

    @Inject(
            method = "onScramRender",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$enterAthen(CallbackInfo ci) {
        translate_allinone$athenScope = UiTranslationScope.enter((Object) this);
    }

    @Inject(
            method = "onScramRender",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$leaveAthen(CallbackInfo ci) {
        if (translate_allinone$athenScope != null) {
            translate_allinone$athenScope.close();
            translate_allinone$athenScope = null;
        }
    }
}