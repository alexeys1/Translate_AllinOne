package com.cedarxuesong.translate_allinone.gui.configui.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiDraw.drawOutline;

public final class ConfigUiScreenRenderSupport {
    private ConfigUiScreenRenderSupport() {
    }

    public static void renderChrome(
            DrawContext context,
            TextRenderer textRenderer,
            int screenWidth,
            int screenHeight,
            int topBarHeight,
            int leftPanelWidth,
            Text title,
            Text panelTitle,
            boolean providersSectionSelected,
            Style style
    ) {
        context.fill(0, 0, screenWidth, screenHeight, style.colorBg());
        context.fill(0, 0, screenWidth, topBarHeight, style.colorTopBar());
        context.fill(0, topBarHeight, leftPanelWidth, screenHeight, style.colorLeftPanel());
        context.fill(leftPanelWidth, topBarHeight, screenWidth, screenHeight, style.colorMainPanel());

        drawOutline(context, 0, 0, screenWidth, topBarHeight, style.colorBorder());
        drawOutline(context, 0, topBarHeight, leftPanelWidth, screenHeight - topBarHeight, style.colorBorder());
        drawOutline(context, leftPanelWidth, topBarHeight, screenWidth - leftPanelWidth, screenHeight - topBarHeight, style.colorBorder());

        context.drawText(textRenderer, title, 14, 14, style.colorText(), false);
        context.drawText(textRenderer, panelTitle, leftPanelWidth + 20, topBarHeight + 8, style.colorText(), false);

        if (providersSectionSelected) {
            // provider section groups are rendered through GroupBox controls.
        }
    }

    public static void renderModalOverlayAndShell(
            DrawContext context,
            TextRenderer textRenderer,
            int screenWidth,
            int screenHeight,
            int topBarHeight,
            boolean addProviderModalOpen,
            boolean modelSettingsModalOpen,
            boolean customParametersModalOpen,
            boolean dictionaryFilesModalOpen,
            boolean resetConfirmModalOpen,
            boolean updateNoticeModalOpen,
            boolean unsavedChangesConfirmModalOpen,
            boolean promptEditorWarningOpen,
            Text addProviderTitle,
            Text modelSettingsTitle,
            Text customParametersTitle,
            Text dictionaryFilesTitle,
            Text resetConfirmTitle,
            Text updateNoticeTitle,
            Text unsavedChangesConfirmTitle,
            Text promptEditorWarningTitle,
            Style style
    ) {
        if (!ConfigUiModalSupport.isAnyModalOpen(
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen,
                promptEditorWarningOpen
        )) {
            return;
        }

        context.fill(0, topBarHeight, screenWidth, screenHeight, style.colorModalOverlay());
        if (updateNoticeModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.updateNoticeModalRect(screenWidth, screenHeight),
                    updateNoticeTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (resetConfirmModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.resetConfirmModalRect(screenWidth, screenHeight),
                    resetConfirmTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (unsavedChangesConfirmModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.unsavedChangesConfirmModalRect(screenWidth, screenHeight),
                    unsavedChangesConfirmTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (promptEditorWarningOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.promptEditorWarningRect(screenWidth, screenHeight),
                    promptEditorWarningTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (customParametersModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.customParametersModalRect(screenWidth, screenHeight),
                    customParametersTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (dictionaryFilesModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.dictionaryFilesModalRect(screenWidth, screenHeight),
                    dictionaryFilesTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (addProviderModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.addProviderModalRect(screenWidth, screenHeight),
                    addProviderTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (modelSettingsModalOpen) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.modelSettingsModalRect(screenWidth, screenHeight),
                    modelSettingsTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        }
    }

    public static void renderStatusToast(
            DrawContext context,
            TextRenderer textRenderer,
            int screenWidth,
            int topBarHeight,
            Text statusMessage,
            int statusColor,
            long statusExpireAtMillis,
            long nowMillis,
            int toastBgColor,
            int toastBorderColor
    ) {
        if (statusMessage.getString().isEmpty() || nowMillis > statusExpireAtMillis) {
            return;
        }

        long remaining = statusExpireAtMillis - nowMillis;
        float alpha = 1.0f;
        if (remaining < 500) {
            alpha = remaining / 500.0f;
        }

        int textWidth = textRenderer.getWidth(statusMessage);
        int paddingX = 16;
        int paddingY = 8;
        int toastWidth = textWidth + paddingX * 2;
        int toastHeight = textRenderer.fontHeight + paddingY * 2;
        int toastX = (screenWidth - toastWidth) / 2;
        int toastY = topBarHeight + 12;

        int bg = applyAlpha(toastBgColor, alpha);
        int border = applyAlpha(toastBorderColor, alpha);
        int text = applyAlpha(statusColor, alpha);

        context.fill(toastX, toastY, toastX + toastWidth, toastY + toastHeight, bg);
        drawOutline(context, toastX, toastY, toastWidth, toastHeight, border);
        context.drawText(textRenderer, statusMessage, toastX + paddingX, toastY + paddingY, text, false);
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) ((color >> 24 & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public record Style(
            int colorBg,
            int colorTopBar,
            int colorLeftPanel,
            int colorMainPanel,
            int colorBorder,
            int colorText,
            int colorModalOverlay
    ) {
    }
}
