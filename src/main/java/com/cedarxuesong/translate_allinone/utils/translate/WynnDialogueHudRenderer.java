package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

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

    private static final String STATUS_ERROR_PREFIX = "Translation error";
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
        HudRenderCallback.EVENT.register(WynnDialogueHudRenderer::render);
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
            TextRenderer textRenderer,
            int viewportWidth,
            int viewportHeight
    ) {
        return getEditorPreviewLayout(textRenderer, viewportWidth, viewportHeight).dialogue();
    }

    public static EditorPreviewLayout getEditorPreviewLayout(
            TextRenderer textRenderer,
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
            DrawContext drawContext,
            TextRenderer textRenderer,
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

    private static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
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

        if (System.currentTimeMillis() > visibleUntil) {
            clear();
            return;
        }

        if (client.currentScreen != null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        HudLayout hudLayout = resolveHudLayout();
        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                drawContext.getScaledWindowWidth(),
                drawContext.getScaledWindowHeight(),
                new DialogueContent(
                        pageInfo,
                        npcName,
                        pending
                                ? AnimationManager.getAnimatedStyledText(Text.literal(translation), animationKey, false)
                                : Text.literal(translation),
                        Text.literal(optionsText)
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
                    drawContext.getScaledWindowWidth(),
                    drawContext.getScaledWindowHeight(),
                    Text.literal(optionsText),
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

    private static DialogueRenderData prepareDialogueRenderData(
            TextRenderer textRenderer,
            int viewportWidth,
            int viewportHeight,
            DialogueContent content,
            HudLayout hudLayout
    ) {
        Text title = buildTitle(content.pageInfo(), content.npcName());
        int widthBudget = (int) Math.floor((viewportWidth - SCREEN_MARGIN) / hudLayout.scale()) - PADDING * 2;
        int maxContentWidth = Math.min(MAX_BOX_WIDTH, Math.max(MIN_CONTENT_WIDTH, widthBudget));
        List<OrderedText> wrappedLines = wrapTextLines(textRenderer, content.translation(), maxContentWidth);
        if (wrappedLines.isEmpty()) {
            wrappedLines = List.of(content.translation().asOrderedText());
        }

        int contentWidth = textRenderer.getWidth(title.asOrderedText());
        for (OrderedText line : wrappedLines) {
            contentWidth = Math.max(contentWidth, textRenderer.getWidth(line));
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
            TextRenderer textRenderer,
            int viewportWidth,
            int viewportHeight,
            Text optionsText,
            HudLayout hudLayout
    ) {
        Text title = Text.empty();
        int widthBudget = (int) Math.floor((viewportWidth - SCREEN_MARGIN) / hudLayout.scale()) - PADDING * 2;
        int maxContentWidth = Math.min(MAX_BOX_WIDTH, Math.max(MIN_CONTENT_WIDTH, widthBudget));
        List<List<OrderedText>> optionGroups = wrapOptionsGroups(textRenderer, optionsText, maxContentWidth);
        if (optionGroups.isEmpty()) {
            optionGroups = List.of(List.of(optionsText.asOrderedText()));
        }

        List<String> rawSegments = new ArrayList<>();
        int contentWidth = 0;
        List<OrderedText> flatLines = new ArrayList<>();
        for (List<OrderedText> group : optionGroups) {
            StringBuilder sb = new StringBuilder();
            for (OrderedText line : group) {
                contentWidth = Math.max(contentWidth, textRenderer.getWidth(line));
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

    private static List<List<OrderedText>> wrapOptionsGroups(
            TextRenderer textRenderer,
            Text optionsText,
            int maxContentWidth
    ) {
        if (optionsText == null) {
            return List.of();
        }
        String plain = optionsText.getString();
        if (plain.indexOf('\n') < 0) {
            return List.of(textRenderer.wrapLines(optionsText, maxContentWidth));
        }
        List<List<OrderedText>> groups = new ArrayList<>();
        for (String segment : plain.split("\n", -1)) {
            if (segment.isEmpty()) {
                groups.add(List.of(Text.empty().asOrderedText()));
            } else {
                groups.add(textRenderer.wrapLines(Text.literal(segment), maxContentWidth));
            }
        }
        return groups;
    }

    private static List<OrderedText> wrapTextLines(TextRenderer textRenderer, Text text, int maxContentWidth) {
        if (text == null) {
            return List.of();
        }

        String plain = text.getString();
        if (plain.indexOf('\n') < 0) {
            return textRenderer.wrapLines(text, maxContentWidth);
        }

        List<OrderedText> wrappedLines = new ArrayList<>();
        for (Text line : splitTextByNewlines(text)) {
            String linePlain = line.getString();
            if (linePlain.isEmpty()) {
                wrappedLines.add(Text.empty().asOrderedText());
                continue;
            }
            wrappedLines.addAll(textRenderer.wrapLines(line, maxContentWidth));
        }
        return wrappedLines;
    }

    private static List<Text> splitTextByNewlines(Text text) {
        List<Text> lines = new ArrayList<>();
        MutableText[] current = { Text.empty() };
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
                        current[0].append(Text.literal(s.substring(start, i)).setStyle(resolvedStyle));
                    }
                    lines.add(current[0]);
                    current[0] = Text.empty();
                    start = i + 1;
                }
            }
            if (start < len) {
                current[0].append(Text.literal(s.substring(start)).setStyle(resolvedStyle));
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

        Text previewOptionsText = Text.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_options");
        if (translation != null && !translation.isBlank() && System.currentTimeMillis() <= visibleUntil) {
            Text visibleOptionsText = optionsText == null || optionsText.isBlank()
                    ? previewOptionsText
                    : Text.literal(optionsText);
            return new DialogueContent(pageInfo, npcName, Text.literal(translation), visibleOptionsText);
        }

        return new DialogueContent(
                "",
                Text.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_npc").getString(),
                Text.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_body"),
                previewOptionsText
        );
    }

    private static void drawDialogueBox(
            DrawContext drawContext,
            TextRenderer textRenderer,
            DialogueRenderData renderData,
            int viewportX,
            int viewportY
    ) {
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate((float) (viewportX + renderData.x()), (float) (viewportY + renderData.y()));
        drawContext.getMatrices().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());

        drawContext.fill(0, 0, renderData.boxWidth(), renderData.boxHeight(), BOX_BACKGROUND_COLOR);
        drawContext.drawStrokedRectangle(0, 0, renderData.boxWidth(), renderData.boxHeight(), BOX_BORDER_COLOR);

        int textX = PADDING;
        int textY = PADDING;
        drawContext.drawTextWithShadow(textRenderer, renderData.title(), textX, textY, TITLE_COLOR);
        textY += 9 + TITLE_GAP;

        for (OrderedText line : renderData.wrappedLines()) {
            drawContext.drawTextWithShadow(textRenderer, line, textX, textY, BODY_COLOR);
            textY += LINE_HEIGHT;
        }

        drawContext.getMatrices().popMatrix();
    }

    private static void drawErrorStatusLine(
            DrawContext drawContext,
            TextRenderer textRenderer,
            DialogueRenderData renderData,
            String errorMessage
    ) {
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate((float) renderData.x(), (float) (renderData.y() + renderData.scaledBoxHeight() + 2));
        drawContext.getMatrices().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());
        String statusText = STATUS_ERROR_PREFIX;
        if (errorMessage != null && !errorMessage.isBlank()) {
            statusText = STATUS_ERROR_PREFIX + ": " + errorMessage;
        }
        drawContext.drawTextWithShadow(textRenderer, Text.literal(statusText), 0, 0, STATUS_LINE_COLOR);
        drawContext.getMatrices().popMatrix();
    }

    private static void drawOptionsRows(
            DrawContext drawContext,
            TextRenderer textRenderer,
            DialogueRenderData renderData,
            int viewportX,
            int viewportY,
            List<String> perLineAnimationKeys
    ) {
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate((float) (viewportX + renderData.x()), (float) (viewportY + renderData.y()));
        drawContext.getMatrices().scale(renderData.hudLayout().scale(), renderData.hudLayout().scale());

        List<List<OrderedText>> groups = renderData.optionGroups();
        List<String> rawSegments = renderData.rawSegments();
        if (groups == null || groups.isEmpty()) {
            drawContext.getMatrices().popMatrix();
            return;
        }

        int maxContentWidth = renderData.maxContentWidth();
        int yOffset = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            List<OrderedText> group = groups.get(gi);

            List<OrderedText> renderLines;
            String lineAnimationKey = (perLineAnimationKeys != null && gi < perLineAnimationKeys.size())
                    ? perLineAnimationKeys.get(gi) : "";
            if (!lineAnimationKey.isBlank() && rawSegments != null && gi < rawSegments.size()) {
                Text animatedText = AnimationManager.getAnimatedStyledText(
                        Text.literal(rawSegments.get(gi)), lineAnimationKey, false);
                renderLines = textRenderer.wrapLines(animatedText, maxContentWidth);
            } else {
                renderLines = group;
            }

            int maxLineWidth = 0;
            for (OrderedText line : renderLines) {
                maxLineWidth = Math.max(maxLineWidth, textRenderer.getWidth(line));
            }

            int rowHeight = OPTION_ROW_PADDING_Y * 2 + renderLines.size() * LINE_HEIGHT;
            int bgWidth = maxLineWidth + PADDING;
            int rowBottom = yOffset + rowHeight;
            fillRoundedRect(drawContext, 0, yOffset, bgWidth, rowBottom, BOX_BACKGROUND_COLOR);
            int textY = yOffset + OPTION_ROW_PADDING_Y;
            int textX = PADDING / 2;
            for (OrderedText line : renderLines) {
                drawContext.drawTextWithShadow(textRenderer, line, textX, textY, BODY_COLOR);
                textY += LINE_HEIGHT;
            }
            yOffset = rowBottom + OPTION_ROW_GAP;
        }

        drawContext.getMatrices().popMatrix();
    }

    private static void fillRoundedRect(DrawContext ctx, int x, int y, int x2, int y2, int color) {
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

    private static Text buildTitle(String pageInfo, String npcName) {
        StringBuilder builder = new StringBuilder();
        if (pageInfo != null && !pageInfo.isBlank()) {
            builder.append(pageInfo.trim()).append(' ');
        }
        if (npcName != null && !npcName.isBlank()) {
            builder.append(npcName.trim()).append(':');
        } else {
            builder.append("Dialogue:");
        }
        return Text.literal(builder.toString());
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
            Text title,
            List<OrderedText> wrappedLines,
            int boxWidth,
            int boxHeight,
            int scaledBoxWidth,
            int scaledBoxHeight,
            int x,
            int y,
            HudLayout hudLayout,
            List<List<OrderedText>> optionGroups,
            List<String> rawSegments,
            int maxContentWidth
    ) {
        DialogueRenderData(
                Text title,
                List<OrderedText> wrappedLines,
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

    private record DialogueContent(String pageInfo, String npcName, Text translation, Text optionsText) {
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
