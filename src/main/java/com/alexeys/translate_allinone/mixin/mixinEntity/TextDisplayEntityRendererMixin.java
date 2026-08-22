package com.alexeys.translate_allinone.mixin.mixinEntity;

import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSnapshot;
import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSupport;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayEntityRendererMixin {
    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;splitLines(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;"
            )
    )
    private DisplayEntity.TextDisplayEntity.TextLines translate_allinone$translateLines(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.LineSplitter splitter
    ) {
        TextDisplayTranslationSnapshot snapshot = TextDisplayTranslationSupport.resolve(entity, entity.getData());
        if (snapshot.isTranslated()) {
            return splitter.split(snapshot.displayedText(), snapshot.originalState().lineWidth());
        }
        return entity.splitLines(splitter);
    }
}
