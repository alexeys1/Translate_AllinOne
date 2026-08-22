package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class UiTranslationEditBoxMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$inputScope;

    @Inject(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterInput(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        translate_allinone$inputScope = UiTranslationScope.enterInput();
    }

    @Inject(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveInput(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        if (translate_allinone$inputScope != null) {
            translate_allinone$inputScope.close();
            translate_allinone$inputScope = null;
        }
    }
}
