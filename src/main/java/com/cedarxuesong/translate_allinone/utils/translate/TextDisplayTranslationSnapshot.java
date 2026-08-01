package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;

public record TextDisplayTranslationSnapshot(
        DisplayEntity.TextDisplayEntity.Data originalState,
        Text displayedText
) {
    public boolean isTranslated() {
        return originalState != null && displayedText != null && !displayedText.equals(originalState.text());
    }

    public DisplayEntity.TextDisplayEntity.Data displayedState() {
        if (!isTranslated()) {
            return originalState;
        }
        return new DisplayEntity.TextDisplayEntity.Data(
                displayedText,
                originalState.lineWidth(),
                originalState.textOpacity(),
                originalState.backgroundColor(),
                originalState.flags()
        );
    }
}
