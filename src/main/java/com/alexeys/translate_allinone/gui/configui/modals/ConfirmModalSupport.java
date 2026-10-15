package com.alexeys.translate_allinone.gui.configui.modals;

import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import com.alexeys.translate_allinone.utils.update.UpdateCheckManager;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

public final class ConfirmModalSupport {
    private ConfirmModalSupport() {
    }

    public enum WarningConfirmation {
        PROMPT_EDITOR
    }

    public static void addResetConfirm(
            int screenWidth,
            int screenHeight,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            Style style,
            Runnable onDismiss,
            Runnable onReset
    ) {
        UiRect modalRect = ConfigUiModalSupport.resetConfirmModalRect(screenWidth, screenHeight);
        int buttonWidth = 92;
        int buttonHeight = 20;
        int buttonGap = 10;
        int buttonY = modalRect.bottom() - 30;
        int resetX = modalRect.right() - 16 - buttonWidth;
        int cancelX = resetX - buttonGap - buttonWidth;

        actionBlockAdder.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.cancel"),
                onDismiss,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                resetX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.reset"),
                onReset,
                style.colorBlockDanger(),
                style.colorBlockDangerHover(),
                style.colorText(),
                true
        );
    }

    public static void addUpdateNotice(
            int screenWidth,
            int screenHeight,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            Style style,
            Runnable onDismiss
    ) {
        UiRect modalRect = ConfigUiModalSupport.updateNoticeModalRect(screenWidth, screenHeight);
        int buttonWidth = 108;
        int buttonHeight = 20;
        int buttonGap = 10;
        int buttonY = modalRect.bottom() - 30;
        int openX = modalRect.right() - 16 - buttonWidth;
        int cancelX = openX - buttonGap - buttonWidth;

        actionBlockAdder.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.cancel"),
                onDismiss,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                openX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.open_release"),
                () -> {
                    UpdateCheckManager.openLatestReleasePage();
                    onDismiss.run();
                },
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );
    }

    public static void addUnsavedChanges(
            int screenWidth,
            int screenHeight,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            Style style,
            Runnable onDismiss,
            Runnable onSaveAndClose,
            Runnable onDiscardAndClose
    ) {
        UiRect modalRect = ConfigUiModalSupport.unsavedChangesConfirmModalRect(screenWidth, screenHeight);
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = modalRect.bottom() - 30;
        int cancelX = modalRect.right() - 16 - buttonWidth;
        int discardX = cancelX - buttonGap - buttonWidth;
        int saveX = discardX - buttonGap - buttonWidth;

        actionBlockAdder.add(
                saveX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.save_and_close"),
                onSaveAndClose,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                discardX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.close_without_saving"),
                onDiscardAndClose,
                style.colorBlockDanger(),
                style.colorBlockDangerHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.cancel"),
                onDismiss,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );
    }

    public static void addWarningConfirm(
            int screenWidth,
            int screenHeight,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            Style style,
            Runnable onDismiss,
            Runnable onContinue
    ) {
        UiRect rect = ConfigUiModalSupport.promptEditorWarningRect(screenWidth, screenHeight);
        int buttonsY = rect.y + rect.height - 32;
        int half = (rect.width - 44) / 2;
        int leftX = rect.x + 20;
        int rightX = leftX + half + 4;

        actionBlockAdder.add(
                leftX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.cancel"),
                onDismiss,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                rightX,
                buttonsY,
                half,
                20,
                () -> translator.t("button.continue"),
                onContinue,
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );
    }

    public static void renderResetMessage(
            GuiGraphicsExtractor context,
            Font font,
            int screenWidth,
            int screenHeight,
            Translator translator,
            Style style
    ) {
        UiRect modalRect = ConfigUiModalSupport.resetConfirmModalRect(screenWidth, screenHeight);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<FormattedCharSequence> lines = font.split(translator.t("modal.reset_confirm.message"), maxWidth);
        int lineY = textY;
        for (FormattedCharSequence line : lines) {
            context.text(font, line, textX, lineY, style.colorTextMuted(), false);
            lineY += font.lineHeight + 2;
        }
    }

    public static void renderUpdateNoticeMessage(
            GuiGraphicsExtractor context,
            Font font,
            int screenWidth,
            int screenHeight,
            Translator translator,
            Style style
    ) {
        UiRect modalRect = ConfigUiModalSupport.updateNoticeModalRect(screenWidth, screenHeight);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<FormattedCharSequence> lines = font.split(
                translator.t("modal.update_notice.message", UpdateCheckManager.latestVersion(), UpdateCheckManager.currentVersion()),
                maxWidth
        );
        int lineY = textY;
        for (FormattedCharSequence line : lines) {
            context.text(font, line, textX, lineY, style.colorTextMuted(), false);
            lineY += font.lineHeight + 2;
        }
    }

    public static void renderUnsavedChangesMessage(
            GuiGraphicsExtractor context,
            Font font,
            int screenWidth,
            int screenHeight,
            Translator translator,
            Style style
    ) {
        UiRect modalRect = ConfigUiModalSupport.unsavedChangesConfirmModalRect(screenWidth, screenHeight);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<FormattedCharSequence> lines = font.split(translator.t("modal.unsaved_changes.message"), maxWidth);
        int lineY = textY;
        for (FormattedCharSequence line : lines) {
            context.text(font, line, textX, lineY, style.colorTextMuted(), false);
            lineY += font.lineHeight + 2;
        }
    }

    public static void renderWarningMessage(
            GuiGraphicsExtractor context,
            Font font,
            int screenWidth,
            int screenHeight,
            Translator translator,
            Style style
    ) {
        UiRect rect = ConfigUiModalSupport.promptEditorWarningRect(screenWidth, screenHeight);
        int textX = rect.x + 16;
        int textY = rect.y + 48;
        int maxWidth = Math.max(10, rect.width - 32);
        List<FormattedCharSequence> lines = font.split(translator.t("modal.prompt_editor_warning_message"), maxWidth);
        int lineY = textY;
        for (FormattedCharSequence line : lines) {
            context.text(font, line, textX, lineY, style.colorError(), false);
            lineY += font.lineHeight + 2;
        }
    }

    public record Style(
            int colorBlock,
            int colorBlockHover,
            int colorBlockAccent,
            int colorBlockAccentHover,
            int colorBlockDanger,
            int colorBlockDangerHover,
            int colorText,
            int colorTextMuted,
            int colorError
    ) {
    }
}
