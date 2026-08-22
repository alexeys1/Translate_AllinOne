package com.alexeys.translate_allinone.mixin.mixinSign;

import com.alexeys.translate_allinone.utils.translate.SignTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.SignRenderContext;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignBlockEntityRenderer.class)
public abstract class AbstractSignBlockEntityRendererMixin {
    @Shadow
    @Final
    private TextRenderer textRenderer;

    @Inject(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V", at = @At("HEAD"))
    private void translate_allinone$resolveSign(
            SignBlockEntity sign,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            CallbackInfo ci
    ) {
        SignTranslationSupport.RenderedSignText rendered = SignRenderContext.rendered(sign);
        translate_allinone$translatedSign = rendered == null
                ? SignTranslationSupport.resolveForRender(sign, textRenderer)
                : rendered;
    }

    @org.spongepowered.asm.mixin.Unique
    private SignTranslationSupport.RenderedSignText translate_allinone$translatedSign;

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/BlockState;Lnet/minecraft/block/AbstractSignBlock;Lnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/SignBlockEntity;getFrontText()Lnet/minecraft/block/entity/SignText;")
    )
    private SignText translate_allinone$frontText(SignBlockEntity sign) {
        SignTranslationSupport.RenderedSignText translated = translate_allinone$translatedSign;
        return translated == null || translated.frontText() == null ? sign.getFrontText() : translated.frontText();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "render(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/BlockState;Lnet/minecraft/block/AbstractSignBlock;Lnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/SignBlockEntity;getBackText()Lnet/minecraft/block/entity/SignText;")
    )
    private SignText translate_allinone$backText(SignBlockEntity sign) {
        SignTranslationSupport.RenderedSignText translated = translate_allinone$translatedSign;
        return translated == null || translated.backText() == null ? sign.getBackText() : translated.backText();
    }
}
