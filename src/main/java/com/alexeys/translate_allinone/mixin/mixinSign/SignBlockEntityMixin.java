package com.alexeys.translate_allinone.mixin.mixinSign;

import com.alexeys.translate_allinone.utils.translate.SignRenderContext;
import com.alexeys.translate_allinone.utils.translate.SignTranslationSupport;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {
    @Inject(method = "getFrontText()Lnet/minecraft/block/entity/SignText;", at = @At("HEAD"), cancellable = true)
    private void translate_allinone$translatedFront(CallbackInfoReturnable<SignText> cir) {
        SignBlockEntity self = (SignBlockEntity) (Object) this;
        SignTranslationSupport.RenderedSignText rendered = SignRenderContext.rendered(self);
        if (rendered != null && rendered.frontText() != null) {
            cir.setReturnValue(rendered.frontText());
        }
    }

    @Inject(method = "getBackText()Lnet/minecraft/block/entity/SignText;", at = @At("HEAD"), cancellable = true)
    private void translate_allinone$translatedBack(CallbackInfoReturnable<SignText> cir) {
        SignBlockEntity self = (SignBlockEntity) (Object) this;
        SignTranslationSupport.RenderedSignText rendered = SignRenderContext.rendered(self);
        if (rendered != null && rendered.backText() != null) {
            cir.setReturnValue(rendered.backText());
        }
    }
}