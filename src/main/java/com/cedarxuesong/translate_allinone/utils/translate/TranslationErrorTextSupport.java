package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TranslationErrorTextSupport {
    private static final String NO_ROUTED_MODEL_ERROR = "No routed model selected";
    private static final String NO_ROUTED_MODEL_ERROR_KEY = "text.translate_allinone.translation.error.no_routed_model_reason";
    private static final String REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR = "Remote host terminated the handshake";
    private static final String REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR_KEY = "text.translate_allinone.translation.error.remote_host_terminated_handshake";

    private TranslationErrorTextSupport() {
    }

    public static Component localizeReason(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return Component.literal("");
        }
        if (NO_ROUTED_MODEL_ERROR.equals(errorMessage)) {
            return Component.translatable(NO_ROUTED_MODEL_ERROR_KEY);
        }
        int handshakeErrorStart = errorMessage.indexOf(REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR);
        if (handshakeErrorStart >= 0) {
            return replaceHandshakeError(errorMessage, handshakeErrorStart);
        }
        return Component.literal(errorMessage);
    }

    private static Component replaceHandshakeError(String errorMessage, int handshakeErrorStart) {
        MutableComponent localized = Component.literal(errorMessage.substring(0, handshakeErrorStart));
        localized.append(Component.translatable(REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR_KEY));
        int handshakeErrorEnd = handshakeErrorStart + REMOTE_HOST_TERMINATED_HANDSHAKE_ERROR.length();
        if (handshakeErrorEnd < errorMessage.length()) {
            localized.append(Component.literal(errorMessage.substring(handshakeErrorEnd)));
        }
        return localized;
    }
}
