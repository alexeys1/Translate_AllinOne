package com.alexeys.translate_allinone.mixin.mixinEntity;

import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSnapshot;
import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSupport;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayEntityRendererMixin {
    @Unique
    private TextDisplayTranslationSnapshot translate_allinone$textSnapshot;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void translate_allinone$resolveTextDisplay(
            DisplayEntity.TextDisplayEntity entity,
            TextDisplayEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        translate_allinone$textSnapshot = TextDisplayTranslationSupport.resolve(entity, entity.getData());
    }

    @Redirect(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;getData()Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;"
            )
    )
    private DisplayEntity.TextDisplayEntity.Data translate_allinone$replaceTextData(
            DisplayEntity.TextDisplayEntity entity
    ) {
        TextDisplayTranslationSnapshot snapshot = translate_allinone$textSnapshot;
        if (snapshot == null || snapshot.originalState() == null) {
            snapshot = TextDisplayTranslationSupport.resolve(entity, entity.getData());
            translate_allinone$textSnapshot = snapshot;
        }
        return snapshot.displayedState();
    }

    @Redirect(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;splitLines(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;"
            )
    )
    private DisplayEntity.TextDisplayEntity.TextLines translate_allinone$replaceTextLines(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.LineSplitter splitter
    ) {
        TextDisplayTranslationSnapshot snapshot = translate_allinone$textSnapshot;
        return TextDisplayTranslationSupport.resolveCachedTextLines(entity, splitter, snapshot);
    }
}
