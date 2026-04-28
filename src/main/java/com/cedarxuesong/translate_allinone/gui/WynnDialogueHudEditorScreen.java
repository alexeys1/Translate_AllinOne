package com.cedarxuesong.translate_allinone.gui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlockRegistry;
import com.cedarxuesong.translate_allinone.gui.configui.interaction.ConfigUiInteractionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiControlRenderer;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.cedarxuesong.translate_allinone.utils.translate.WynnDialogueHudRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class WynnDialogueHudEditorScreen extends Screen {
    private static final String I18N_PREFIX = "text.translate_allinone.configscreen.hud_editor.";
    private static final int COLOR_PANEL_BG = 0x8A0F0F0F;
    private static final int COLOR_PANEL_BORDER = 0xCC3A3A3A;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_MUTED = 0xFFB8B8B8;
    private static final int COLOR_TEXT_ACCENT = 0xFF9CF0C0;
    private static final int COLOR_BUTTON = 0xB31A1A1A;
    private static final int COLOR_BUTTON_HOVER = 0xCC28503D;
    private static final int COLOR_SELECTION = 0xFF9CF0C0;
    private static final int SELECTION_GLOW = 0x4432FFB4;
    private static final int SCREEN_EDGE_PADDING = 4;
    private static final int SCALE_STEP = 5;

    private final Screen parent;
    private final List<ActionBlock> actionBlocks = new ArrayList<>();
    private final ActionBlockRegistry actionBlockRegistry = new ActionBlockRegistry(actionBlocks, COLOR_BUTTON, COLOR_BUTTON_HOVER, COLOR_TEXT);

    private HudTarget selectedTarget = HudTarget.DIALOGUE;
    private boolean draggingHud;
    private int dragGrabOffsetX;
    private int dragGrabOffsetY;

    public WynnDialogueHudEditorScreen(Screen parent) {
        super(t("title"));
        this.parent = parent;
    }

    private static Text t(String key, Object... args) {
        return Text.translatable(I18N_PREFIX + key, args);
    }

    @Override
    protected void init() {
        rebuildActionBlocks();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        draggingHud = false;
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(actionBlocks, click.x(), click.y())) {
            return true;
        }

        WynnDialogueHudRenderer.EditorPreviewLayout layout = currentLayout();
        HudTarget target = targetAt(layout, click.x(), click.y());
        if (target != null) {
            selectedTarget = target;
            WynnDialogueHudRenderer.EditorPreviewSnapshot snapshot = currentSnapshot(layout, selectedTarget);
            draggingHud = true;
            dragGrabOffsetX = (int) Math.round(click.x()) - snapshot.x();
            dragGrabOffsetY = (int) Math.round(click.y()) - snapshot.y();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!draggingHud || click.button() != 0) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        moveHudTo(click.x(), click.y());
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingHud && click.button() == 0) {
            draggingHud = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        WynnDialogueHudRenderer.EditorPreviewLayout layout = currentLayout();
        HudTarget hoverTarget = targetAt(layout, mouseX, mouseY);
        if (hoverTarget != null) {
            selectedTarget = hoverTarget;
        }

        WynnCraftConfig.HudConfig hud = ensureHudConfig(selectedTarget);
        int direction = verticalAmount > 0.0 ? 1 : -1;
        int steps = Math.max(1, (int) Math.round(Math.abs(verticalAmount)));
        hud.scale_percent = clamp(
                hud.scale_percent + direction * steps * SCALE_STEP,
                WynnCraftConfig.HudConfig.MIN_SCALE_PERCENT,
                WynnCraftConfig.HudConfig.MAX_SCALE_PERCENT
        );
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        WynnDialogueHudRenderer.drawHudEditorPreview(context, this.textRenderer, this.width, this.height);
        WynnDialogueHudRenderer.EditorPreviewLayout layout = currentLayout();
        HudTarget hoverTarget = targetAt(layout, mouseX, mouseY);

        drawSelection(
                context,
                layout.dialogue(),
                selectedTarget == HudTarget.DIALOGUE || hoverTarget == HudTarget.DIALOGUE || draggingHud && selectedTarget == HudTarget.DIALOGUE
        );
        drawSelection(
                context,
                layout.options(),
                selectedTarget == HudTarget.OPTIONS || hoverTarget == HudTarget.OPTIONS || draggingHud && selectedTarget == HudTarget.OPTIONS
        );
        drawInfoPanel(context, currentSnapshot(layout, selectedTarget), selectedTarget);
        ConfigUiControlRenderer.drawActionBlocks(context, this.textRenderer, actionBlocks, mouseX, mouseY, COLOR_PANEL_BORDER);
    }

    private void rebuildActionBlocks() {
        actionBlocks.clear();

        int buttonWidth = 84;
        int buttonHeight = 20;
        int buttonGap = 8;
        int top = 14;
        int right = this.width - 14;

        actionBlockRegistry.add(
                right - buttonWidth,
                top,
                buttonWidth,
                buttonHeight,
                t("button.done"),
                this::close,
                COLOR_BUTTON,
                COLOR_BUTTON_HOVER,
                COLOR_TEXT,
                true
        );
        actionBlockRegistry.add(
                right - buttonWidth * 2 - buttonGap,
                top,
                buttonWidth,
                buttonHeight,
                t("button.reset"),
                this::resetHudLayout,
                COLOR_BUTTON,
                COLOR_BUTTON_HOVER,
                COLOR_TEXT,
                true
        );
    }

    private void drawSelection(DrawContext context, WynnDialogueHudRenderer.EditorPreviewSnapshot snapshot, boolean active) {
        if (snapshot.width() <= 0 || snapshot.height() <= 0) {
            return;
        }

        int glowInset = 4;
        if (active) {
            context.fill(
                    snapshot.x() - glowInset,
                    snapshot.y() - glowInset,
                    snapshot.x() + snapshot.width() + glowInset,
                    snapshot.y() + snapshot.height() + glowInset,
                    SELECTION_GLOW
            );
        }
        ConfigUiDraw.drawOutline(context, snapshot.x(), snapshot.y(), snapshot.width(), snapshot.height(), COLOR_SELECTION);

        int handleSize = 4;
        drawHandle(context, snapshot.x() - handleSize / 2, snapshot.y() - handleSize / 2, handleSize);
        drawHandle(context, snapshot.x() + snapshot.width() - handleSize / 2 - 1, snapshot.y() - handleSize / 2, handleSize);
        drawHandle(context, snapshot.x() - handleSize / 2, snapshot.y() + snapshot.height() - handleSize / 2 - 1, handleSize);
        drawHandle(
                context,
                snapshot.x() + snapshot.width() - handleSize / 2 - 1,
                snapshot.y() + snapshot.height() - handleSize / 2 - 1,
                handleSize
        );
    }

    private void drawHandle(DrawContext context, int x, int y, int size) {
        context.fill(x, y, x + size, y + size, COLOR_SELECTION);
    }

    private void drawInfoPanel(DrawContext context, WynnDialogueHudRenderer.EditorPreviewSnapshot snapshot, HudTarget target) {
        int panelX = 14;
        int panelY = 14;
        int panelWidth = 310;
        int panelHeight = 84;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL_BG);
        ConfigUiDraw.drawOutline(context, panelX, panelY, panelWidth, panelHeight, COLOR_PANEL_BORDER);

        context.drawText(this.textRenderer, this.title, panelX + 10, panelY + 8, COLOR_TEXT_ACCENT, false);
        context.drawText(this.textRenderer, t("status.target", targetName(target).getString()), panelX + 10, panelY + 24, COLOR_TEXT_ACCENT, false);
        context.drawText(this.textRenderer, t("instruction.drag"), panelX + 10, panelY + 38, COLOR_TEXT, false);
        context.drawText(this.textRenderer, t("instruction.scroll"), panelX + 10, panelY + 50, COLOR_TEXT, false);
        context.drawText(this.textRenderer, t("instruction.close"), panelX + 10, panelY + 62, COLOR_TEXT_MUTED, false);

        String layoutValue = t("status.layout", snapshot.scalePercent(), snapshot.xOffset(), snapshot.yOffset()).getString();
        int layoutWidth = this.textRenderer.getWidth(layoutValue);
        context.drawText(
                this.textRenderer,
                layoutValue,
                this.width - 14 - layoutWidth,
                42,
                COLOR_TEXT_ACCENT,
                false
        );
    }

    private WynnDialogueHudRenderer.EditorPreviewLayout currentLayout() {
        return WynnDialogueHudRenderer.getEditorPreviewLayout(this.textRenderer, this.width, this.height);
    }

    private WynnDialogueHudRenderer.EditorPreviewSnapshot currentSnapshot(
            WynnDialogueHudRenderer.EditorPreviewLayout layout,
            HudTarget target
    ) {
        return target == HudTarget.OPTIONS ? layout.options() : layout.dialogue();
    }

    private void moveHudTo(double mouseX, double mouseY) {
        WynnDialogueHudRenderer.EditorPreviewLayout layout = currentLayout();
        WynnDialogueHudRenderer.EditorPreviewSnapshot snapshot = currentSnapshot(layout, selectedTarget);
        if (snapshot.width() <= 0 || snapshot.height() <= 0) {
            return;
        }

        int clampedBoxX = clamp(
                (int) Math.round(mouseX) - dragGrabOffsetX,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, this.width - snapshot.width() - SCREEN_EDGE_PADDING)
        );
        int clampedBoxY = clamp(
                (int) Math.round(mouseY) - dragGrabOffsetY,
                SCREEN_EDGE_PADDING,
                Math.max(SCREEN_EDGE_PADDING, this.height - snapshot.height() - SCREEN_EDGE_PADDING)
        );

        WynnCraftConfig.HudConfig hud = ensureHudConfig(selectedTarget);
        hud.x_offset = clamp(
                clampedBoxX + snapshot.width() / 2 - this.width / 2,
                WynnCraftConfig.HudConfig.MIN_X_OFFSET,
                WynnCraftConfig.HudConfig.MAX_X_OFFSET
        );
        hud.y_offset = clamp(
                clampedBoxY + snapshot.height() / 2 - Math.round(this.height * 0.55F),
                WynnCraftConfig.HudConfig.MIN_Y_OFFSET,
                WynnCraftConfig.HudConfig.MAX_Y_OFFSET
        );
    }

    private void resetHudLayout() {
        WynnCraftConfig.HudConfig hud = ensureHudConfig(selectedTarget);
        WynnCraftConfig.HudConfig defaults = selectedTarget == HudTarget.OPTIONS
                ? WynnCraftConfig.HudConfig.optionsDefaults()
                : new WynnCraftConfig.HudConfig();
        hud.scale_percent = defaults.scale_percent;
        hud.x_offset = defaults.x_offset;
        hud.y_offset = defaults.y_offset;
    }

    private WynnCraftConfig.HudConfig ensureHudConfig(HudTarget target) {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config.wynnCraft == null) {
            config.wynnCraft = new WynnCraftConfig();
        }
        if (config.wynnCraft.npc_dialogue == null) {
            config.wynnCraft.npc_dialogue = new WynnCraftConfig.NpcDialogueConfig();
        }
        if (config.wynnCraft.npc_dialogue.hud == null) {
            config.wynnCraft.npc_dialogue.hud = new WynnCraftConfig.HudConfig();
        }
        if (config.wynnCraft.npc_dialogue.options_hud == null) {
            config.wynnCraft.npc_dialogue.options_hud = WynnCraftConfig.HudConfig.optionsDefaults();
        }
        return target == HudTarget.OPTIONS
                ? config.wynnCraft.npc_dialogue.options_hud
                : config.wynnCraft.npc_dialogue.hud;
    }

    private HudTarget targetAt(WynnDialogueHudRenderer.EditorPreviewLayout layout, double mouseX, double mouseY) {
        if (contains(layout.options(), mouseX, mouseY)) {
            return HudTarget.OPTIONS;
        }
        if (contains(layout.dialogue(), mouseX, mouseY)) {
            return HudTarget.DIALOGUE;
        }
        return null;
    }

    private Text targetName(HudTarget target) {
        return t(target == HudTarget.OPTIONS ? "target.options" : "target.dialogue");
    }

    private boolean contains(WynnDialogueHudRenderer.EditorPreviewSnapshot snapshot, double mouseX, double mouseY) {
        return snapshot.width() > 0
                && snapshot.height() > 0
                && mouseX >= snapshot.x()
                && mouseX <= snapshot.x() + snapshot.width()
                && mouseY >= snapshot.y()
                && mouseY <= snapshot.y() + snapshot.height();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum HudTarget {
        DIALOGUE,
        OPTIONS
    }
}
