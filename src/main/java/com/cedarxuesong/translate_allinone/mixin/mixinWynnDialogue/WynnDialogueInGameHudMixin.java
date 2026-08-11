package com.cedarxuesong.translate_allinone.mixin.mixinWynnDialogue;

import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueOverlayController;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class WynnDialogueInGameHudMixin {
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text translate_allinone$transformWynnDialogueOverlay(Text original) {
        return WynnDialogueOverlayController.getInstance().transformOverlay(original);
    }
}
