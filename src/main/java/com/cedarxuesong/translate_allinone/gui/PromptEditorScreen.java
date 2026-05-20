package com.cedarxuesong.translate_allinone.gui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlockRegistry;
import com.cedarxuesong.translate_allinone.gui.configui.interaction.ConfigUiInteractionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiControlRenderer;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.translate.PromptMessageBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PromptEditorScreen extends Screen {
    private static final String I18N_PREFIX = "text.translate_allinone.configscreen.prompt_editor.";
    private static final int COLOR_BG = 0xFF0C0C0C;
    private static final int COLOR_TOP_BAR = 0xFF151515;
    private static final int COLOR_LEFT_PANEL = 0xFF131313;
    private static final int COLOR_MAIN_PANEL = 0xFF101010;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_MUTED = 0xFF9A9A9A;
    private static final int COLOR_TEXT_ACCENT = 0xFF9CF0C0;
    private static final int COLOR_BLOCK = 0xFF1A1A1A;
    private static final int COLOR_BLOCK_HOVER = 0xFF242424;
    private static final int COLOR_BLOCK_SELECTED = 0xFF214936;
    private static final int COLOR_BLOCK_SELECTED_HOVER = 0xFF295B43;
    private static final int COLOR_BLOCK_ACCENT = 0xFF2B6A4C;
    private static final int COLOR_BLOCK_ACCENT_HOVER = 0xFF337B59;
    private static final int COLOR_BLOCK_DANGER = 0xFF3C1C1C;
    private static final int COLOR_BLOCK_DANGER_HOVER = 0xFF4B2525;
    private static final int COLOR_EDITOR_BG = 0xFF0A0A0A;
    private static final int COLOR_CURSOR = 0xFF9CF0C0;
    private static final int COLOR_SELECTION = 0x5532B44A;
    private static final int COLOR_LINE_NUMBER = 0xFF555555;
    private static final int COLOR_STATUS_BAR = 0xFF151515;

    private static final int TOP_BAR_HEIGHT = 40;
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int LINE_HEIGHT = 12;
    private static final int EDITOR_PADDING = 12;
    private static final int LINE_NUMBER_WIDTH = 32;
    private static final int STATUS_BAR_HEIGHT = 20;

    private final Screen parent;
    private final String providerId;
    private final Map<String, StringBuilder> routeTexts = new LinkedHashMap<>();
    private final Map<String, String> originalTexts = new LinkedHashMap<>();
    private final List<ActionBlock> actionBlocks = new ArrayList<>();
    private final ActionBlockRegistry actionBlockRegistry = new ActionBlockRegistry(actionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);

    private String selectedRoute = "item";
    private int cursorPos;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int scrollOffset;
    private long lastBlinkMillis;
    private boolean cursorVisible = true;
    private final Map<String, List<UndoEntry>> undoStacks = new LinkedHashMap<>();

    private void pushUndo() {
        StringBuilder text = currentText();
        if (text == null) return;
        List<UndoEntry> stack = undoStacks.computeIfAbsent(selectedRoute, k -> new ArrayList<>());
        String snapshot = text.toString();
        if (!stack.isEmpty() && stack.get(stack.size() - 1).text.equals(snapshot)) return;
        stack.add(new UndoEntry(snapshot, cursorPos, selectionStart, selectionEnd));
        if (stack.size() > 50) stack.remove(0);
    }

    private record UndoEntry(String text, int cursorPos, int selectionStart, int selectionEnd) {}

    private static final String[] ROUTE_KEYS = {
            "item", "scoreboard", "chat_output", "chat_input_translate",
            "wynn_npc_dialogue", "wynntils_task_tracker"
    };

    public PromptEditorScreen(Screen parent, String providerId) {
        super(t("title"));
        this.parent = parent;
        this.providerId = providerId;
    }

    private static Text t(String key, Object... args) {
        return Text.translatable(I18N_PREFIX + key, args);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        loadRouteTexts();
        cursorPos = routeTexts.containsKey(selectedRoute) ? routeTexts.get(selectedRoute).length() : 0;
        selectionStart = -1;
        selectionEnd = -1;
        scrollOffset = 0;
        rebuildActionBlocks();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void loadRouteTexts() {
        routeTexts.clear();
        originalTexts.clear();
        ModConfig config = Translate_AllinOne.getConfig();
        config.providerManager.ensureDefaults();
        ApiProviderProfile profile = config.providerManager.findById(providerId);
        Map<String, String> overrides = profile == null ? new LinkedHashMap<>() :
                profile.system_prompt_overrides == null ? new LinkedHashMap<>() :
                        new LinkedHashMap<>(profile.system_prompt_overrides);

        for (String key : ROUTE_KEYS) {
            String override = overrides.get(key);
            String text;
            if (override != null && !override.isBlank()) {
                text = override;
            } else {
                text = PromptMessageBuilder.getDefaultPromptTemplate(key);
            }
            routeTexts.put(key, new StringBuilder(text));
            originalTexts.put(key, text);
        }
    }

    private void saveAll() {
        ModConfig config = Translate_AllinOne.getConfig();
        config.providerManager.ensureDefaults();
        ApiProviderProfile profile = config.providerManager.findById(providerId);
        if (profile == null) {
            return;
        }
        profile.system_prompt_overrides = new LinkedHashMap<>();
        for (String key : routeTexts.keySet()) {
            String text = routeTexts.get(key).toString();
            String defaultPrompt = PromptMessageBuilder.getDefaultPromptTemplate(key);
            if (!text.equals(defaultPrompt) && !text.isBlank()) {
                profile.system_prompt_overrides.put(key, text);
            }
        }
        profile.normalizePromptOverrides();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void resetAll() {
        for (String key : routeTexts.keySet()) {
            String defaultPrompt = PromptMessageBuilder.getDefaultPromptTemplate(key);
            routeTexts.get(key).setLength(0);
            routeTexts.get(key).append(defaultPrompt);
        }
        cursorPos = 0;
        selectionStart = -1;
        selectionEnd = -1;
        scrollOffset = 0;
    }

    private boolean hasUnsavedChanges() {
        for (String key : routeTexts.keySet()) {
            String current = routeTexts.get(key).toString();
            String original = originalTexts.getOrDefault(key, "");
            if (!current.equals(original)) {
                return true;
            }
        }
        return false;
    }

    private StringBuilder currentText() {
        return routeTexts.getOrDefault(selectedRoute, new StringBuilder());
    }

    private void rebuildActionBlocks() {
        actionBlocks.clear();

        int buttonWidth = 84;
        int buttonHeight = 20;
        int buttonGap = 8;
        int top = (TOP_BAR_HEIGHT - buttonHeight) / 2;
        int right = this.width - 14;

        actionBlockRegistry.add(
                right - buttonWidth,
                top,
                buttonWidth,
                buttonHeight,
                t("button.reset"),
                this::resetAll,
                COLOR_BLOCK_DANGER,
                COLOR_BLOCK_DANGER_HOVER,
                COLOR_TEXT,
                true
        );
        actionBlockRegistry.add(
                right - buttonWidth * 2 - buttonGap,
                top,
                buttonWidth,
                buttonHeight,
                t("button.cancel"),
                this::close,
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true
        );
        actionBlockRegistry.add(
                right - buttonWidth * 3 - buttonGap * 2,
                top,
                buttonWidth,
                buttonHeight,
                t("button.save"),
                this::saveAll,
                COLOR_BLOCK_ACCENT,
                COLOR_BLOCK_ACCENT_HOVER,
                COLOR_TEXT,
                true
        );
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) {
            return true;
        }

        int key = input.key();
        boolean ctrl = isCtrlDown();
        boolean shift = isShiftDown();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (key == GLFW.GLFW_KEY_TAB && !ctrl && !shift) {
            return true;
        }

        StringBuilder text = currentText();

        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectionStart = 0;
            selectionEnd = text.length();
            cursorPos = selectionEnd;
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            List<UndoEntry> stack = undoStacks.get(selectedRoute);
            if (stack != null && !stack.isEmpty()) {
                UndoEntry entry = stack.remove(stack.size() - 1);
                text.setLength(0);
                text.append(entry.text);
                cursorPos = entry.cursorPos;
                selectionStart = entry.selectionStart;
                selectionEnd = entry.selectionEnd;
            }
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                int start = Math.min(selectionStart, selectionEnd);
                int end = Math.max(selectionStart, selectionEnd);
                if (this.client != null) {
                    this.client.keyboard.setClipboard(text.substring(start, end));
                }
            }
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                pushUndo();
                int start = Math.min(selectionStart, selectionEnd);
                int end = Math.max(selectionStart, selectionEnd);
                if (this.client != null) {
                    this.client.keyboard.setClipboard(text.substring(start, end));
                }
                text.delete(start, end);
                cursorPos = start;
                selectionStart = -1;
                selectionEnd = -1;
            }
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_V) {
            if (this.client != null) {
                pushUndo();
                String clipboard = this.client.keyboard.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                        int start = Math.min(selectionStart, selectionEnd);
                        int end = Math.max(selectionStart, selectionEnd);
                        text.delete(start, end);
                        cursorPos = start;
                        selectionStart = -1;
                        selectionEnd = -1;
                    }
                    text.insert(cursorPos, clipboard);
                    cursorPos += clipboard.length();
                }
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT) {
            selectionStart = -1;
            selectionEnd = -1;
            if (cursorPos > 0) cursorPos--;
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT) {
            selectionStart = -1;
            selectionEnd = -1;
            if (cursorPos < text.length()) cursorPos++;
            return true;
        }

        if (key == GLFW.GLFW_KEY_HOME) {
            selectionStart = -1;
            selectionEnd = -1;
            cursorPos = 0;
            return true;
        }

        if (key == GLFW.GLFW_KEY_END) {
            selectionStart = -1;
            selectionEnd = -1;
            cursorPos = text.length();
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            pushUndo();
            if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                int start = Math.min(selectionStart, selectionEnd);
                int end = Math.max(selectionStart, selectionEnd);
                text.delete(start, end);
                cursorPos = start;
                selectionStart = -1;
                selectionEnd = -1;
            } else if (cursorPos > 0) {
                text.deleteCharAt(cursorPos - 1);
                cursorPos--;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_DELETE) {
            pushUndo();
            if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                int start = Math.min(selectionStart, selectionEnd);
                int end = Math.max(selectionStart, selectionEnd);
                text.delete(start, end);
                cursorPos = start;
                selectionStart = -1;
                selectionEnd = -1;
            } else if (cursorPos < text.length()) {
                text.deleteCharAt(cursorPos);
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            String fullStr = text.toString();
            List<String> ulines = wrapLines(fullStr, (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12));
            int[] lineCol = cursorVisualLine(fullStr, ulines, cursorPos);
            int curLine = lineCol[0];
            int curCol = lineCol[1];
            int targetLine = key == GLFW.GLFW_KEY_UP ? Math.max(0, curLine - 1) : curLine + 1;
            if (targetLine >= 0 && targetLine < ulines.size()) {
                int targetCol = Math.min(curCol, ulines.get(targetLine).length());
                cursorPos = cursorPosFromLineCol(fullStr, targetLine, targetCol, ulines);
                selectionStart = -1;
                selectionEnd = -1;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            pushUndo();
            if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                int start = Math.min(selectionStart, selectionEnd);
                int end = Math.max(selectionStart, selectionEnd);
                text.delete(start, end);
                cursorPos = start;
                selectionStart = -1;
                selectionEnd = -1;
            }
            text.insert(cursorPos, '\n');
            cursorPos++;
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (super.charTyped(input)) {
            return true;
        }

        String str = input.asString();
        if (str == null || str.isEmpty()) {
            return false;
        }
        char c = str.charAt(0);
        if (c == '§' || c < 0x20) {
            return false;
        }

        StringBuilder text = currentText();

        pushUndo();
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            text.delete(start, end);
            cursorPos = start;
            selectionStart = -1;
            selectionEnd = -1;
        }

        text.insert(cursorPos, c);
        cursorPos++;
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        double mouseX = click.x();
        double mouseY = click.y();

        if (mouseY < TOP_BAR_HEIGHT) {
            if (ConfigUiInteractionSupport.dispatchActionBlocks(actionBlocks, mouseX, mouseY)) {
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (mouseX < LEFT_PANEL_WIDTH) {
            int routeIndex = (int) ((mouseY - TOP_BAR_HEIGHT - 12) / 28);
            if (routeIndex >= 0 && routeIndex < ROUTE_KEYS.length) {
                selectedRoute = ROUTE_KEYS[routeIndex];
                cursorPos = currentText().length();
                selectionStart = -1;
                selectionEnd = -1;
                scrollOffset = 0;
                rebuildActionBlocks();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (mouseX >= (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) && mouseX < (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) + (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12)) {
            String fullText = currentText().toString();
            List<String> lines = wrapLines(fullText, (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12));
            int lineIndex = (int) ((mouseY - (TOP_BAR_HEIGHT + EDITOR_PADDING) + scrollOffset * LINE_HEIGHT) / LINE_HEIGHT);
            if (lineIndex >= 0 && lineIndex < lines.size()) {
                int col = cursorColumnFromMouse(mouseX - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH), lines.get(lineIndex));
                int newPos = cursorPosFromLineCol(fullText, lineIndex, col, lines);
                if (isShiftDown()) {
                    if (selectionStart < 0) selectionStart = cursorPos;
                    selectionEnd = newPos;
                } else {
                    selectionStart = -1;
                    selectionEnd = -1;
                }
                cursorPos = newPos;
                return true;
            }
            if (lineIndex >= lines.size() && !lines.isEmpty()) {
                cursorPos = fullText.length();
                selectionStart = -1;
                selectionEnd = -1;
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() != 0) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        double mouseX = click.x();
        double mouseY = click.y();

        if (mouseX >= (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH)
                && mouseX < (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) + (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12)) {
            String fullText = currentText().toString();
            List<String> lines = wrapLines(fullText, (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12));
            int lineIndex = (int) ((mouseY - (TOP_BAR_HEIGHT + EDITOR_PADDING) + scrollOffset * LINE_HEIGHT) / LINE_HEIGHT);
            if (lineIndex < 0) lineIndex = 0;
            if (lineIndex >= lines.size()) lineIndex = lines.size() - 1;
            if (lineIndex >= 0 && lineIndex < lines.size()) {
                int col = cursorColumnFromMouse(mouseX - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH), lines.get(lineIndex));
                int newPos = cursorPosFromLineCol(fullText, lineIndex, col, lines);
                if (selectionStart < 0) selectionStart = cursorPos;
                selectionEnd = newPos;
                cursorPos = newPos;
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        String fullText = currentText().toString();
        List<String> lines = wrapLines(fullText, (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12));
        int maxScroll = Math.max(0, lines.size() - ((this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2) / LINE_HEIGHT));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastBlinkMillis > 530) {
            cursorVisible = !cursorVisible;
            lastBlinkMillis = now;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderChrome(context);
        renderLeftPanel(context, mouseX, mouseY);
        renderEditor(context);
        renderStatusBar(context);
        ConfigUiControlRenderer.drawActionBlocks(context, this.textRenderer, actionBlocks, mouseX, mouseY, COLOR_BORDER);
    }

    private void renderChrome(DrawContext context) {
        context.fill(0, 0, this.width, this.height, COLOR_BG);
        context.fill(0, 0, this.width, TOP_BAR_HEIGHT, COLOR_TOP_BAR);
        context.fill(0, TOP_BAR_HEIGHT, LEFT_PANEL_WIDTH, this.height, COLOR_LEFT_PANEL);
        context.fill(LEFT_PANEL_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, COLOR_MAIN_PANEL);

        ConfigUiDraw.drawOutline(context, 0, 0, this.width, TOP_BAR_HEIGHT, COLOR_BORDER);
        ConfigUiDraw.drawOutline(context, 0, TOP_BAR_HEIGHT, LEFT_PANEL_WIDTH, this.height - TOP_BAR_HEIGHT, COLOR_BORDER);
        ConfigUiDraw.drawOutline(context, LEFT_PANEL_WIDTH, TOP_BAR_HEIGHT, this.width - LEFT_PANEL_WIDTH, this.height - TOP_BAR_HEIGHT, COLOR_BORDER);

        Text title = t("screen_title", providerId);
        context.drawText(this.textRenderer, title, 14, (TOP_BAR_HEIGHT - this.textRenderer.fontHeight) / 2, COLOR_TEXT_ACCENT, false);

        boolean unsaved = hasUnsavedChanges();
        if (unsaved) {
            Text unsavedText = t("unsaved");
            context.drawText(this.textRenderer, unsavedText, 14 + this.textRenderer.getWidth(title) + 12, (TOP_BAR_HEIGHT - this.textRenderer.fontHeight) / 2, COLOR_TEXT_MUTED, false);
        }
    }

    private void renderLeftPanel(DrawContext context, int mouseX, int mouseY) {
        int y = TOP_BAR_HEIGHT + 12;
        for (int i = 0; i < ROUTE_KEYS.length; i++) {
            String routeKey = ROUTE_KEYS[i];
            boolean selected = routeKey.equals(selectedRoute);
            boolean hovered = mouseX >= 0 && mouseX < LEFT_PANEL_WIDTH && mouseY >= y && mouseY < y + 24;

            int bgColor = selected ? COLOR_BLOCK_SELECTED : (hovered ? COLOR_BLOCK_HOVER : COLOR_BLOCK);
            if (selected && hovered) bgColor = COLOR_BLOCK_SELECTED_HOVER;

            context.fill(8, y, LEFT_PANEL_WIDTH - 8, y + 24, bgColor);

            String text = currentText().toString();
            boolean hasContent = !text.isEmpty();
            int textColor = selected ? COLOR_TEXT_ACCENT : (hasContent ? COLOR_TEXT : COLOR_TEXT_MUTED);

            context.drawText(this.textRenderer, routeDisplayName(routeKey), 16, y + 6, textColor, false);

            if (hasContent) {
                String indicator = "●";
                int indicatorWidth = this.textRenderer.getWidth(indicator);
                context.drawText(this.textRenderer, indicator, LEFT_PANEL_WIDTH - 16 - indicatorWidth, y + 6, 0xFF59D185, false);
            }

            y += 28;
        }
    }

    private void renderEditor(DrawContext context) {
        int labelX = LEFT_PANEL_WIDTH + EDITOR_PADDING;
        int editorHeight = this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2;

        context.fill(LEFT_PANEL_WIDTH, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + editorHeight + EDITOR_PADDING * 2, COLOR_EDITOR_BG);

        String fullText = currentText().toString();
        int textW = (this.width - (LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH) - EDITOR_PADDING - 12);
        List<String> lines = wrapLines(fullText, textW);
        int visibleLines = (this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2) / LINE_HEIGHT;

        int selStart = selectionStart >= 0 && selectionEnd >= 0 ? Math.min(selectionStart, selectionEnd) : -1;
        int selEnd = selStart >= 0 ? Math.max(selectionStart, selectionEnd) : -1;

        if (selStart >= 0 && selEnd > selStart) {
            drawSelection(context, fullText, lines, selStart, selEnd, scrollOffset);
        }

        int numberX = labelX;
        int lineY = (TOP_BAR_HEIGHT + EDITOR_PADDING);
        int textX = LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH;

        for (int i = scrollOffset; i < Math.min(lines.size(), scrollOffset + visibleLines); i++) {
            String lineStr = lines.get(i);
            String numStr = String.valueOf(i + 1);
            int numWidth = this.textRenderer.getWidth(numStr);
            context.drawText(this.textRenderer, numStr, numberX + LINE_NUMBER_WIDTH - numWidth - 8, lineY, COLOR_LINE_NUMBER, false);
            context.drawText(this.textRenderer, lineStr, textX, lineY, COLOR_TEXT, false);
            lineY += LINE_HEIGHT;
        }

        if (fullText.isEmpty() && scrollOffset == 0) {
            context.drawText(this.textRenderer, t("empty_placeholder"), textX, (TOP_BAR_HEIGHT + EDITOR_PADDING), COLOR_TEXT_MUTED, false);
        }

        if (cursorVisible) {
            int[] cursorLineCol = cursorVisualLine(fullText, lines, cursorPos);
            int cursorRenderLine = cursorLineCol[0];
            int cursorRenderCol = cursorLineCol[1];
            int cursorScreenLine = cursorRenderLine - scrollOffset;
            if (cursorScreenLine >= 0 && cursorScreenLine < visibleLines) {
                String cursorLineText = cursorRenderLine < lines.size() ? lines.get(cursorRenderLine) : "";
                int cursorX = textX + this.textRenderer.getWidth(cursorLineText.substring(0, Math.min(cursorRenderCol, cursorLineText.length())));
                int cursorY = (TOP_BAR_HEIGHT + EDITOR_PADDING) + cursorScreenLine * LINE_HEIGHT;
                context.fill(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT, COLOR_CURSOR);
            }
        }

        if (lines.size() > ((this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2) / LINE_HEIGHT)) {
            int scrollbarX = this.width - 8;
            int scrollbarHeight = editorHeight;
            int thumbHeight = Math.max(24, ((this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2) / LINE_HEIGHT) * scrollbarHeight / Math.max(1, lines.size()));
            int thumbY = (TOP_BAR_HEIGHT + EDITOR_PADDING) + scrollOffset * scrollbarHeight / Math.max(1, lines.size());
            context.fill(scrollbarX, (TOP_BAR_HEIGHT + EDITOR_PADDING), scrollbarX + 4, (TOP_BAR_HEIGHT + EDITOR_PADDING) + scrollbarHeight, 0xAA171717);
            context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, COLOR_BORDER);
        }
    }

    private void renderStatusBar(DrawContext context) {
        int barY = this.height - STATUS_BAR_HEIGHT;
        context.fill(0, barY, this.width, this.height, COLOR_STATUS_BAR);
        ConfigUiDraw.drawOutline(context, 0, barY, this.width, STATUS_BAR_HEIGHT, COLOR_BORDER);

        String fullText = currentText().toString();
        int currentTokens = estimateTokens(fullText);

        String statusText = fullText.length() + " chars"
                + "  |  ~" + currentTokens + " tokens";
        context.drawText(this.textRenderer, statusText, LEFT_PANEL_WIDTH + EDITOR_PADDING, barY + (STATUS_BAR_HEIGHT - this.textRenderer.fontHeight) / 2, COLOR_TEXT_MUTED, false);

        String defaultPrompt = PromptMessageBuilder.getDefaultPromptTemplate(selectedRoute);
        boolean hasOverride = !fullText.equals(defaultPrompt) && !fullText.isEmpty();
        if (hasOverride) {
            String overrideLabel = t("status.override_active").getString();
            int labelWidth = this.textRenderer.getWidth(overrideLabel);
            context.drawText(this.textRenderer, overrideLabel, this.width - labelWidth - 12, barY + (STATUS_BAR_HEIGHT - this.textRenderer.fontHeight) / 2, COLOR_STATUS_OK, false);
        }
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length()) {
                int codePoint = Character.toCodePoint(c, text.charAt(i + 1));
                i++;
                if (isCjkCodePoint(codePoint)) {
                    cjk++;
                } else {
                    other++;
                }
            } else if (isCjkChar(c)) {
                cjk++;
            } else if (c > 0x1F) {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }

    private static boolean isCjkChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x3000 && c <= 0x303F)
                || (c >= 0xFF00 && c <= 0xFFEF)
                || (c >= 0x3040 && c <= 0x309F)
                || (c >= 0x30A0 && c <= 0x30FF)
                || (c >= 0xAC00 && c <= 0xD7AF);
    }

    private static boolean isCjkCodePoint(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
                || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
                || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
                || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
                || (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF)
                || (codePoint >= 0x30000 && codePoint <= 0x3134F);
    }

    private int cursorPosFromLineCol(String fullText, int targetLine, int targetCol, List<String> visualLines) {
        if (fullText.isEmpty()) {
            return 0;
        }
        if (targetLine < 0 || visualLines.isEmpty()) {
            return 0;
        }
        if (targetLine >= visualLines.size()) {
            return fullText.length();
        }
        int pos = 0;
        for (int i = 0; i < targetLine && i < visualLines.size(); i++) {
            pos += visualLines.get(i).length();
            while (pos < fullText.length() && fullText.charAt(pos) == '\n') {
                pos++;
            }
        }
        return Math.min(pos + Math.min(targetCol, visualLines.get(targetLine).length()), fullText.length());
    }

    private int[] cursorVisualLine(String fullText, List<String> visualLines, int pos) {
        if (fullText.isEmpty() || visualLines.isEmpty()) {
            return new int[]{0, 0};
        }
        if (pos <= 0) {
            return new int[]{0, 0};
        }
        int flatPos = 0;
        for (int i = 0; i < visualLines.size(); i++) {
            String vl = visualLines.get(i);
            int segStart = flatPos;
            int segEnd = flatPos + vl.length();
            if (pos >= segStart && (pos <= segEnd || i == visualLines.size() - 1)) {
                return new int[]{i, Math.min(pos - segStart, vl.length())};
            }
            flatPos += vl.length();
            while (flatPos < fullText.length() && fullText.charAt(flatPos) == '\n') {
                flatPos++;
            }
        }
        return new int[]{visualLines.size() - 1, visualLines.get(visualLines.size() - 1).length()};
    }

    private void drawSelection(DrawContext context, String fullText, List<String> lines, int selStart, int selEnd, int scrollOffset) {
        int textX = LEFT_PANEL_WIDTH + EDITOR_PADDING + LINE_NUMBER_WIDTH;
        int textY = TOP_BAR_HEIGHT + EDITOR_PADDING;
        int visibleLines = (this.height - TOP_BAR_HEIGHT - STATUS_BAR_HEIGHT - EDITOR_PADDING * 2) / LINE_HEIGHT;

        int flatPos = 0;
        for (int i = 0; i < lines.size(); i++) {
            String vl = lines.get(i);
            int lineStart = flatPos;
            int lineEnd = flatPos + vl.length();

            if (lineEnd > selStart && lineStart < selEnd) {
                int selStartInLine = Math.max(0, selStart - lineStart);
                int selEndInLine = Math.min(vl.length(), selEnd - lineStart);
                if (selEndInLine > selStartInLine) {
                    int screenLine = i - scrollOffset;
                    if (screenLine >= 0 && screenLine < visibleLines) {
                        String beforeSel = vl.substring(0, selStartInLine);
                        String selText = vl.substring(selStartInLine, selEndInLine);
                        int selX = textX + this.textRenderer.getWidth(beforeSel);
                        int selW = this.textRenderer.getWidth(selText);
                        int selY = textY + screenLine * LINE_HEIGHT;
                        context.fill(selX, selY, selX + selW, selY + LINE_HEIGHT, COLOR_SELECTION);
                    }
                }
            }
            flatPos += vl.length();
            while (flatPos < fullText.length() && fullText.charAt(flatPos) == '\n') {
                flatPos++;
            }
        }
    }

    private int cursorColumnFromMouse(double mouseX, String lineText) {
        if (mouseX <= 0) return 0;
        for (int i = 1; i <= lineText.length(); i++) {
            if (this.textRenderer.getWidth(lineText.substring(0, i)) > mouseX) {
                return i - 1;
            }
        }
        return lineText.length();
    }

    private List<String> wrapLines(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty()) {
            return result;
        }
        String[] rawLines = text.split("\n", -1);
        for (String rawLine : rawLines) {
            result.addAll(wrapLine(rawLine, maxWidth));
        }
        return result;
    }

    private List<String> wrapLine(String line, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (line.isEmpty()) {
            result.add("");
            return result;
        }
        int start = 0;
        while (start < line.length()) {
            int end = start + 1;
            while (end <= line.length() && this.textRenderer.getWidth(line.substring(start, end)) <= maxWidth) {
                end++;
            }
            end--;
            if (end <= start) {
                end = start + 1;
            }
            result.add(line.substring(start, end));
            start = end;
        }
        return result;
    }

    private Text routeDisplayName(String routeKey) {
        return t("route." + routeKey);
    }

    private static boolean isCtrlDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static final int COLOR_STATUS_OK = 0xFF59D185;
}
