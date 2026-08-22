package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(
        targets = {
                "gg.essential.elementa.font.BasicFontRenderer",
                "gg.essential.elementa.VanillaFontRenderer"
        },
        remap = false
)
public abstract class UiTranslationElementaFontMixin {
    @ModifyVariable(
            method = {
                    "getStringWidth(Ljava/lang/String;F)F",
                    "drawString(Lgg/essential/universal/UMatrixStack;Ljava/lang/String;Ljava/awt/Color;FFFFZLjava/awt/Color;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String translate_allinone$translateText(String source) {
        if (getClass().getName().contains("VanillaFontRenderer")) {
            return UiTranslationRuntime.translateStringAnimatedInCurrentScreen(source, UiTextRole.OPTION);
        }
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTextRole.OPTION);
    }
}
