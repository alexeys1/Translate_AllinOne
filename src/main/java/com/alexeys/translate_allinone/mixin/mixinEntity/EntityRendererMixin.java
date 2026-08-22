package com.alexeys.translate_allinone.mixin.mixinEntity;

import com.alexeys.translate_allinone.utils.translate.EntityTextTranslationSupport;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void translate_allinone$translateNameTag(
            Entity entity,
            EntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (state != null && state.displayName != null) {
            state.displayName = EntityTextTranslationSupport.translateNameTag(entity, state.displayName);
        }
    }
}
