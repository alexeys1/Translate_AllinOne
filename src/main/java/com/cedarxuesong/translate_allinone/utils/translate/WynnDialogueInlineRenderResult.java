package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.text.Text;

public record WynnDialogueInlineRenderResult(Text text, Outcome outcome) {
    public WynnDialogueInlineRenderResult {
        text = text == null ? Text.empty() : text;
        outcome = outcome == null ? Outcome.REJECTED : outcome;
    }

    public enum Outcome {
        TRANSLATED,
        MASKED,
        ORIGINAL,
        REJECTED
    }
}
