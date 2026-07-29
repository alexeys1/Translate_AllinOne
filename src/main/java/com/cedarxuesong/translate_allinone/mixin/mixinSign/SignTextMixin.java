package com.cedarxuesong.translate_allinone.mixin.mixinSign;

import com.cedarxuesong.translate_allinone.utils.translate.SignTranslationSupport;
import com.cedarxuesong.translate_allinone.utils.translate.SignRenderRuntimeProbe;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignText.class)
public abstract class SignTextMixin {
    @Inject(method = "getRenderMessages", at = @At("HEAD"), cancellable = true)
    private void translate_allinone$useSubmittedTranslation(
            boolean filtered,
            Function<Component, FormattedCharSequence> transformer,
            CallbackInfoReturnable<FormattedCharSequence[]> cir
    ) {
        SignRenderRuntimeProbe.getRenderMessagesHit();
        FormattedCharSequence[] translated = SignTranslationSupport.getRegisteredRenderLines(
                (SignText) (Object) this
        );
        if (translated != null) {
            SignRenderRuntimeProbe.translatedLinesApplied();
            cir.setReturnValue(translated);
        }
    }
}
