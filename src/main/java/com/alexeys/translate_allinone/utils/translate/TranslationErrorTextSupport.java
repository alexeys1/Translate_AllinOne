package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class TranslationErrorTextSupport {
    private static final String NO_ROUTED_MODEL_ERROR = "No routed model selected";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model_reason";
    private static final String REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR = "Remote host terminated the handshake";
    private static final String REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR_KEY = "text.translate_allinone.translation.error.remote_host_terminated_handshake";

    private TranslationErrorTextSupport() {
    }

    public static Text localizeReason(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return Text.literal("");
        }
        if (NO_ROUTED_MODEL_ERROR.equals(errorMessage)) {
            return Text.translatable(NO_ROUTED_MODEL_ERROR_KEY);
        }
        int handshakeErrorStart = errorMessage.indexOf(REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR);
        if (handshakeErrorStart >= 0) {
            return replaceHandshakeError(errorMessage, handshakeErrorStart);
        }
        return Text.literal(errorMessage);
    }

    private static Text replaceHandshakeError(String errorMessage, int handshakeErrorStart) {
        MutableText localized = Text.literal(errorMessage.substring(0, handshakeErrorStart));
        localized.append(Text.translatable(REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR_KEY));
        int handshakeErrorEnd = handshakeErrorStart + REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR.length();
        if (handshakeErrorEnd < errorMessage.length()) {
            localized.append(Text.literal(errorMessage.substring(handshakeErrorEnd)));
        }
        return localized;
    }
}
