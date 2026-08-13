package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueInlinePendingAnimation;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueOverlayController;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Hud.class)
public abstract class WynnDialogueInGameHudMixin {
    @ModifyVariable(
            method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component translate_allinone$transformWynnDialogueOverlay(Component original) {
        return WynnDialogueOverlayController.getInstance().transformOverlay(original);
    }

    @ModifyArg(
            method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
            ),
            index = 1
    )
    private Component translate_allinone$animatePendingWynnDialogue(Component text) {
        return WynnDialogueInlinePendingAnimation.animate(text);
    }
}
