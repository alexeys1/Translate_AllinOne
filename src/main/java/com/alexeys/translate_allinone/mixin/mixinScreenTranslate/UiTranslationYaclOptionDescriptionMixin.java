package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationLazySplitList;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(
        targets = "dev.isxander.yacl3.gui.OptionDescriptionWidget",
        remap = false
)
public abstract class UiTranslationYaclOptionDescriptionMixin {
    @Redirect(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"
            ),
            require = 0,
            remap = true
    )
    private List<OrderedText> translate_allinone$translateSplit(
            TextRenderer font,
            StringVisitable source,
            int width
    ) {
        return new UiTranslationLazySplitList(font, source, width, UiTextRole.DESCRIPTION);
    }
}
