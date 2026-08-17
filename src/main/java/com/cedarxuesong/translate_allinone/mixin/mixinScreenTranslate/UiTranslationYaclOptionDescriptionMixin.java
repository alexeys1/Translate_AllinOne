package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTextRole;
import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationLazySplitList;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
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
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
            ),
            require = 0,
            remap = true
    )
    private List<FormattedCharSequence> translate_allinone$translateSplit(
            Font font,
            FormattedText source,
            int width
    ) {
        // Return a lazy list instead of a snapshot. YACL caches this list in `wrappedText`,
        // so a plain translated list would freeze the pre-AI-cache original forever. The lazy
        // list re-evaluates translation once per frame, which lets a later AI translation result
        // appear without needing a UI-specific cache reset.
        return new UiTranslationLazySplitList(font, source, width, UiTextRole.DESCRIPTION);
    }
}
