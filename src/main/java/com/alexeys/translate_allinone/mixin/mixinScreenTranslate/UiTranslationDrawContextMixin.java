package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class UiTranslationDrawContextMixin {
    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private OrderedText translate_allinone$translateFormattedSequence(OrderedText source) {
        return UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Text translate_allinone$translateComponent(Text source) {
        Text visible = UiTranslationRuntime.translateComponentInCurrentScreen(source, UiTextRole.OPTION);
        UiTranslationRuntime.markFormattedSequenceHandled(visible.asOrderedText());
        return visible;
    }

    @ModifyVariable(
            method = {
                    "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
                    "drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
                    "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private String translate_allinone$translateString(String source) {
        return UiTranslationRuntime.translateStringAnimatedInCurrentScreen(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {
                    "drawWrappedText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIIIZ)V",
                    "drawWrappedTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/StringVisitable;IIII)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private StringVisitable translate_allinone$translateWrapped(StringVisitable source) {
        return UiTranslationRuntime.translateFormattedTextInCurrentScreen(source, UiTextRole.DESCRIPTION);
    }

    @ModifyVariable(
            method = {
                    "drawOrderedTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V",
                    "drawOrderedTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/util/Identifier;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private List<OrderedText> translate_allinone$translateTooltipSequences(List<OrderedText> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<OrderedText> translated = new java.util.ArrayList<>(source.size());
        for (OrderedText sequence : source) {
            translated.add(UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(sequence, UiTextRole.TOOLTIP));
        }
        return java.util.Collections.unmodifiableList(translated);
    }
}
