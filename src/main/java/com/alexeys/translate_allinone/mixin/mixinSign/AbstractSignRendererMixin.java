package com.alexeys.translate_allinone.mixin.mixinSign;

import com.alexeys.translate_allinone.utils.translate.SignTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.SignRenderRuntimeProbe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public class AbstractSignRendererMixin {
    @Shadow
    @Final
    private Font font;

    @Inject(
            method = "submitSignText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/world/level/block/entity/SignText;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$registerTranslatedSignText(
            SignRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            SignText signText,
            CallbackInfo ci
    ) {
        SignRenderRuntimeProbe.submitSignTextHit();
        SignTranslationSupport.registerForSubmit(state, signText, font);
    }
}
