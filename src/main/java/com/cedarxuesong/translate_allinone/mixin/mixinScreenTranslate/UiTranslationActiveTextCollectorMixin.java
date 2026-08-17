package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiTextRole;
import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationRuntime;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ActiveTextCollector.class)
public interface UiTranslationActiveTextCollectorMixin {
    @ModifyVariable(
            method = {
                    "accept(IILnet/minecraft/network/chat/Component;)V",
                    "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/network/chat/Component;)V",
                    "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/network/chat/Component;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Component translate_allinone$translateComponent(Component source) {
        Component visible = UiTranslationRuntime.translateComponentInCurrentScreen(source, UiTextRole.OPTION);
        UiTranslationRuntime.markFormattedSequenceHandled(visible.getVisualOrderText());
        return visible;
    }

    @ModifyVariable(
            method = {
                    "accept(IILnet/minecraft/util/FormattedCharSequence;)V",
                    "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/util/FormattedCharSequence;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translateSequence(FormattedCharSequence source) {
        return UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(source, UiTextRole.OPTION);
    }
}
