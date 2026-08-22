package com.alexeys.translate_allinone.utils.translate;

import java.util.List;

public final class WynnDialogueHudPresenter {
    private WynnDialogueHudPresenter() {
    }

    public static void present(WynnDialoguePresentation presentation) {
        if (presentation == null) {
            return;
        }
        HudRenderRequest request = toRenderRequest(presentation);
        WynnDialogueHudRenderer.showDialogue(
                request.pageInfo(),
                request.npcName(),
                request.originalDialogue(),
                request.displayedDialogue(),
                request.dialoguePending(),
                request.dialogueAnimationKey(),
                request.optionsText(),
                request.optionsPending(),
                request.optionAnimationKeys(),
                request.errorMessage()
        );
    }

    public static void clear() {
        WynnDialogueHudRenderer.clear();
    }

    static HudRenderRequest toRenderRequest(WynnDialoguePresentation presentation) {
        return new HudRenderRequest(
                presentation.pageInfo(),
                presentation.displayedNpcName(),
                presentation.originalDialogue(),
                presentation.displayedDialogue(),
                presentation.dialoguePending(),
                presentation.dialogueAnimationKey(),
                presentation.displayedOptionsText(),
                presentation.optionsPending(),
                presentation.optionAnimationKeys(),
                presentation.errorMessage()
        );
    }

    record HudRenderRequest(
            String pageInfo,
            String npcName,
            String originalDialogue,
            String displayedDialogue,
            boolean dialoguePending,
            String dialogueAnimationKey,
            String optionsText,
            boolean optionsPending,
            List<String> optionAnimationKeys,
            String errorMessage
    ) {
    }
}
