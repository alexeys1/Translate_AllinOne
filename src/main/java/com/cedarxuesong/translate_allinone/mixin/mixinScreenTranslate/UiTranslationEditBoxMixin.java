package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFieldWidget.class)
public abstract class UiTranslationEditBoxMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$inputScope;

    @Inject(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterInput(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        translate_allinone$inputScope = UiTranslationScope.enterInput();
    }

    @Inject(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveInput(
            DrawContext context,
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
