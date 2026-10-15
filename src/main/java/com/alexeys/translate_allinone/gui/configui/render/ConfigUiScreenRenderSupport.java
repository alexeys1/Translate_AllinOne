package com.alexeys.translate_allinone.gui.configui.render;

import static com.alexeys.translate_allinone.gui.configui.render.ConfigUiDraw.drawOutline;

import com.alexeys.translate_allinone.utils.config.ui.ConfigUiModalState;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ConfigUiScreenRenderSupport {
    private ConfigUiScreenRenderSupport() {
    }

    public static void renderChrome(
            GuiGraphicsExtractor context,
            Font textRenderer,
            int screenWidth,
            int screenHeight,
            int topBarHeight,
            int leftPanelWidth,
            Component title,
            Component panelTitle,
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

        context.text(textRenderer, title, 14, 14, style.colorText(), false);
        context.text(textRenderer, panelTitle, leftPanelWidth + 20, topBarHeight + 8, style.colorText(), false);

        if (providersSectionSelected) {
            // provider section groups are rendered through GroupBox controls.
        }
    }

    public static void renderModalOverlayAndShell(
            GuiGraphicsExtractor context,
            Font textRenderer,
            int screenWidth,
            int screenHeight,
            int topBarHeight,
            ConfigUiModalState state,
            Component addProviderTitle,
            Component modelSettingsTitle,
            Component customParametersTitle,
            Component modelTemperatureTitle,
            Component dictionaryFilesTitle,
            Component resetConfirmTitle,
            Component updateNoticeTitle,
            Component unsavedChangesConfirmTitle,
            Component promptEditorWarningTitle,
            Style style
    ) {
        if (!state.isAnyOpen()) {
            return;
        }

        context.fill(0, topBarHeight, screenWidth, screenHeight, style.colorModalOverlay());
        if (state.updateNotice()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.updateNoticeModalRect(screenWidth, screenHeight),
                    updateNoticeTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.resetConfirm()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.resetConfirmModalRect(screenWidth, screenHeight),
                    resetConfirmTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.unsavedChanges()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.unsavedChangesConfirmModalRect(screenWidth, screenHeight),
                    unsavedChangesConfirmTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.promptEditorWarning()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.promptEditorWarningRect(screenWidth, screenHeight),
                    promptEditorWarningTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.customParameters()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.customParametersModalRect(screenWidth, screenHeight),
                    customParametersTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.modelTemperature()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.modelTemperatureModalRect(screenWidth, screenHeight),
                    modelTemperatureTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.dictionaryFiles()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.dictionaryFilesModalRect(screenWidth, screenHeight),
                    dictionaryFilesTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.addProvider()) {
            ConfigUiModalSupport.renderModalShell(
                    context,
                    textRenderer,
                    ConfigUiModalSupport.addProviderModalRect(screenWidth, screenHeight),
                    addProviderTitle,
                    style.colorMainPanel(),
                    style.colorBorder(),
                    style.colorText()
            );
        } else if (state.modelSettings()) {
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
            GuiGraphicsExtractor context,
            Font textRenderer,
            int screenWidth,
            int topBarHeight,
            Component statusMessage,
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

        int textWidth = textRenderer.width(statusMessage);
        int paddingX = 16;
        int paddingY = 8;
        int toastWidth = textWidth + paddingX * 2;
        int toastHeight = textRenderer.lineHeight + paddingY * 2;
        int toastX = (screenWidth - toastWidth) / 2;
        int toastY = topBarHeight + 12;

        int bg = applyAlpha(toastBgColor, alpha);
        int border = applyAlpha(toastBorderColor, alpha);
        int text = applyAlpha(statusColor, alpha);

        context.fill(toastX, toastY, toastX + toastWidth, toastY + toastHeight, bg);
        drawOutline(context, toastX, toastY, toastWidth, toastHeight, border);
        context.text(textRenderer, statusMessage, toastX + paddingX, toastY + paddingY, text, false);
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
