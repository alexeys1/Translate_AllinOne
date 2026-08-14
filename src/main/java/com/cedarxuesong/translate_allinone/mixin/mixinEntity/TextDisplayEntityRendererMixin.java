package com.cedarxuesong.translate_allinone.mixin.mixinEntity;

import com.cedarxuesong.translate_allinone.utils.translate.TextDisplayTranslationSnapshot;
import com.cedarxuesong.translate_allinone.utils.translate.TextDisplayTranslationSupport;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayEntityRendererMixin {
    @Inject(method = "getData", at = @At("RETURN"), cancellable = true)
    private void translate_allinone$translateText(
            DisplayEntity.TextDisplayEntity entity,
            CallbackInfoReturnable<DisplayEntity.TextDisplayEntity.Data> cir
    ) {
        DisplayEntity.TextDisplayEntity.Data original = cir.getReturnValue();
        TextDisplayTranslationSnapshot snapshot = TextDisplayTranslationSupport.resolve(entity, original);
        cir.setReturnValue(snapshot.displayedState());
    }
}
