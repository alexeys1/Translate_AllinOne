package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.gui.DrawContext$TextConsumerImpl")
public abstract class UiTranslationTextConsumerMixin {
    @ModifyVariable(
            method = "text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/client/font/DrawnTextConsumer$Transformation;Lnet/minecraft/text/OrderedText;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private OrderedText translate_allinone$translateSequence(OrderedText source) {
        return UiTranslationRuntime.translateFormattedCharSequence(source, UiTextRole.OPTION);
    }
}
