package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.NvgAnimatedTextRenderer;
import com.alexeys.translate_allinone.utils.translate.UiOdinFontFallback;
import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "foo.starred.athen.utils.nvg.NVGRenderer",
        remap = false
)
public abstract class UiTranslationAthenNvgMixin {
    @Redirect(
            method = {"drawText", "drawTextWrapped", "getTextWidth", "getWrappedTextWidth", "getWrappedTextHeight"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgText(JFFLjava/lang/CharSequence;)F"
            ),
            require = 0,
            remap = false
    )
    private static float translate_allinone$redirectNvgText(long vg, float x, float y, CharSequence text) {
        return NvgAnimatedTextRenderer.drawText(vg, x, y, translate_allinone$translateAthenAnimated(text == null ? null : text.toString(), UiTextRole.OPTION));
    }

    @Redirect(
            method = {"drawText", "drawTextWrapped", "getTextWidth", "getWrappedTextWidth", "getWrappedTextHeight"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBounds(JFFLjava/lang/CharSequence;[F)F"
            ),
            require = 0,
            remap = false
    )
    private static float translate_allinone$redirectNvgTextBounds(long vg, float x, float y, CharSequence text, float[] bounds) {
        return translate_allinone$callNvgTextBounds(vg, x, y, translate_allinone$translateAthen(text == null ? null : text.toString(), UiTextRole.OPTION), bounds);
    }

    @Redirect(
            method = {"drawText", "drawTextWrapped", "getTextWidth", "getWrappedTextWidth", "getWrappedTextHeight"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBox(JFFFLjava/lang/CharSequence;)V"
            ),
            require = 0,
            remap = false
    )
    private static void translate_allinone$redirectNvgTextBox(long vg, float x, float y, float rowHeight, CharSequence text) {
        NvgAnimatedTextRenderer.drawTextBox(vg, x, y, rowHeight, translate_allinone$translateAthenAnimated(text == null ? null : text.toString(), UiTextRole.DESCRIPTION));
    }

    @Redirect(
            method = {"drawText", "drawTextWrapped", "getTextWidth", "getWrappedTextWidth", "getWrappedTextHeight"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgTextBoxBounds(JFFFLjava/lang/CharSequence;[F)V"
            ),
            require = 0,
            remap = false
    )
    private static void translate_allinone$redirectNvgTextBoxBounds(long vg, float x, float y, float rowHeight, CharSequence text, float[] bounds) {
        translate_allinone$callNvgTextBoxBounds(vg, x, y, rowHeight, translate_allinone$translateAthen(text == null ? null : text.toString(), UiTextRole.DESCRIPTION), bounds);
    }

    @Redirect(
            method = {"drawText", "drawTextWrapped", "getTextWidth", "getWrappedTextWidth", "getWrappedTextHeight"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/nanovg/NanoVG;nvgFontFaceId(JI)V"
            ),
            require = 0,
            remap = false
    )
    private static void translate_allinone$attachAthenFont(long vg, int fontId) {
        UiOdinFontFallback.attachFallback("foo.starred.athen.utils.nvg.NVGRenderer", fontId);
        translate_allinone$callNvgFontFaceId(vg, fontId);
    }

    private static String translate_allinone$translateAthenAnimated(String raw, UiTextRole role) {
        UiTranslationScope.Scope scope = UiTranslationScope.enter("foo.starred.athen.config.ui.ClickGUI");
        try {
            String translated = UiTranslationRuntime.translateStringAnimatedInCurrentScreen(raw, role);
            return translated == null ? "" : translated;
        } finally {
            scope.close();
        }
    }

    private static String translate_allinone$translateAthen(String raw, UiTextRole role) {
        UiTranslationScope.Scope scope = UiTranslationScope.enter("foo.starred.athen.config.ui.ClickGUI");
        try {
            String translated = UiTranslationRuntime.translateStringInCurrentScreen(raw, role);
            return translated == null ? "" : translated;
        } finally {
            scope.close();
        }
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

    private static float translate_allinone$callNvgTextBounds(long vg, float x, float y, String text, float[] bounds) {
        try {
            Object result = Class.forName("org.lwjgl.nanovg.NanoVG")
                    .getMethod("nvgTextBounds", long.class, float.class, float.class, CharSequence.class, float[].class)
                    .invoke(null, vg, x, y, text, bounds);
            return ((Number) result).floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0.0f;
        }
    }

    private static void translate_allinone$callNvgTextBox(long vg, float x, float y, float rowHeight, String text) {
        try {
            Class.forName("org.lwjgl.nanovg.NanoVG")
                    .getMethod("nvgTextBox", long.class, float.class, float.class, float.class, CharSequence.class)
                    .invoke(null, vg, x, y, rowHeight, text);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void translate_allinone$callNvgTextBoxBounds(long vg, float x, float y, float rowHeight, String text, float[] bounds) {
        try {
            Class.forName("org.lwjgl.nanovg.NanoVG")
                    .getMethod("nvgTextBoxBounds", long.class, float.class, float.class, float.class, CharSequence.class, float[].class)
                    .invoke(null, vg, x, y, rowHeight, text, bounds);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void translate_allinone$callNvgFontFaceId(long vg, int fontId) {
        try {
            Class.forName("org.lwjgl.nanovg.NanoVG")
                    .getMethod("nvgFontFaceId", long.class, int.class)
                    .invoke(null, vg, fontId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
