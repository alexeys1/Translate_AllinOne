package com.alexeys.translate_allinone.mixin.mixinEntity;

import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSnapshot;
import com.alexeys.translate_allinone.utils.translate.TextDisplayTranslationSupport;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class TextDisplayRendererMixin {
    @Unique
    private TextDisplayTranslationSnapshot translate_allinone$textSnapshot;

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void translate_allinone$resolveTextDisplay(
            Display.TextDisplay entity,
            TextDisplayEntityRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        translate_allinone$textSnapshot = TextDisplayTranslationSupport.resolve(entity, entity.textRenderState());
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Display$TextDisplay;textRenderState()Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;"
            ),
            require = 0
    )
    private Display.TextDisplay.TextRenderState translate_allinone$replaceTextRenderState(Display.TextDisplay entity) {
        TextDisplayTranslationSnapshot snapshot = translate_allinone$textSnapshot;
        if (snapshot == null || snapshot.originalState() == null) {
            snapshot = TextDisplayTranslationSupport.resolve(entity, entity.textRenderState());
            translate_allinone$textSnapshot = snapshot;
        }
        return snapshot.displayedState();
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Display$TextDisplay;cacheDisplay(Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
            ),
            require = 0
    )
    private Display.TextDisplay.CachedInfo translate_allinone$replaceCachedInfo(
            Display.TextDisplay entity,
            Display.TextDisplay.LineSplitter splitter
    ) {
        TextDisplayTranslationSnapshot snapshot = translate_allinone$textSnapshot;
        if (snapshot != null && snapshot.isTranslated()) {
            return splitter.split(snapshot.displayedText(), snapshot.originalState().lineWidth());
        }
        return entity.cacheDisplay(splitter);
    }
}
