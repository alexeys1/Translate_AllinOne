package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SignRenderRuntimeProbe {
    private static final AtomicBoolean SUBMIT_SIGN_TEXT_HIT = new AtomicBoolean();
    private static final AtomicBoolean BBE_SUBMIT_SIGN_TEXT_HIT = new AtomicBoolean();
    private static final AtomicBoolean GET_RENDER_MESSAGES_HIT = new AtomicBoolean();
    private static final AtomicBoolean TRANSLATED_LINES_APPLIED = new AtomicBoolean();

    private SignRenderRuntimeProbe() {
    }

    public static void submitSignTextHit() {
        logOnce(
                SUBMIT_SIGN_TEXT_HIT,
                "[Sign] Render hook active: AbstractSignRenderer#submitSignText."
        );
    }

    public static void betterBlockEntitiesSubmitSignTextHit() {
        logOnce(
                BBE_SUBMIT_SIGN_TEXT_HIT,
                "[Sign] Render hook active: Better Block Entities BBEAbstractSignRenderer#submitSignText."
        );
    }

    public static void getRenderMessagesHit() {
        logOnce(
                GET_RENDER_MESSAGES_HIT,
                "[Sign] Final text hook active: SignText#getRenderMessages."
        );
    }

    public static void translatedLinesApplied() {
        logOnce(
                TRANSLATED_LINES_APPLIED,
                "[Sign] Registered translated lines reached the final sign renderer."
        );
    }

    private static void logOnce(AtomicBoolean logged, String message) {
        if (logged.compareAndSet(false, true)) {
            Translate_AllinOne.LOGGER.info(message);
        }
    }
}
