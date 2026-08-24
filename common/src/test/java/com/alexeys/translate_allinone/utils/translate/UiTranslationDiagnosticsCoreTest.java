package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UiTranslationDiagnosticsCoreTest {
    @Test
    void recordsSameScreenEventOnlyOnce() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();

        UiTranslationDiagnosticsCore.ScreenEvent first = core.recordScreen(
                "net.minecraft.client.gui.screens.TitleScreen",
                "minecraft",
                "component"
        );

        UiTranslationDiagnosticsCore.ScreenEvent duplicate = core.recordScreen(
                "net.minecraft.client.gui.screens.TitleScreen",
                "minecraft",
                "component"
        );

        assertNotNull(first);
        assertEquals("net.minecraft.client.gui.screens.TitleScreen", first.className());
        assertEquals("minecraft", first.modId());
        assertEquals("component", first.backend());
        assertNull(duplicate);
    }

    @Test
    void recordsSameTextEventOnlyOnce() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();
        UiTextFilter.Decision decision = eligibleDecision();

        UiTranslationDiagnosticsCore.TextEvent first = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                decision,
                UiTranslationStatus.ORIGINAL,
                "Settings"
        );

        UiTranslationDiagnosticsCore.TextEvent duplicate = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                decision,
                UiTranslationStatus.ORIGINAL,
                "Settings"
        );

        assertNotNull(first);
        assertNull(duplicate);
    }

    @Test
    void differentReasonProducesDifferentEvent() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();
        UiTextFilter.Decision noLetters = new UiTextFilter.Decision(
                false,
                "",
                UiTextRole.OPTION,
                UiTextFilter.Reason.NO_LETTERS
        );
        UiTextFilter.Decision command = new UiTextFilter.Decision(
                false,
                "",
                UiTextRole.OPTION,
                UiTextFilter.Reason.COMMAND
        );

        UiTranslationDiagnosticsCore.TextEvent first = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                noLetters,
                UiTranslationStatus.INELIGIBLE,
                "\uE000"
        );

        UiTranslationDiagnosticsCore.TextEvent second = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                command,
                UiTranslationStatus.INELIGIBLE,
                "/help"
        );

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("no_letters", first.reason());
        assertEquals("command", second.reason());
    }

    @Test
    void differentStatusProducesDifferentEvent() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();
        UiTextFilter.Decision decision = eligibleDecision();

        UiTranslationDiagnosticsCore.TextEvent pending = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                decision,
                UiTranslationStatus.PENDING,
                "Settings"
        );

        UiTranslationDiagnosticsCore.TextEvent translated = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                decision,
                UiTranslationStatus.TRANSLATED,
                "Settings"
        );

        assertNotNull(pending);
        assertNotNull(translated);
        assertEquals("pending", pending.status());
        assertEquals("translated", translated.status());
    }

    @Test
    void userInputEventNeverLeaksSourceText() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();
        UiTextFilter.Decision userInput = new UiTextFilter.Decision(
                false,
                "",
                UiTextRole.OPTION,
                UiTextFilter.Reason.USER_INPUT
        );

        UiTranslationDiagnosticsCore.TextEvent event = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                userInput,
                UiTranslationStatus.INELIGIBLE,
                "MySecretInput"
        );

        assertNotNull(event);
        assertEquals("<user-input>", event.preview());
        assertFalse(event.preview().contains("MySecretInput"));
        assertFalse(event.modId().contains("MySecretInput"));
        assertFalse(event.screenId().contains("MySecretInput"));
        assertFalse(event.role().contains("MySecretInput"));
        assertFalse(event.reason().contains("MySecretInput"));
        assertFalse(event.status().contains("MySecretInput"));
        assertFalse(event.backend().contains("MySecretInput"));
    }

    @Test
    void collapsesWhitespaceAndTrimsPreview() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();

        UiTranslationDiagnosticsCore.TextEvent event = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                eligibleDecision(),
                UiTranslationStatus.ORIGINAL,
                "   Settings \n\t  panel  "
        );

        assertNotNull(event);
        assertEquals("Settings panel", event.preview());
    }

    @Test
    void blankSourcePreviewIsEmptyMarker() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();

        UiTranslationDiagnosticsCore.TextEvent event = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                eligibleDecision(),
                UiTranslationStatus.ORIGINAL,
                "   "
        );

        assertNotNull(event);
        assertEquals("<empty>", event.preview());
    }

    @Test
    void truncatesPreviewAtLimitWithEllipsis() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();
        String source = "x".repeat(200);

        UiTranslationDiagnosticsCore.TextEvent event = core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                eligibleDecision(),
                UiTranslationStatus.ORIGINAL,
                source
        );

        assertNotNull(event);
        assertEquals(161, event.preview().length());
        assertEquals("x".repeat(160) + "…", event.preview());
    }

    @Test
    void resetAllowsSameEventAgain() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();

        assertNotNull(core.recordScreen("TitleScreen", "minecraft", "component"));
        core.reset();
        assertNotNull(core.recordScreen("TitleScreen", "minecraft", "component"));

        assertNotNull(core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                eligibleDecision(),
                UiTranslationStatus.ORIGINAL,
                "Settings"
        ));
        core.reset();
        assertNotNull(core.recordText(
                "minecraft",
                "title-screen",
                "component",
                UiTextRole.OPTION,
                eligibleDecision(),
                UiTranslationStatus.ORIGINAL,
                "Settings"
        ));
    }

    @Test
    void screenEventDistinguishesBackendAndMod() {
        UiTranslationDiagnosticsCore core = new UiTranslationDiagnosticsCore();

        UiTranslationDiagnosticsCore.ScreenEvent first = core.recordScreen(
                "GenericMessageScreen",
                "minecraft",
                "component"
        );

        UiTranslationDiagnosticsCore.ScreenEvent second = core.recordScreen(
                "GenericMessageScreen",
                "wynntils",
                "component"
        );

        UiTranslationDiagnosticsCore.ScreenEvent third = core.recordScreen(
                "GenericMessageScreen",
                "minecraft",
                "nanovg"
        );

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
    }

    private static UiTextFilter.Decision eligibleDecision() {
        return new UiTextFilter.Decision(true, "Settings", UiTextRole.OPTION, null);
    }
}
