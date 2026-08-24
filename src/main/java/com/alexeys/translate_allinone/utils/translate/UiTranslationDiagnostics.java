package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;

import java.util.Locale;

final class UiTranslationDiagnostics {
    private static final UiTranslationDiagnosticsCore CORE = new UiTranslationDiagnosticsCore();

    private UiTranslationDiagnostics() {
    }

    static void recordScreen(String className, UiScreenAdapter adapter) {
        if (!isEnabled()) {
            return;
        }
        String resolvedClassName = className == null ? "" : className;
        String modId = adapter == null ? "unmatched" : adapter.modId();
        String backend = adapter == null ? "none" : adapter.backend().name().toLowerCase(Locale.ROOT);
        UiTranslationDiagnosticsCore.ScreenEvent event = CORE.recordScreen(resolvedClassName, modId, backend);
        if (event != null) {
            Translate_AllinOne.LOGGER.info(
                    "[ScreenTranslate] screen class={} mod={} backend={}",
                    event.className(),
                    event.modId(),
                    event.backend()
            );
        }
    }

    static void recordText(
            UiScreenAdapter adapter,
            UiTextRole role,
            String source,
            UiTextFilter.Decision decision,
            UiTranslationStatus status
    ) {
        if (!isEnabled() || adapter == null) {
            return;
        }
        String backend = adapter.backend().name().toLowerCase(Locale.ROOT);
        UiTranslationDiagnosticsCore.TextEvent event = CORE.recordText(
                adapter.modId(),
                adapter.screenId(),
                backend,
                role,
                decision,
                status,
                source
        );
        if (event != null) {
            Translate_AllinOne.LOGGER.info(
                    "[ScreenTranslate] text mod={} screen={} backend={} role={} reason={} status={} source={}",
                    event.modId(),
                    event.screenId(),
                    event.backend(),
                    event.role(),
                    event.reason(),
                    event.status(),
                    event.preview()
            );
        }
    }

    static void reset() {
        CORE.reset();
    }

    private static boolean isEnabled() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            OtherTranslationsConfig otherTranslations = config == null ? null : config.otherTranslations;
            return otherTranslations != null
                    && otherTranslations.debug != null
                    && otherTranslations.debug.enabled;
        } catch (RuntimeException error) {
            return false;
        }
    }
}
