package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
public record TextDisplayTranslationSnapshot(
        Display.TextDisplay.TextRenderState originalState,
        Component displayedText
) {
    public boolean isTranslated() {
        return originalState != null && displayedText != null && !displayedText.equals(originalState.text());
    }

    public Display.TextDisplay.TextRenderState displayedState() {
        if (!isTranslated()) {
            return originalState;
        }
        return new Display.TextDisplay.TextRenderState(
                displayedText,
                originalState.lineWidth(),
                originalState.textOpacity(),
                originalState.backgroundColor(),
                originalState.flags()
        );
    }
}
