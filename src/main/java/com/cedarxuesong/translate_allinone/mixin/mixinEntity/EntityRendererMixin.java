package com.cedarxuesong.translate_allinone.mixin.mixinEntity;

import com.cedarxuesong.translate_allinone.utils.translate.EntityTextTranslationSupport;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Redirect(
            method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getDisplayName()Lnet/minecraft/text/Text;")
    )
    private Text translate_allinone$translateNameTag(Entity entity) {
        return EntityTextTranslationSupport.translateNameTag(entity, entity.getDisplayName());
    }
}
