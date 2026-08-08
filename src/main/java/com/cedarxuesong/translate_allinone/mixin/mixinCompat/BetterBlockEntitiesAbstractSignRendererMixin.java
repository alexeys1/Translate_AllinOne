package com.cedarxuesong.translate_allinone.mixin.mixinCompat;

import com.cedarxuesong.translate_allinone.utils.translate.SignTranslationSupport;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "betterblockentities.client.render.immediate.blockentity.renderers.BBEAbstractSignRenderer",
        remap = false
)
public abstract class BetterBlockEntitiesAbstractSignRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void translate_allinone$translateRenderState(
            SignBlockEntity sign,
            SignBlockEntityRenderState state,
            float tickDelta,
            Vec3d cameraPos,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            CallbackInfo ci
    ) {
        SignTranslationSupport.RenderedSignText translated =
                SignTranslationSupport.resolveForRender(
                        sign,
                        state,
                        MinecraftClient.getInstance().textRenderer
                );
        if (translated.frontText() != null) {
            state.frontText = translated.frontText();
        }
        if (translated.backText() != null) {
            state.backText = translated.backText();
        }
    }
}
