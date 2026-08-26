package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationLazySplitList;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class UiTranslationGuiGraphicsExtractorMixin {
    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
                    "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translateFormattedSequence(FormattedCharSequence source) {
        return UiTranslationRuntime.translateFormattedCharSequence(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {
                    "setTooltipForNextFrame(Ljava/util/List;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private List<FormattedCharSequence> translate_allinone$translateTooltipSequences(List<FormattedCharSequence> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<FormattedCharSequence> translated = new java.util.ArrayList<>(source.size());
        for (FormattedCharSequence sequence : source) {
            translated.add(UiTranslationRuntime.translateFormattedCharSequence(sequence, UiTextRole.TOOLTIP));
        }
        return java.util.Collections.unmodifiableList(translated);
    }

    @Redirect(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
                    "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;"
            ),
            require = 0
    )
    private FormattedCharSequence translate_allinone$translateComponent(Component source) {
        Component visible = UiTranslationRuntime.translateComponent(source, UiTextRole.OPTION);
        FormattedCharSequence sequence = visible.getVisualOrderText();
        UiTranslationRuntime.markFormattedSequenceHandled(sequence);
        return sequence;
    }

    @Redirect(
            method = {
                    "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
                    "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IILnet/minecraft/resources/Identifier;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;"
            ),
            require = 0
    )
    private FormattedCharSequence translate_allinone$translateTooltip(Component source) {
        Component visible = UiTranslationRuntime.translateComponent(source, UiTextRole.TOOLTIP);
        FormattedCharSequence sequence = visible.getVisualOrderText();
        UiTranslationRuntime.markFormattedSequenceHandled(sequence);
        return sequence;
    }

    @Redirect(
            method = "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"
            ),
            require = 0
    )
    private int translate_allinone$translatedCenteredWidth(Font font, String source) {
        String visible = UiTranslationRuntime.translateString(source, UiTextRole.OPTION);
        return UiTranslationRuntime.withoutNestedTranslation(() -> font.width(visible));
    }

    @Redirect(
            method = "textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
            ),
            require = 0
    )
    private List<FormattedCharSequence> translate_allinone$translatedWrap(
            Font font,
            FormattedText source,
            int width
    ) {
        if (!UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return font.split(source, width);
        }
        return new UiTranslationLazySplitList(font, source, width, UiTextRole.DESCRIPTION);
    }
}
