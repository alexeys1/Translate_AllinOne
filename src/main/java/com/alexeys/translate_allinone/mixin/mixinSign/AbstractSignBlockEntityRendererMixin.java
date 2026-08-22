package com.alexeys.translate_allinone.mixin.mixinSign;

import com.alexeys.translate_allinone.utils.translate.SignTranslationSupport;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class AbstractSignBlockEntityRendererMixin {
    @Shadow
    @Final
    private TextRenderer textRenderer;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void translate_allinone$translateRenderState(
            SignBlockEntity sign,
            SignBlockEntityRenderState state,
            float tickDelta,
            Vec3d cameraPos,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            CallbackInfo ci
    ) {
        SignTranslationSupport.RenderedSignText translated =
                SignTranslationSupport.resolveForRender(sign, state, textRenderer);
        if (translated.frontText() != null) {
            state.frontText = translated.frontText();
        }
        if (translated.backText() != null) {
            state.backText = translated.backText();
        }
    }
}
