package com.alexeys.translate_allinone.utils.translate;

import java.util.List;

public record WynnDialoguePresentation(
        long observedNonce,
        String pageInfo,
        String originalNpcName,
        String displayedNpcName,
        String originalDialogue,
        String displayedDialogue,
        List<OptionPresentation> options,
        boolean dialoguePending,
        boolean optionsPending,
        String dialogueAnimationKey,
        List<String> optionAnimationKeys,
        String errorMessage
) {
    public WynnDialoguePresentation {
        pageInfo = safe(pageInfo);
        originalNpcName = safe(originalNpcName);
        displayedNpcName = safe(displayedNpcName);
        originalDialogue = safe(originalDialogue);
        displayedDialogue = safe(displayedDialogue);
        options = options == null ? List.of() : List.copyOf(options);
        dialogueAnimationKey = safe(dialogueAnimationKey);
        optionAnimationKeys = optionAnimationKeys == null
                ? options.stream().map(OptionPresentation::animationKey).toList()
                : List.copyOf(optionAnimationKeys);
        errorMessage = safe(errorMessage);
    }

    public String originalOptionsText() {
        return String.join("\n", options.stream().map(OptionPresentation::originalText).toList());
    }

    public String displayedOptionsText() {
        return String.join("\n", options.stream().map(OptionPresentation::displayedText).toList());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record OptionPresentation(
            int index,
            String originalText,
            String displayedText,
            boolean pending,
            String animationKey
    ) {
        public OptionPresentation {
            originalText = safe(originalText);
            displayedText = safe(displayedText);
            animationKey = safe(animationKey);
        }
    }
}
