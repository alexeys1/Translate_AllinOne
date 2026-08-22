package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiScreenAdapter;
import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationLazySplitList;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TextRenderer.class)
public abstract class UiTranslationFontMixin {
    @ModifyVariable(
            method = {
                    "getWidth(Lnet/minecraft/text/StringVisitable;)I",
                    "trimToWidth(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/StringVisitable;",
                    "getWrappedLinesHeight(Lnet/minecraft/text/StringVisitable;I)I"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private StringVisitable translate_allinone$translatedFormattedText(StringVisitable source) {
        return UiTranslationRuntime.translateFormattedTextInCurrentScreen(source, UiTranslationScope.role());
    }

    @Inject(
            method = "wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void translate_allinone$lazySplit(
            StringVisitable source,
            int width,
            CallbackInfoReturnable<List<OrderedText>> cir
    ) {
        if (!UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return;
        }
        UiTextRole role = UiTranslationScope.role();
        cir.setReturnValue(new UiTranslationLazySplitList(
                (TextRenderer) (Object) this,
                source,
                width,
                role
        ));
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private OrderedText translate_allinone$translatedSequenceWidth(OrderedText source) {
        return UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(source, UiTranslationScope.role());
    }

    @ModifyVariable(
            method = {
                    "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;",
                    "trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private String translate_allinone$translatedPlainText(String source) {
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTranslationScope.role());
    }

    @ModifyVariable(
            method = {
                    "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
                    "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private OrderedText translate_allinone$translatedSequence(OrderedText source) {
        OrderedText visible = UiTranslationRuntime.translateFormattedCharSequenceInCurrentScreen(source, UiTranslationScope.role());
        if (visible != source) {
            UiTranslationRuntime.markFormattedSequenceHandled(visible);
        }
        return visible;
    }

    @ModifyVariable(
            method = {
                    "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private String translate_allinone$translatedStringDraw(String source) {
        return UiTranslationRuntime.translateStringAnimatedInCurrentScreen(source, UiTranslationScope.role());
    }

    @ModifyVariable(
            method = {
                    "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Text translate_allinone$translatedComponent(Text source) {
        Text visible = UiTranslationRuntime.translateComponentInCurrentScreen(source, UiTextRole.OPTION);
        UiTranslationRuntime.markFormattedSequenceHandled(visible.asOrderedText());
        return visible;
    }

    @Redirect(
            method = "getWidth(Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextHandler;getWidth(Ljava/lang/String;)F"
            ),
            require = 0
    )
    private float translate_allinone$translatedWidth(TextHandler splitter, String source) {
        if (!UiTranslationScope.isActive() || UiTranslationScope.isInternal()) {
            return splitter.getWidth(source);
        }

        UiScreenAdapter adapter = UiTranslationScope.adapter();
        if (adapter != null && "noammaddons".equals(adapter.modId())
                && isNoammAddonsSortingWidthCall()) {
            return splitter.getWidth(source);
        }
        String visible = UiTranslationRuntime.translateStringInCurrentScreen(source, UiTranslationScope.role());
        return splitter.getWidth(visible);
    }

    private static boolean isNoammAddonsSortingWidthCall() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .limit(16)
                        .anyMatch(frame -> {
                            String className = frame.getDeclaringClass().getName();
                            String methodName = frame.getMethodName();
                            if ("com.github.noamm9.ui.clickgui.Panel".equals(className)
                                    && "getSorting".equals(methodName)) {
                                return true;
                            }
                            return "com.github.noamm9.features.FeatureManager".equals(className)
                                    && "createFeatureList".equals(methodName);
                        }));
    }
}
