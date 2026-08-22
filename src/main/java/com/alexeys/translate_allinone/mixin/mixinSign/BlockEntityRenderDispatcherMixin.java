package com.alexeys.translate_allinone.mixin.mixinSign;

import com.alexeys.translate_allinone.utils.translate.SignRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)Z", at = @At("HEAD"))
    private void translate_allinone$beforeSignRenderEntity(
            BlockEntity entity,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof SignBlockEntity sign) {
            SignRenderContext.enter(sign, MinecraftClient.getInstance().textRenderer);
        }
    }

    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)Z", at = @At("RETURN"))
    private void translate_allinone$afterSignRenderEntity(
            BlockEntity entity,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof SignBlockEntity) {
            SignRenderContext.exit();
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"))
    private void translate_allinone$beforeSignRender(
            BlockEntity entity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (entity instanceof SignBlockEntity sign) {
            SignRenderContext.enter(sign, MinecraftClient.getInstance().textRenderer);
        }
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("RETURN"))
    private void translate_allinone$afterSignRender(
            BlockEntity entity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (entity instanceof SignBlockEntity) {
            SignRenderContext.exit();
        }
    }
}