package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiTextRenderState.class)
public abstract class UiTranslationGuiTextRenderStateMixin {
    @Redirect(
            method = "ensurePrepared()Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
            require = 0
    )
    private Font.PreparedText translate_allinone$prepareText(
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            boolean includeEmpty,
            int background
    ) {
        FormattedCharSequence visible = UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(
                text,
                UiTranslationScope.role()
        );
        return font.prepareText(visible, x, y, color, shadow, includeEmpty, background);
    }
}
