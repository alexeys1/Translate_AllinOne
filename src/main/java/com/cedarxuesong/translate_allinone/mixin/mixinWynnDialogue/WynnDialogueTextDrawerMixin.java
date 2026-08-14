package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueInlineMaskStyle;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueFontFallback;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public abstract class WynnDialogueTextDrawerMixin {
    @Shadow
    float x;

    @Shadow
    @Final
    TextRenderer field_24240;

    @Inject(
            method = "accept(ILnet/minecraft/text/Style;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void translate_allinone$hideMaskedGlyph(
            int index,
            Style style,
            int codePoint,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!WynnDialogueInlineMaskStyle.isMasked(style)) {
            return;
        }
        WynnDialogueTextRendererAccessor accessor = (WynnDialogueTextRendererAccessor) field_24240;
        Function<Identifier, FontStorage> fonts = accessor.translate_allinone$getFontStorageAccessor();
        x += WynnDialogueFontFallback.advance(
                fonts,
                style,
                codePoint,
                accessor.translate_allinone$isValidateAdvance()
        );
        cir.setReturnValue(true);
    }
}
