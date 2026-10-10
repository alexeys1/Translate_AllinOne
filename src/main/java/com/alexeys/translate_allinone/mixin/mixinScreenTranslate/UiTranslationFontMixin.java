package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationLazySplitList;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import com.alexeys.translate_allinone.utils.text.LegacyComponentTextCodec;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Font.class)
public abstract class UiTranslationFontMixin {
    @ModifyVariable(
            method = {
                    "width(Lnet/minecraft/network/chat/FormattedText;)I",
                    "wordWrapHeight(Lnet/minecraft/network/chat/FormattedText;I)I"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedText translate_allinone$translatedMeasuredText(FormattedText source) {
        return UiTranslationRuntime.withoutOriginatingRequests(
                () -> UiTranslationRuntime.translateFormattedText(source, UiTranslationScope.role())
        );
    }

    @ModifyVariable(
            method = {
                    "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;",
                    "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedText translate_allinone$translatedFormattedText(FormattedText source) {
        return UiTranslationRuntime.translateFormattedText(source, UiTranslationScope.role());
    }

    @Inject(
            method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void translate_allinone$lazySplit(
            FormattedText source,
            int width,
            CallbackInfoReturnable<List<FormattedCharSequence>> cir
    ) {
        if (!UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return;
        }
        UiTextRole role = UiTranslationScope.role();
        cir.setReturnValue(new UiTranslationLazySplitList(
                (Font) (Object) this,
                source,
                width,
                role
        ));
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translatedSequenceWidth(FormattedCharSequence source) {
        return UiTranslationRuntime.withoutOriginatingRequests(
                () -> UiTranslationRuntime.translateFormattedCharSequence(source, UiTranslationScope.role())
        );
    }

    @ModifyVariable(
            method = {
                    "plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;",
                    "plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private String translate_allinone$translatedPlainText(String source) {
        return UiTranslationRuntime.translateString(source, UiTranslationScope.role());
    }

    @ModifyVariable(
            method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translatedSequence(FormattedCharSequence source) {
        FormattedCharSequence visible = UiTranslationRuntime.translateFormattedCharSequence(source, UiTranslationScope.role());
        if (visible != source) {
            UiTranslationRuntime.markFormattedSequenceHandled(visible);
        }
        return visible;
    }

    @ModifyVariable(
            method = "drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translatedOutlineSequence(FormattedCharSequence source) {
        return UiTranslationRuntime.translateFormattedCharSequence(source, UiTranslationScope.role());
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private FormattedCharSequence translate_allinone$translatedPreparedSequence(FormattedCharSequence source) {
        return UiTranslationRuntime.translateFormattedCharSequence(source, UiTranslationScope.role());
    }

    @Redirect(
            method = "width(Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/StringSplitter;stringWidth(Ljava/lang/String;)F"
            ),
            require = 0
    )
    private float translate_allinone$translatedWidth(StringSplitter splitter, String source) {
        if (!UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return splitter.stringWidth(source);
        }

        String visible = UiTranslationRuntime.withoutOriginatingRequests(
                () -> UiTranslationRuntime.translateString(source, UiTranslationScope.role())
        );
        return splitter.stringWidth(visible);
    }

    @Redirect(
            method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
            require = 0
    )
    private Font.PreparedText translate_allinone$translatedPreparedText(
            Font font,
            String source,
            float x,
            float y,
            int color,
            boolean shadow,
            int background
    ) {
        Component sourceComponent = source.indexOf('搂') >= 0 ? LegacyComponentTextCodec.decode(source) : Component.literal(source);
        Component visible = UiTranslationRuntime.translateComponent(sourceComponent, UiTranslationScope.role());
        return UiTranslationScope.withInternal(() -> font.prepareText(visible.getVisualOrderText(), x, y, color, shadow, false, background));
    }

    @Redirect(
            method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;"
            ),
            require = 0
    )
    private FormattedCharSequence translate_allinone$translatedComponent(Component source) {
        FormattedCharSequence sequence = UiTranslationRuntime.translateComponent(source, UiTextRole.OPTION)
                .getVisualOrderText();
        UiTranslationRuntime.markFormattedSequenceHandled(sequence);
        return sequence;
    }
}
