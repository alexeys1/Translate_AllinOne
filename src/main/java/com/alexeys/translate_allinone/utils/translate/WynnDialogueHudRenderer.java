package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.WynnCraftConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

public final class WynnDialogueHudRenderer {
    private static final long DISPLAY_DURATION_MILLIS = 10_000L;
    private static final int PADDING = 8;
    private static final int TITLE_GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_BOX_WIDTH = 300;
    private static final int SCREEN_MARGIN = 100;
    private static final int MIN_CONTENT_WIDTH = 40;
    private static final int SCREEN_EDGE_PADDING = 4;
    private static final int BOX_BACKGROUND_COLOR = 0xB0101010;
    private static final int BOX_BORDER_COLOR = 0x90A0A0A0;
    private static final int TITLE_COLOR = 0xFFD8D8D8;
    private static final int BODY_COLOR = 0xFFFFFFFF;
    private static final int OPTION_ROW_GAP = 3;
    private static final int OPTION_ROW_PADDING_Y = 2;

    private static final String STATUS_ERROR_KEY = "text.translate_allinone.wynn.dialogue.translation_error";
    private static final String STATUS_ERROR_WITH_REASON_KEY = "text.translate_allinone.wynn.dialogue.translation_error_with_reason";
    private static final int STATUS_LINE_COLOR = 0xFFFF5555;

    private static boolean initialized;
    private static String currentPageInfo = "";
    private static String currentNpcName = "";
    private static String currentDialogue = "";
    private static String currentTranslation = "";
    private static String currentOptionsText = "";
    private static boolean currentTranslationPending;
    private static String currentAnimationKey = "";
    private static boolean currentOptionsPending;
    private static List<String> currentOptionsAnimationKeys = List.of();
    private static String currentErrorMessage = "";
    private static long displayUntil;
    private static String lastRenderedPayload = "";

    private WynnDialogueHudRenderer() {
    }

    static long getDisplayDurationMillis() {
        return DISPLAY_DURATION_MILLIS;
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(Translate_AllinOne.MOD_ID, "wynn_dialogue"),
                WynnDialogueHudRenderer::render
        );
        initialized = true;
    }

    public static synchronized void showDialogue(
            String pageInfo,
            String npcName,
            String dialogue,
            String translation,
            boolean pending,
            String animationKey
    ) {
        showDialogue(pageInfo, npcName, dialogue, translation, pending, animationKey, "", false, List.of(), "");
    }

    public static synchronized void showDialogue(
            String pageInfo,
            String npcName,
            String dialogue,
            String translation,
            boolean pending,
            String animationKey,
            String optionsText
    ) {
        showDialogue(pageInfo, npcName, dialogue, translation, pending, animationKey, optionsText, false, List.of(), "");
    }

    public static synchronized void showDialogue(
            String pageInfo,
            String npcName,
            String dialogue,
            String translation,
            boolean pending,
            String animationKey,
            String optionsText,
            boolean optionsPending,
            List<String> optionsAnimationKeys
    ) {
        showDialogue(pageInfo, npcName, dialogue, translation, pending, animationKey, optionsText, optionsPending, optionsAnimationKeys, "");
    }

    public static synchronized void showDialogue(
            String pageInfo,
            String npcName,
            String dialogue,
            String translation,
            boolean pending,
            String animationKey,
            String optionsText,
            boolean optionsPending,
            List<String> optionsAnimationKeys,
            String errorMessage
    ) {
        String safePageInfo = pageInfo == null ? "" : pageInfo;
        String safeNpcName = npcName == null ? "" : npcName;
        String safeDialogue = dialogue == null ? "" : dialogue;
        String safeTranslation = translation == null ? "" : translation.trim();
        String safeAnimationKey = animationKey == null ? "" : animationKey;
        String safeOptionsText = optionsText == null ? "" : optionsText.trim();
        List<String> safeOptionsAnimationKeys = optionsAnimationKeys == null ? List.of() : optionsAnimationKeys;
        if (safeTranslation.isEmpty()) {
            return;
        }

        if (Objects.equals(currentPageInfo, safePageInfo)
                && Objects.equals(currentNpcName, safeNpcName)
                && Objects.equals(currentDialogue, safeDialogue)
                && Objects.equals(currentTranslation, safeTranslation)
                && Objects.equals(currentOptionsText, safeOptionsText)
                && currentTranslationPending == pending
                && Objects.equals(currentAnimationKey, safeAnimationKey)
                && currentOptionsPending == optionsPending
                && Objects.equals(currentOptionsAnimationKeys, safeOptionsAnimationKeys)
                && Objects.equals(currentErrorMessage, errorMessage)) {
            return;
        }

        currentPageInfo = safePageInfo;
        currentNpcName = safeNpcName;
        currentDialogue = safeDialogue;
        currentTranslation = safeTranslation;
        currentOptionsText = safeOptionsText;
        currentTranslationPending = pending;
        currentAnimationKey = safeAnimationKey;
        currentOptionsPending = optionsPending;
        currentOptionsAnimationKeys = safeOptionsAnimationKeys;
        currentErrorMessage = errorMessage == null ? "" : errorMessage;
        displayUntil = System.currentTimeMillis() + DISPLAY_DURATION_MILLIS;
        WynnDialogueTranslationSupport.throttledDevLog(
                "hud_state_set",
                1000L,
                "hud_state_set page={} npc=\"{}\" dialogue=\"{}\" display=\"{}\" options=\"{}\" pending={} animationKey=\"{}\" optionsPending={} optionsAnimationKey=\"{}\"",
                safePageInfo,
                safeNpcName,
                safeDialogue,
                safeTranslation,
                safeOptionsText,
                pending,
                safeAnimationKey,
                optionsPending,
                safeOptionsAnimationKeys
        );
    }

    public static synchronized void clear() {
        currentPageInfo = "";
        currentNpcName = "";
        currentDialogue = "";
        currentTranslation = "";
        currentOptionsText = "";
        currentTranslationPending = false;
        currentAnimationKey = "";
        currentOptionsPending = false;
        currentOptionsAnimationKeys = List.of();
        currentErrorMessage = "";
        displayUntil = 0L;
        lastRenderedPayload = "";
    }

    public static EditorPreviewSnapshot getEditorPreviewSnapshot(
            Font textRenderer,
            int viewportWidth,
            int viewportHeight
    ) {
        return getEditorPreviewLayout(textRenderer, viewportWidth, viewportHeight).dialogue();
    }

    public static EditorPreviewLayout getEditorPreviewLayout(
            Font textRenderer,
            int viewportWidth,
            int viewportHeight
    ) {
        if (textRenderer == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return new EditorPreviewLayout(
                    new EditorPreviewSnapshot(
                            0,
                            0,
                            0,
                            0,
                            WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT,
                            WynnCraftConfig.HudConfig.DEFAULT_X_OFFSET,
                            WynnCraftConfig.HudConfig.DEFAULT_Y_OFFSET
                    ),
                    new EditorPreviewSnapshot(
                            0,
                            0,
                            0,
                            0,
                            WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT,
                            WynnCraftConfig.HudConfig.DEFAULT_X_OFFSET,
                            WynnCraftConfig.HudConfig.DEFAULT_OPTIONS_Y_OFFSET
                    )
            );
        }

        DialogueContent content = resolveEditorContent();
        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                viewportWidth,
                viewportHeight,
                content,
                resolveHudLayout()
        );
        HudLayout optionsHudLayout = resolveOptionsHudLayout();
        EditorPreviewSnapshot optionsSnapshot = new EditorPreviewSnapshot(
                0,
                0,
                0,
                0,
                optionsHudLayout.scalePercent(),
                optionsHudLayout.xOffset(),
                optionsHudLayout.yOffset()
        );
        if (content.optionsText().getString() != null && !content.optionsText().getString().isBlank()) {
            DialogueRenderData optionsRenderData = prepareOptionsRenderData(
                    textRenderer,
                    viewportWidth,
                    viewportHeight,
                    content.optionsText(),
                    optionsHudLayout
            );
            optionsSnapshot = toEditorPreviewSnapshot(optionsRenderData);
        }
        return new EditorPreviewLayout(
                toEditorPreviewSnapshot(renderData),
                optionsSnapshot
        );
    }

    public static void drawHudEditorPreview(
            GuiGraphicsExtractor drawContext,
            Font textRenderer,
            int viewportWidth,
            int viewportHeight
    ) {
        if (drawContext == null || textRenderer == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        DialogueContent content = resolveEditorContent();
        HudLayout hudLayout = resolveHudLayout();
        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                viewportWidth,
                viewportHeight,
                content,
                hudLayout
        );
        drawDialogueBox(drawContext, textRenderer, renderData, 0, 0);
        if (content.optionsText().getString() != null && !content.optionsText().getString().isBlank()) {
            DialogueRenderData optionsRenderData = prepareOptionsRenderData(
                    textRenderer,
                    viewportWidth,
                    viewportHeight,
                    content.optionsText(),
                    resolveOptionsHudLayout()
            );
            drawOptionsRows(drawContext, textRenderer, optionsRenderData, 0, 0, List.of());
        }
    }

    private static void render(GuiGraphicsExtractor drawContext, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return;
        }

        String translation;
        String optionsText;
        String pageInfo;
        String npcName;
        boolean pending;
        String animationKey;
        boolean optionsPending;
        List<String> optionsAnimationKeys;
        String errorMessage;
        long visibleUntil;
        synchronized (WynnDialogueHudRenderer.class) {
            translation = currentTranslation;
            optionsText = shouldRenderOptionsHud() ? currentOptionsText : "";
            pageInfo = currentPageInfo;
            npcName = currentNpcName;
            pending = currentTranslationPending;
            animationKey = currentAnimationKey;
            optionsPending = currentOptionsPending;
            optionsAnimationKeys = currentOptionsAnimationKeys;
            errorMessage = currentErrorMessage;
            visibleUntil = displayUntil;
        }

        if (translation == null || translation.isBlank()) {
            return;
        }

        if (shouldExpire(System.currentTimeMillis(), visibleUntil, pending, optionsPending)) {
            clear();
            return;
        }

        if (client.screen != null) {
            return;
        }

        Font textRenderer = client.font;
        HudLayout hudLayout = resolveHudLayout();
        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                drawContext.guiWidth(),
                drawContext.guiHeight(),
                new DialogueContent(
                        pageInfo,
                        npcName,
                        pending
                                ? AnimationManager.getAnimatedStyledText(Component.literal(translation), animationKey, false)
                                : Component.literal(translation),
                        Component.literal(optionsText)
                ),
                hudLayout
        );
        drawDialogueBox(drawContext, textRenderer, renderData, 0, 0);
        if (errorMessage != null && !errorMessage.isBlank()) {
            drawErrorStatusLine(drawContext, textRenderer, renderData, errorMessage);
        }
        DialogueRenderData optionsRenderData = null;
        if (optionsText != null && !optionsText.isBlank()) {
            optionsRenderData = prepareOptionsRenderData(
                    textRenderer,
                    drawContext.guiWidth(),
                    drawContext.guiHeight(),
                    Component.literal(optionsText),
                    resolveOptionsHudLayout()
            );
            drawOptionsRows(drawContext, textRenderer, optionsRenderData, 0, 0, optionsAnimationKeys);
        }

        String renderPayload = pageInfo + "\n" + npcName + "\n" + translation
                + "\n" + optionsText
                + "\n" + pending
                + "\n" + animationKey
                + "\n" + optionsPending
                + "\n" + (optionsAnimationKeys == null ? "" : String.join(",", optionsAnimationKeys))
                + "\n" + errorMessage
                + "\n" + renderData.hudLayout().scalePercent()
                + "\n" + renderData.hudLayout().xOffset()
                + "\n" + renderData.hudLayout().yOffset()
                + "\n" + (optionsRenderData == null ? "" : optionsRenderData.hudLayout().scalePercent())
                + "\n" + (optionsRenderData == null ? "" : optionsRenderData.hudLayout().xOffset())
                + "\n" + (optionsRenderData == null ? "" : optionsRenderData.hudLayout().yOffset());
        if (!renderPayload.equals(lastRenderedPayload)) {
            WynnDialogueTranslationSupport.throttledDevLog(
                    "hud_rendered",
                    1000L,
                    "hud_rendered x={} y={} width={} height={} scale={} xOffset={} yOffset={} title=\"{}\" options=\"{}\"",
                    renderData.x(),
                    renderData.y(),
                    renderData.scaledBoxWidth(),
                    renderData.scaledBoxHeight(),
                    renderData.hudLayout().scalePercent(),
                    renderData.hudLayout().xOffset(),
                    renderData.hudLayout().yOffset(),
                    renderData.title().getString(),
                    optionsText
            );
            lastRenderedPayload = renderPayload;
        }
    }

    static boolean shouldExpire(
            long nowMillis,
            long visibleUntil,
            boolean dialoguePending,
            boolean optionsPending
    ) {
        return !dialoguePending && !optionsPending && nowMillis > visibleUntil;
    }

    private static DialogueRenderData prepareDialogueRenderData(
            Font textRenderer,
            int viewportWidth,
            int viewportHeight,
            DialogueContent content,
            HudLayout hudLayout
    ) {
        Component title = buildTitle(content.pageInfo(), content.npcName());
        int widthBudget = (int) Math.floor((viewportWidth - SCREEN_MARGIN) / hudLayout.scale()) - PADDING * 2;
        int maxContentWidth = Math.min(MAX_BOX_WIDTH, Math.max(MIN_CONTENT_WIDTH, widthBudget));
        List<FormattedCharSequence> wrappedLines = wrapTextLines(textRenderer, content.translation(), maxContentWidth);
        if (wrappedLines.isEmpty()) {
            wrappedLines = List.of(content.translation().getVisualOrderText());
        }

        int contentWidth = textRenderer.width(title.getVisualOrderText());
        for (FormattedCharSequence line : wrappedLines) {
            contentWidth = Math.max(contentWidth, textRenderer.width(line));
        }

        int boxWidth = Math.min(maxContentWidth + PADDING * 2, contentWidth + PADDING * 2);
        int boxHeight = PADDING * 2 + 9 + TITLE_GAP + wrappedLines.size() * LINE_HEIGHT;
        int scaledBoxWidth = Math.max(1, Math.round(boxWidth * hudLayout.scale()));
        int scaledBoxHeight = Math.max(1, Math.round(boxHeight * hudLayout.scale()));
        int anchorCenterX = viewportWidth / 2 + hudLayout.xOffset();
        int anchorCenterY = Math.round(viewportHeight * 0.55F) + hudLayout.yOffset();
        int x = clamp(
                anchorCenterX - scaledBoxWidth / 2,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, viewportWidth - scaledBoxWidth - SCREEN_EDGE_PADDING)
        );
        int y = clamp(
                anchorCenterY - scaledBoxHeight / 2,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, viewportHeight - scaledBoxHeight - SCREEN_EDGE_PADDING)
        );
        return new DialogueRenderData(title, wrappedLines, boxWidth, boxHeight, scaledBoxWidth, scaledBoxHeight, x, y, hudLayout);
    }

    private static DialogueRenderData prepareOptionsRenderData(
            Font textRenderer,
            int viewportWidth,
            int viewportHeight,
            Component optionsText,
            HudLayout hudLayout
    ) {
        Component title = Component.empty();
        int widthBudget = (int) Math.floor((viewportWidth - SCREEN_MARGIN) / hudLayout.scale()) - PADDING * 2;
        int maxContentWidth = Math.min(MAX_BOX_WIDTH, Math.max(MIN_CONTENT_WIDTH, widthBudget));
        List<List<FormattedCharSequence>> optionGroups = wrapOptionsGroups(textRenderer, optionsText, maxContentWidth);
        if (optionGroups.isEmpty()) {
            optionGroups = List.of(List.of(optionsText.getVisualOrderText()));
        }

        List<String> rawSegments = new ArrayList<>();
        int contentWidth = 0;
        List<FormattedCharSequence> flatLines = new ArrayList<>();
        for (List<FormattedCharSequence> group : optionGroups) {
            StringBuilder sb = new StringBuilder();
            for (FormattedCharSequence line : group) {
                contentWidth = Math.max(contentWidth, textRenderer.width(line));
                flatLines.add(line);
                line.accept((charIndex, style, codePoint) -> {
                    sb.appendCodePoint(codePoint);
                    return true;
                });
            }
            rawSegments.add(sb.toString());
        }

        int boxWidth = Math.min(maxContentWidth + PADDING * 2, contentWidth + PADDING * 2);
        int totalVisualLines = flatLines.size();
        int boxHeight = optionsBoxHeight(optionGroups.size(), totalVisualLines);
        int scaledBoxWidth = Math.max(1, Math.round(boxWidth * hudLayout.scale()));
        int scaledBoxHeight = Math.max(1, Math.round(boxHeight * hudLayout.scale()));
        int anchorCenterX = viewportWidth / 2 + hudLayout.xOffset();
        int anchorCenterY = Math.round(viewportHeight * 0.55F) + hudLayout.yOffset();
        int x = clamp(
                anchorCenterX - scaledBoxWidth / 2,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, viewportWidth - scaledBoxWidth - SCREEN_EDGE_PADDING)
        );
        int y = clamp(
                anchorCenterY - scaledBoxHeight / 2,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, viewportHeight - scaledBoxHeight - SCREEN_EDGE_PADDING)
        );
        return new DialogueRenderData(title, flatLines, boxWidth, boxHeight, scaledBoxWidth, scaledBoxHeight, x, y, hudLayout, optionGroups, rawSegments, maxContentWidth);
    }

    private static int optionsBoxHeight(int optionCount, int totalVisualLines) {
        if (optionCount == 0) {
            return 0;
        }
        int paddingHeight = optionCount * OPTION_ROW_PADDING_Y * 2;
        int lineHeight = totalVisualLines * LINE_HEIGHT;
        int gaps = Math.max(0, optionCount - 1) * OPTION_ROW_GAP;
        return paddingHeight + lineHeight + gaps;
    }

    private static List<List<FormattedCharSequence>> wrapOptionsGroups(
            Font textRenderer,
            Component optionsText,
            int maxContentWidth
    ) {
        if (optionsText == null) {
            return List.of();
        }
        String plain = optionsText.getString();
        if (plain.indexOf('\n') < 0) {
            return List.of(textRenderer.split(optionsText, maxContentWidth));
        }
        List<List<FormattedCharSequence>> groups = new ArrayList<>();
        for (String segment : plain.split("\n", -1)) {
            if (segment.isEmpty()) {
                groups.add(List.of(Component.empty().getVisualOrderText()));
            } else {
                groups.add(textRenderer.split(Component.literal(segment), maxContentWidth));
            }
        }
        return groups;
    }

    private static List<FormattedCharSequence> wrapTextLines(Font textRenderer, Component text, int maxContentWidth) {
        if (text == null) {
            return List.of();
        }

        String plain = text.getString();
        if (plain.indexOf('\n') < 0) {
            return textRenderer.split(text, maxContentWidth);
        }

        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (Component line : splitTextByNewlines(text)) {
            String linePlain = line.getString();
            if (linePlain.isEmpty()) {
                wrappedLines.add(Component.empty().getVisualOrderText());
                continue;
            }
            wrappedLines.addAll(textRenderer.split(line, maxContentWidth));
        }
        return wrappedLines;
    }

    private static List<Component> splitTextByNewlines(Component text) {
        List<Component> lines = new ArrayList<>();
        MutableComponent[] current = { Component.empty() };
        text.visit((style, s) -> {
            if (s == null || s.isEmpty()) {
                return Optional.empty();
            }
            Style resolvedStyle = style == null ? Style.EMPTY : style;
            int start = 0;
            int len = s.length();
            for (int i = 0; i < len; i++) {
                if (s.charAt(i) == '\n') {
                    if (i > start) {
                        current[0].append(Component.literal(s.substring(start, i)).setStyle(resolvedStyle));
                    }
                    lines.add(current[0]);
                    current[0] = Component.empty();
                    start = i + 1;
                }
            }
            if (start < len) {
                current[0].append(Component.literal(s.substring(start)).setStyle(resolvedStyle));
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (!current[0].getString().isEmpty() || lines.isEmpty()) {
            lines.add(current[0]);
        }
        return lines;
    }

    private static DialogueContent resolveEditorContent() {
        String pageInfo;
        String npcName;
        String translation;
        String optionsText;
        long visibleUntil;
        synchronized (WynnDialogueHudRenderer.class) {
            pageInfo = currentPageInfo;
            npcName = currentNpcName;
            translation = currentTranslation;
            optionsText = shouldRenderOptionsHud() ? currentOptionsText : "";
            visibleUntil = displayUntil;
        }

        Component previewOptionsText = Component.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_options");
        if (translation != null && !translation.isBlank() && System.currentTimeMillis() <= visibleUntil) {
            Component visibleOptionsText = optionsText == null || optionsText.isBlank()
                    ? previewOptionsText
                    : Component.literal(optionsText);
            return new DialogueContent(pageInfo, npcName, Component.literal(translation), visibleOptionsText);
        }

        return new DialogueContent(
                "",
                Component.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_npc").getString(),
                Component.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_body"),
                previewOptionsText
        );
    }

    private static void drawDialogueBox(
            GuiGraphicsExtractor drawContext,
            Font textRenderer,
            DialogueRenderData renderData,
            int viewportX,
            int viewportY
    ) {
        drawContext.pose().pushMatrix();
        drawContext.pose().translate((float) (viewportX + renderData.x()), (float) (viewportY + renderData.y()));
        drawContext.pose().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());

        drawContext.fill(0, 0, renderData.boxWidth(), renderData.boxHeight(), BOX_BACKGROUND_COLOR);
        drawContext.outline(0, 0, renderData.boxWidth(), renderData.boxHeight(), BOX_BORDER_COLOR);

        int textX = PADDING;
        int textY = PADDING;
        drawContext.text(textRenderer, renderData.title(), textX, textY, TITLE_COLOR);
        textY += 9 + TITLE_GAP;

        for (FormattedCharSequence line : renderData.wrappedLines()) {
            drawContext.text(textRenderer, line, textX, textY, BODY_COLOR);
            textY += LINE_HEIGHT;
        }

        drawContext.pose().popMatrix();
    }

    private static void drawErrorStatusLine(
            GuiGraphicsExtractor drawContext,
            Font textRenderer,
            DialogueRenderData renderData,
            String errorMessage
    ) {
        drawContext.pose().pushMatrix();
        drawContext.pose().translate((float) renderData.x(), (float) (renderData.y() + renderData.scaledBoxHeight() + 2));
        drawContext.pose().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());
        Component statusText = errorMessage == null || errorMessage.isBlank()
                ? Component.translatable(STATUS_ERROR_KEY)
                : Component.translatable(STATUS_ERROR_WITH_REASON_KEY, TranslationErrorTextSupport.localizeReason(errorMessage));
        drawContext.text(textRenderer, statusText, 0, 0, STATUS_LINE_COLOR);
        drawContext.pose().popMatrix();
    }

    private static void drawOptionsRows(
            GuiGraphicsExtractor drawContext,
            Font textRenderer,
            DialogueRenderData renderData,
            int viewportX,
            int viewportY,
            List<String> perLineAnimationKeys
    ) {
        drawContext.pose().pushMatrix();
        drawContext.pose().translate((float) (viewportX + renderData.x()), (float) (viewportY + renderData.y()));
        drawContext.pose().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());

        List<List<FormattedCharSequence>> groups = renderData.optionGroups();
        List<String> rawSegments = renderData.rawSegments();
        if (groups == null || groups.isEmpty()) {
            drawContext.pose().popMatrix();
            return;
        }

        int maxContentWidth = renderData.maxContentWidth();
        int yOffset = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            List<FormattedCharSequence> group = groups.get(gi);

            List<FormattedCharSequence> renderLines;
            String lineAnimationKey = (perLineAnimationKeys != null && gi < perLineAnimationKeys.size())
                    ? perLineAnimationKeys.get(gi) : "";
            if (!lineAnimationKey.isBlank() && rawSegments != null && gi < rawSegments.size()) {
                Component animatedText = AnimationManager.getAnimatedStyledText(
                        Component.literal(rawSegments.get(gi)), lineAnimationKey, false);
                renderLines = textRenderer.split(animatedText, maxContentWidth);
            } else {
                renderLines = group;
            }

            int maxLineWidth = 0;
            for (FormattedCharSequence line : renderLines) {
                maxLineWidth = Math.max(maxLineWidth, textRenderer.width(line));
            }

            int rowHeight = OPTION_ROW_PADDING_Y * 2 + renderLines.size() * LINE_HEIGHT;
            int bgWidth = maxLineWidth + PADDING;
            int rowBottom = yOffset + rowHeight;
            fillRoundedRect(drawContext, 0, yOffset, bgWidth, rowBottom, BOX_BACKGROUND_COLOR);
            int textY = yOffset + OPTION_ROW_PADDING_Y;
            int textX = PADDING / 2;
            for (FormattedCharSequence line : renderLines) {
                drawContext.text(textRenderer, line, textX, textY, BODY_COLOR);
                textY += LINE_HEIGHT;
            }
            yOffset = rowBottom + OPTION_ROW_GAP;
        }

        drawContext.pose().popMatrix();
    }

    private static void fillRoundedRect(GuiGraphicsExtractor ctx, int x, int y, int x2, int y2, int color) {
        int w = x2 - x;
        int h = y2 - y;
        if (w <= 0 || h <= 0) return;

        int r = Math.min(2, Math.min(w, h) / 2);
        for (int row = 0; row < r && row < h; row++) {
            int indent = r - row;
            ctx.fill(x + indent, y + row, x2 - indent, y + row + 1, color);
            ctx.fill(x + indent, y2 - row - 1, x2 - indent, y2 - row, color);
        }
        if (h > r * 2) {
            ctx.fill(x, y + r, x2, y2 - r, color);
        }
    }

    private static Component buildTitle(String pageInfo, String npcName) {
        StringBuilder builder = new StringBuilder();
        if (pageInfo != null && !pageInfo.isBlank()) {
            builder.append(pageInfo.trim()).append(' ');
        }
        if (npcName != null && !npcName.isBlank()) {
            builder.append(npcName.trim()).append(':');
        } else {
            builder.append("Dialogue:");
        }
        return Component.literal(builder.toString());
    }

    private static HudLayout resolveHudLayout() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null || config.wynnCraft.npc_dialogue == null) {
            return HudLayout.defaults();
        }

        WynnCraftConfig.HudConfig hud = config.wynnCraft.npc_dialogue.hud;
        if (hud == null) {
            return HudLayout.defaults();
        }

        return toHudLayout(hud);
    }

    private static HudLayout resolveOptionsHudLayout() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config == null || config.wynnCraft == null || config.wynnCraft.npc_dialogue == null) {
            return HudLayout.optionsDefaults();
        }

        WynnCraftConfig.HudConfig hud = config.wynnCraft.npc_dialogue.options_hud;
        if (hud == null) {
            return HudLayout.optionsDefaults();
        }

        return toHudLayout(hud);
    }

    private static boolean shouldRenderOptionsHud() {
        ModConfig config = Translate_AllinOne.getConfig();
        return config != null
                && config.wynnCraft != null
                && config.wynnCraft.npc_dialogue != null
                && config.wynnCraft.npc_dialogue.translate_options;
    }

    private static HudLayout toHudLayout(WynnCraftConfig.HudConfig hud) {
        int scalePercent = clamp(hud.scale_percent, WynnCraftConfig.HudConfig.MIN_SCALE_PERCENT, WynnCraftConfig.HudConfig.MAX_SCALE_PERCENT);
        int xOffset = clamp(hud.x_offset, WynnCraftConfig.HudConfig.MIN_X_OFFSET, WynnCraftConfig.HudConfig.MAX_X_OFFSET);
        int yOffset = clamp(hud.y_offset, WynnCraftConfig.HudConfig.MIN_Y_OFFSET, WynnCraftConfig.HudConfig.MAX_Y_OFFSET);
        return new HudLayout(scalePercent / 100.0F, scalePercent, xOffset, yOffset);
    }

    private static EditorPreviewSnapshot toEditorPreviewSnapshot(DialogueRenderData renderData) {
        return new EditorPreviewSnapshot(
                renderData.x(),
                renderData.y(),
                renderData.scaledBoxWidth(),
                renderData.scaledBoxHeight(),
                renderData.hudLayout().scalePercent(),
                renderData.hudLayout().xOffset(),
                renderData.hudLayout().yOffset()
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record HudLayout(float scale, int scalePercent, int xOffset, int yOffset) {
        private static HudLayout defaults() {
            return new HudLayout(
                    WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT / 100.0F,
                    WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT,
                    WynnCraftConfig.HudConfig.DEFAULT_X_OFFSET,
                    WynnCraftConfig.HudConfig.DEFAULT_Y_OFFSET
            );
        }

        private static HudLayout optionsDefaults() {
            return new HudLayout(
                    WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT / 100.0F,
                    WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT,
                    WynnCraftConfig.HudConfig.DEFAULT_OPTIONS_X_OFFSET,
                    WynnCraftConfig.HudConfig.DEFAULT_OPTIONS_Y_OFFSET
            );
        }
    }

    private record DialogueRenderData(
            Component title,
            List<FormattedCharSequence> wrappedLines,
            int boxWidth,
            int boxHeight,
            int scaledBoxWidth,
            int scaledBoxHeight,
            int x,
            int y,
            HudLayout hudLayout,
            List<List<FormattedCharSequence>> optionGroups,
            List<String> rawSegments,
            int maxContentWidth
    ) {
        DialogueRenderData(
                Component title,
                List<FormattedCharSequence> wrappedLines,
                int boxWidth,
                int boxHeight,
                int scaledBoxWidth,
                int scaledBoxHeight,
                int x,
                int y,
                HudLayout hudLayout
        ) {
            this(title, wrappedLines, boxWidth, boxHeight, scaledBoxWidth, scaledBoxHeight, x, y, hudLayout, null, null, 0);
        }
    }

    private record DialogueContent(String pageInfo, String npcName, Component translation, Component optionsText) {
    }

    public record EditorPreviewSnapshot(
            int x,
            int y,
            int width,
            int height,
            int scalePercent,
            int xOffset,
            int yOffset
    ) {
    }

    public record EditorPreviewLayout(EditorPreviewSnapshot dialogue, EditorPreviewSnapshot options) {
    }
}
