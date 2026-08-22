package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.Text;

final class WynnDialogueFontExtractor {
    private WynnDialogueFontExtractor() {
    }

    static FontExtractionResult extract(Text text) {
        WynnDialogueTextTemplate template = WynnDialogueTextTemplateParser.parse(text);
        if (template == null) {
            return FontExtractionResult.empty();
        }
        return new FontExtractionResult(
                true,
                template.npcName(),
                template.dialogue(),
                template.optionsText()
        );
    }

    record FontExtractionResult(
            boolean matched,
            String npcName,
            String dialogue,
            String optionsText
    ) {
        private static FontExtractionResult empty() {
            return new FontExtractionResult(false, "", "", "");
        }
    }
}
