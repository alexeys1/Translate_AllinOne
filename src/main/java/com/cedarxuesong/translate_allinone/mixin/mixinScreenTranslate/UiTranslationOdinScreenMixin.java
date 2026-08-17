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
        targets = "com.odtheking.odin.clickgui.ClickGUI",
        remap = false
)
public abstract class UiTranslationOdinScreenMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$odinScope;

    @Inject(
            method = "method_25394",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void translate_allinone$enterOdin(CallbackInfo ci) {
        translate_allinone$odinScope = UiTranslationScope.enter((Object) this);
    }

    @Inject(
            method = "method_25394",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$leaveOdin(CallbackInfo ci) {
        if (translate_allinone$odinScope != null) {
            translate_allinone$odinScope.close();
            translate_allinone$odinScope = null;
        }
    }
}
