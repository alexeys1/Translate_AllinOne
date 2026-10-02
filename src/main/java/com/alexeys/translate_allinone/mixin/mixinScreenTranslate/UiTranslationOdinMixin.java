package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.NvgAnimatedTextRenderer;
import com.alexeys.translate_allinone.utils.translate.UiOdinFontFallback;
import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.odtheking.odin.utils.ui.rendering.NVGRenderer",
        remap = false
)
public abstract class UiTranslationOdinMixin {
    @Redirect(
            method = {"text", "textWidth", "drawWrappedString", "wrappedTextBounds"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgText(JFFLjava/lang/CharSequence;)F"
            ),
            require = 0,
            remap = false
    )
    private static float translate_allinone$redirectNvgText(long vg, float x, float y, CharSequence text) {
        String raw = text == null ? null : text.toString();
        String translated;
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter("com.odtheking.odin.clickgui.ClickGUI")) {
            translated = UiTranslationRuntime.translateStringAnimatedInCurrentScreen(raw, UiTextRole.OPTION);
        }
        return NvgAnimatedTextRenderer.drawText(vg, x, y, translated == null ? "" : translated);
    }

    @Redirect(
            method = "textShadow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgText(JFFLjava/lang/CharSequence;)F"
            ),
            require = 0,
            remap = false
    )
    private static float translate_allinone$redirectNvgTextShadow(long vg, float x, float y, CharSequence text) {
        String raw = text == null ? null : text.toString();
        String translated;
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter("com.odtheking.odin.clickgui.ClickGUI")) {
            translated = UiTranslationRuntime.translateStringInCurrentScreen(raw, UiTextRole.OPTION);
        }
        return translate_allinone$callNvgText(vg, x, y, translated == null ? "" : translated);
    }

    @Redirect(
            method = {"text", "textWidth", "drawWrappedString", "wrappedTextBounds"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBounds(JFFLjava/lang/CharSequence;[F)F"
            ),
            require = 0,
            remap = false
    )
    private static float translate_allinone$redirectNvgTextBounds(long vg, float x, float y, CharSequence text, float[] bounds) {
        String raw = text == null ? null : text.toString();
        String translated;
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter("com.odtheking.odin.clickgui.ClickGUI")) {
            translated = UiTranslationRuntime.translateStringInCurrentScreen(raw, UiTextRole.OPTION);
        }
        return NvgAnimatedTextRenderer.textBounds(vg, x, y, translated == null ? "" : translated, bounds);
    }

    @Redirect(
            method = {"text", "textWidth", "drawWrappedString", "wrappedTextBounds"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBox(JFFFLjava/lang/CharSequence;)V"
            ),
            require = 0,
            remap = false
    )
    private static void translate_allinone$redirectNvgTextBox(long vg, float x, float y, float rowHeight, CharSequence text) {
        String raw = text == null ? null : text.toString();
        String translated;
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter("com.odtheking.odin.clickgui.ClickGUI")) {
            translated = UiTranslationRuntime.translateStringAnimatedInCurrentScreen(raw, UiTextRole.DESCRIPTION);
        }
        NvgAnimatedTextRenderer.drawTextBox(vg, x, y, rowHeight, translated == null ? "" : translated);
    }

    @Redirect(
            method = {"text", "textWidth", "drawWrappedString", "wrappedTextBounds"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBoxBounds(JFFFLjava/lang/CharSequence;[F)V"
            ),
            require = 0,
            remap = false
    )
    private static void translate_allinone$redirectNvgTextBoxBounds(long vg, float x, float y, float rowHeight, CharSequence text, float[] bounds) {
        String raw = text == null ? null : text.toString();
        String translated;
        try (UiTranslationScope.Scope scope = UiTranslationScope.enter("com.odtheking.odin.clickgui.ClickGUI")) {
            translated = UiTranslationRuntime.translateStringInCurrentScreen(raw, UiTextRole.DESCRIPTION);
        }
        NvgAnimatedTextRenderer.textBoxBounds(vg, x, y, rowHeight, translated == null ? "" : translated, bounds);
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

    private static float translate_allinone$callNvgText(long vg, float x, float y, String text) {
        try {
            Object result = Class.forName("org.lwjgl.nanovg.NanoVG")
                    .getMethod("nvgText", long.class, float.class, float.class, CharSequence.class)
                    .invoke(null, vg, x, y, text);
            return ((Number) result).floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0.0f;
        }
    }

}
