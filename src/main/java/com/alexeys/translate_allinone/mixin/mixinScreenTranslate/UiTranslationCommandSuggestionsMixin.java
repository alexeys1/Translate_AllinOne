package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandSuggestions.class)
public abstract class UiTranslationCommandSuggestionsMixin {
    @Unique
    private UiTranslationScope.Scope translate_allinone$commandScope;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"),
            require = 0
    )
    private void translate_allinone$enterCommandSuggestions(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        translate_allinone$commandScope = UiTranslationScope.enterInternal();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("RETURN"),
            require = 0
    )
    private void translate_allinone$leaveCommandSuggestions(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (translate_allinone$commandScope != null) {
            translate_allinone$commandScope.close();
            translate_allinone$commandScope = null;
        }
    }
}
