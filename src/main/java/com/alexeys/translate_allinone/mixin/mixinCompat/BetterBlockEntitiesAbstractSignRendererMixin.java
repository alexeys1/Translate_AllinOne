package com.alexeys.translate_allinone.mixin.mixinCompat;

import com.alexeys.translate_allinone.utils.translate.SignRenderRuntimeProbe;
import com.alexeys.translate_allinone.utils.translate.SignTranslationSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;
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
    @Inject(
            method = "submitSignText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/world/level/block/entity/SignText;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$registerBetterBlockEntitiesSignText(
            SignRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            SignText signText,
            CallbackInfo ci
    ) {
        SignRenderRuntimeProbe.betterBlockEntitiesSubmitSignTextHit();
        SignTranslationSupport.registerForSubmit(state, signText, Minecraft.getInstance().font);
    }
}
