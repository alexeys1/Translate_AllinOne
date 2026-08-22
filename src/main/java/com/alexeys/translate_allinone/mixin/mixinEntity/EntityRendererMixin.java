package com.alexeys.translate_allinone.mixin.mixinEntity;

import com.alexeys.translate_allinone.utils.translate.EntityTextTranslationSupport;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void translate_allinone$translateNameTag(
            Entity entity,
            EntityRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (state != null && state.nameTag != null) {
            state.nameTag = EntityTextTranslationSupport.translateNameTag(entity, state.nameTag);
        }
    }
}
