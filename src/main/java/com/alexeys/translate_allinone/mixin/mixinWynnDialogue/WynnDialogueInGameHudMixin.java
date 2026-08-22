package com.alexeys.translate_allinone.mixin.mixinWynnDialogue;

import com.alexeys.translate_allinone.utils.translate.WynnDialogueOverlayController;
import com.alexeys.translate_allinone.utils.translate.WynnDialogueInlinePendingAnimation;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InGameHud.class)
public abstract class WynnDialogueInGameHudMixin {
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text translate_allinone$transformWynnDialogueOverlay(Text original) {
        return WynnDialogueOverlayController.getInstance().transformOverlay(original);
    }

    @ModifyArg(
            method = "renderOverlayMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V"
            ),
            index = 1
    )
    private Text translate_allinone$animatePendingWynnDialogue(Text text) {
        return WynnDialogueInlinePendingAnimation.animate(text);
    }
}
