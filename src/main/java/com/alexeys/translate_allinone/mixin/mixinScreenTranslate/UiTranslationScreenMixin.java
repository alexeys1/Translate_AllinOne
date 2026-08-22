package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class UiTranslationScreenMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$uiScope;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterScope(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        UiTranslationRuntime.beginFrame();
        translate_allinone$uiScope = UiTranslationScope.enter((Screen) (Object) this);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveScope(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo callbackInfo
    ) {
        if (translate_allinone$uiScope != null) {
            translate_allinone$uiScope.close();
            translate_allinone$uiScope = null;
        }
    }
}
