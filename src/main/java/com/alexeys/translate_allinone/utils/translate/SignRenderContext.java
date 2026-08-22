package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;

public final class SignRenderContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private SignRenderContext() {
    }

    public static void enter(SignBlockEntity sign, TextRenderer textRenderer) {
        CURRENT.set(new Frame(sign, SignTranslationSupport.resolveForRender(sign, textRenderer), CURRENT.get()));
    }

    public static void exit() {
        Frame frame = CURRENT.get();
        if (frame != null) {
            CURRENT.set(frame.parent());
        }
    }

    public static SignTranslationSupport.RenderedSignText rendered(SignBlockEntity sign) {
        Frame frame = CURRENT.get();
        return frame != null && frame.sign() == sign ? frame.rendered() : null;
    }

    private record Frame(SignBlockEntity sign, SignTranslationSupport.RenderedSignText rendered, Frame parent) {
    }
}