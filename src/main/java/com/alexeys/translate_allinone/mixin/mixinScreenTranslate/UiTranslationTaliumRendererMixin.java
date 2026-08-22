package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(
        targets = "com.github.synnerz.talium.utils.Renderer",
        remap = false
)
public abstract class UiTranslationTaliumRendererMixin {
    @ModifyVariable(
            method = "submitText",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String translate_allinone$translateTaliumText(String source) {
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTextRole.OPTION);
    }
}