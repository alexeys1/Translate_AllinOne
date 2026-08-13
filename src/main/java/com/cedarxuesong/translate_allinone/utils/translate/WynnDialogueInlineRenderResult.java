package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;

public record WynnDialogueInlineRenderResult(Component text, Outcome outcome) {
    public WynnDialogueInlineRenderResult {
        text = text == null ? Component.empty() : text;
        outcome = outcome == null ? Outcome.REJECTED : outcome;
    }

    public enum Outcome {
        TRANSLATED,
        ANIMATING,
        MASKED,
        ORIGINAL,
        REJECTED
    }
}
