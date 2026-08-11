package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueInlineMaskStyle;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public abstract class WynnDialogueTextDrawerMixin {
    @Shadow
    float x;

    @Inject(
            method = "accept(ILnet/minecraft/text/Style;Lnet/minecraft/client/font/BakedGlyph;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void translate_allinone$hideMaskedGlyph(
            int index,
            Style style,
            BakedGlyph glyph,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!WynnDialogueInlineMaskStyle.isMasked(style)) {
            return;
        }
        x += glyph.getMetrics().getAdvance(style.isBold());
        cir.setReturnValue(true);
    }
}
