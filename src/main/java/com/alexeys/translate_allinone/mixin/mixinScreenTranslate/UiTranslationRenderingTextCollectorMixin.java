package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$RenderingTextCollector")
public abstract class UiTranslationRenderingTextCollectorMixin {
    @ModifyVariable(
            method = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translateSequence(FormattedCharSequence source) {
        return UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = "acceptScrolling(Lnet/minecraft/network/chat/Component;IIIIILnet/minecraft/client/gui/ActiveTextCollector$Parameters;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Component translate_allinone$translateScrollingComponent(Component source) {
        Component visible = UiTranslationRuntime.translateComponentInCurrentScreen(source, UiTextRole.OPTION);
        UiTranslationRuntime.markFormattedSequenceHandled(visible.getVisualOrderText());
        return visible;
    }
}
