package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import com.alexeys.translate_allinone.utils.translate.WynnDialogueInlineMaskStyle;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class WynnDialogueTextDrawerMixin {
    @Shadow
    private float x;

    @Inject(
            method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
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
        x += glyph.info().getAdvance(style.isBold());
        cir.setReturnValue(true);
    }
}
