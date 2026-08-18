package com.cedarxuesong.translate_allinone.mixin.mixinScreenTranslate;

import com.cedarxuesong.translate_allinone.utils.translate.UiOdinFontFallback;
import com.cedarxuesong.translate_allinone.utils.translate.UiTextRole;
import com.cedarxuesong.translate_allinone.utils.translate.UiTranslationRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.odtheking.odin.utils.ui.rendering.NVGRenderer",
        remap = false
)
public abstract class UiTranslationOdinMixin {
    @ModifyVariable(
            method = {"text", "textShadow"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String translate_allinone$translateOption(String source) {
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTextRole.OPTION);
    }

    private static boolean isOdinPanelConstructionOrSortingCall() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .limit(12)
                        .anyMatch(frame -> frame.getDeclaringClass().getName()
                                .startsWith("com.odtheking.odin.clickgui.Panel")));
    }

    @ModifyVariable(
            method = "textWidth",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String translate_allinone$translateOptionWidth(String source) {
        if (isOdinPanelConstructionOrSortingCall()) {
            return source;
        }
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTextRole.OPTION);
    }

    @ModifyVariable(
            method = {"drawWrappedString", "wrappedTextBounds"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    private String translate_allinone$translateDescription(String source) {
        return UiTranslationRuntime.translateStringInCurrentScreen(source, UiTextRole.DESCRIPTION);
    }

    @Inject(
            method = "getFontID",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void translate_allinone$attachFallbackFont(CallbackInfoReturnable<Integer> cir) {
        UiOdinFontFallback.attachFallback(cir.getReturnValue());
    }
}

