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
        String safePageInfo = pageInfo == null ? "" : pageInfo;
        String safeNpcName = npcName == null ? "" : npcName;
        String safeDialogue = dialogue == null ? "" : dialogue;
        String safeTranslation = translation == null ? "" : translation.trim();
        String safeAnimationKey = animationKey == null ? "" : animationKey;
        if (safeTranslation.isEmpty()) {
            return;
        }

        if (Objects.equals(currentPageInfo, safePageInfo)
                && Objects.equals(currentNpcName, safeNpcName)
                && Objects.equals(currentDialogue, safeDialogue)
                && Objects.equals(currentTranslation, safeTranslation)
                && currentTranslationPending == pending
                && Objects.equals(currentAnimationKey, safeAnimationKey)) {
            return;
        }

        currentPageInfo = safePageInfo;
        currentNpcName = safeNpcName;
        currentDialogue = safeDialogue;
        currentTranslation = safeTranslation;
        currentTranslationPending = pending;
        currentAnimationKey = safeAnimationKey;
        displayUntil = System.currentTimeMillis() + DISPLAY_DURATION_MILLIS;
        WynnDialogueTranslationSupport.throttledDevLog(
                "hud_state_set",
                1000L,
                "hud_state_set page={} npc=\"{}\" dialogue=\"{}\" display=\"{}\" pending={} animationKey=\"{}\"",
                safePageInfo,
                safeNpcName,
                safeDialogue,
                safeTranslation,
                pending,
                safeAnimationKey
        );
    }

    public static synchronized void clear() {
        currentPageInfo = "";
        currentNpcName = "";
        currentDialogue = "";
        currentTranslation = "";
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
        if (textRenderer == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return new EditorPreviewSnapshot(0, 0, 0, 0, WynnCraftConfig.HudConfig.DEFAULT_SCALE_PERCENT, 0, 0);
        }

        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                viewportWidth,
                viewportHeight,
                resolveEditorContent(),
                resolveHudLayout()
        );
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

    public static void drawHudEditorPreview(
            DrawContext drawContext,
            TextRenderer textRenderer,
            int viewportWidth,
            int viewportHeight
    ) {
        if (drawContext == null || textRenderer == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                viewportWidth,
                viewportHeight,
                resolveEditorContent(),
                resolveHudLayout()
        );
        drawDialogueBox(drawContext, textRenderer, renderData, 0, 0);
    }

    private static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        String translation;
        String pageInfo;
        String npcName;
        boolean pending;
        String animationKey;
        long visibleUntil;
        synchronized (WynnDialogueHudRenderer.class) {
            translation = currentTranslation;
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
        DialogueRenderData renderData = prepareDialogueRenderData(
                textRenderer,
                drawContext.getScaledWindowWidth(),
                drawContext.getScaledWindowHeight(),
                new DialogueContent(
                        pageInfo,
                        npcName,
                        pending
                                ? AnimationManager.getAnimatedStyledText(Text.literal(translation), animationKey, false)
                                : Text.literal(translation)
                ),
                resolveHudLayout()
        );
        drawDialogueBox(drawContext, textRenderer, renderData, 0, 0);

        String renderPayload = pageInfo + "\n" + npcName + "\n" + translation
                + "\n" + pending
                + "\n" + animationKey
                + "\n" + renderData.hudLayout().scalePercent()
                + "\n" + renderData.hudLayout().xOffset()
                + "\n" + renderData.hudLayout().yOffset();
        if (!renderPayload.equals(lastRenderedPayload)) {
            WynnDialogueTranslationSupport.throttledDevLog(
                    "hud_rendered",
                    1000L,
                    "hud_rendered x={} y={} width={} height={} scale={} xOffset={} yOffset={} title=\"{}\"",
                    renderData.x(),
                    renderData.y(),
                    renderData.scaledBoxWidth(),
                    renderData.scaledBoxHeight(),
                    renderData.hudLayout().scalePercent(),
                    renderData.hudLayout().xOffset(),
                    renderData.hudLayout().yOffset(),
                    renderData.title().getString()
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
        List<OrderedText> wrappedLines = textRenderer.wrapLines(content.translation(), maxContentWidth);
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

    private static DialogueContent resolveEditorContent() {
        String pageInfo;
        String npcName;
        String translation;
        long visibleUntil;
        synchronized (WynnDialogueHudRenderer.class) {
            pageInfo = currentPageInfo;
            npcName = currentNpcName;
            translation = currentTranslation;
            visibleUntil = displayUntil;
        }

        if (translation != null && !translation.isBlank() && System.currentTimeMillis() <= visibleUntil) {
            return new DialogueContent(pageInfo, npcName, Text.literal(translation));
        }

        return new DialogueContent(
                "",
                Text.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_npc").getString(),
                Text.translatable("text.translate_allinone.configscreen.preview.wynn_npc_dialogue_body")
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

        int scalePercent = clamp(hud.scale_percent, WynnCraftConfig.HudConfig.MIN_SCALE_PERCENT, WynnCraftConfig.HudConfig.MAX_SCALE_PERCENT);
        int xOffset = clamp(hud.x_offset, WynnCraftConfig.HudConfig.MIN_X_OFFSET, WynnCraftConfig.HudConfig.MAX_X_OFFSET);
        int yOffset = clamp(hud.y_offset, WynnCraftConfig.HudConfig.MIN_Y_OFFSET, WynnCraftConfig.HudConfig.MAX_Y_OFFSET);
        return new HudLayout(scalePercent / 100.0F, scalePercent, xOffset, yOffset);
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

    private record DialogueContent(String pageInfo, String npcName, Text translation) {
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
}
