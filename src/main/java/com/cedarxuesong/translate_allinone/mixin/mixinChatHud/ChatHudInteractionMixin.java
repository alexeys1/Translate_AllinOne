package com.cedarxuesong.translate_allinone.mixin.mixinChatHud;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatHud.class)
public abstract class ChatHudInteractionMixin {
    @ModifyArg(
            method = "getTextStyleAt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;toChatLineY(D)D"),
            index = 0,
            require = 0
    )
    private double adjustYCoordinateLegacy(double y) {
        return applyInteractionYOffset(y);
    }

    @ModifyArg(
            method = "method_75804(Lnet/minecraft/class_332;Lnet/minecraft/class_327;IIIZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_338$class_12235;<init>(Lnet/minecraft/class_332;Lnet/minecraft/class_327;IIZ)V",
                    remap = false
            ),
            index = 3,
            require = 0,
            remap = false
    )
    private int adjustYCoordinateInteractableCtor(int mouseY) {
        return (int) Math.round(applyInteractionYOffset(mouseY));
    }

    private double applyInteractionYOffset(double y) {
        int offsetAmount = Translate_AllinOne.getConfig().chatTranslate.output.interaction_offset_amount;
        if (offsetAmount == 0) {
            return y;
        }

        double lineHeight = ((ChatHudAccessor) this).invokeGetLineHeight();
        return y - lineHeight * (offsetAmount * 0.2D);
    }
}
