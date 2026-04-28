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
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
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

    private static boolean initialized;
    private static String currentPageInfo = "";
    private static String currentNpcName = "";
    private static String currentDialogue = "";
    private static String currentTranslation = "";
    private static String currentOptionsText = "";
    private static boolean currentTranslationPending;
    private static String currentAnimationKey = "";
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
        showDialogue(pageInfo, npcName, dialogue, translation, pending, animationKey, "");
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
        String safePageInfo = pageInfo == null ? "" : pageInfo;
        String safeNpcName = npcName == null ? "" : npcName;
        String safeDialogue = dialogue == null ? "" : dialogue;
        String safeTranslation = translation == null ? "" : translation.trim();
        String safeAnimationKey = animationKey == null ? "" : animationKey;
        String safeOptionsText = optionsText == null ? "" : optionsText.trim();
        if (safeTranslation.isEmpty()) {
            return;
        }

        if (Objects.equals(currentPageInfo, safePageInfo)
                && Objects.equals(currentNpcName, safeNpcName)
                && Objects.equals(currentDialogue, safeDialogue)
                && Objects.equals(currentTranslation, safeTranslation)
                && Objects.equals(currentOptionsText, safeOptionsText)
                && currentTranslationPending == pending
                && Objects.equals(currentAnimationKey, safeAnimationKey)) {
            return;
        }

        currentPageInfo = safePageInfo;
        currentNpcName = safeNpcName;
        currentDialogue = safeDialogue;
        currentTranslation = safeTranslation;
        currentOptionsText = safeOptionsText;
        currentTranslationPending = pending;
        currentAnimationKey = safeAnimationKey;
        displayUntil = System.currentTimeMillis() + DISPLAY_DURATION_MILLIS;
        WynnDialogueTranslationSupport.throttledDevLog(
                "hud_state_set",
                1000L,
                "hud_state_set page={} npc=\"{}\" dialogue=\"{}\" display=\"{}\" options=\"{}\" pending={} animationKey=\"{}\"",
                safePageInfo,
                safeNpcName,
                safeDialogue,
                safeTranslation,
                safeOptionsText,
                pending,
                safeAnimationKey
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
            drawDialogueBox(drawContext, textRenderer, optionsRenderData, 0, 0);
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
        long visibleUntil;
        synchronized (WynnDialogueHudRenderer.class) {
            translation = currentTranslation;
            optionsText = shouldRenderOptionsHud() ? currentOptionsText : "";
            pageInfo = currentPageInfo;
            npcName = currentNpcName;
            pending = currentTranslationPending;
            animationKey = currentAnimationKey;
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
        DialogueRenderData optionsRenderData = null;
        if (optionsText != null && !optionsText.isBlank()) {
            optionsRenderData = prepareOptionsRenderData(
                    textRenderer,
                    drawContext.getScaledWindowWidth(),
                    drawContext.getScaledWindowHeight(),
                    Text.literal(optionsText),
                    resolveOptionsHudLayout()
            );
            drawDialogueBox(drawContext, textRenderer, optionsRenderData, 0, 0);
        }

        String renderPayload = pageInfo + "\n" + npcName + "\n" + translation
                + "\n" + optionsText
                + "\n" + pending
                + "\n" + animationKey
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
        Text title = Text.translatable("text.translate_allinone.wynn_dialogue.options_title");
        int widthBudget = (int) Math.floor((viewportWidth - SCREEN_MARGIN) / hudLayout.scale()) - PADDING * 2;
        int maxContentWidth = Math.min(MAX_BOX_WIDTH, Math.max(MIN_CONTENT_WIDTH, widthBudget));
        List<OrderedText> wrappedLines = wrapTextLines(textRenderer, optionsText, maxContentWidth);
        if (wrappedLines.isEmpty()) {
            wrappedLines = List.of(optionsText.asOrderedText());
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

    private static List<OrderedText> wrapTextLines(TextRenderer textRenderer, Text text, int maxContentWidth) {
        if (text == null) {
            return List.of();
        }

        String plain = text.getString();
        if (plain.indexOf('\n') < 0) {
            return textRenderer.wrapLines(text, maxContentWidth);
        }

        List<OrderedText> wrappedLines = new ArrayList<>();
        for (String line : plain.split("\\n", -1)) {
            if (line.isEmpty()) {
                wrappedLines.add(Text.empty().asOrderedText());
                continue;
            }
            wrappedLines.addAll(textRenderer.wrapLines(Text.literal(line), maxContentWidth));
        }
        return wrappedLines;
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
                    WynnCraftConfig.HudConfig.DEFAULT_X_OFFSET,
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
            HudLayout hudLayout
    ) {
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
