package com.alexeys.translate_allinone.gui.configui.modals;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.DictionaryConfig;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import com.alexeys.translate_allinone.utils.translate.DictionaryFileSelectionSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class DictionaryFilesModalSupport {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int LIST_GAP = 8;

    private final int scrollbarWidth;
    private final int scrollbarMinThumbHeight;
    private final Runnable onStateChanged;

    private DictionaryFileSelectionSupport.Slot slot = DictionaryFileSelectionSupport.Slot.WYNNCRAFT_DIALOGUE;
    private List<String> selectionBackup = new ArrayList<>();
    private int scrollOffset;
    private UiRect listViewport;
    private int maxScroll;
    private boolean draggingScrollbar;

    public DictionaryFilesModalSupport(int scrollbarWidth, int scrollbarMinThumbHeight, Runnable onStateChanged) {
        this.scrollbarWidth = scrollbarWidth;
        this.scrollbarMinThumbHeight = scrollbarMinThumbHeight;
        this.onStateChanged = onStateChanged;
    }

    public Component title(Translator translator) {
        return translator.t("modal.dictionary_files.title", slotLabel(translator, slot));
    }

    public void open(DictionaryFileSelectionSupport.Slot slot) {
        this.slot = slot == null ? DictionaryFileSelectionSupport.Slot.WYNNCRAFT_DIALOGUE : slot;
        this.scrollOffset = 0;
        this.maxScroll = 0;
        this.listViewport = null;
        this.draggingScrollbar = false;
        this.selectionBackup = new ArrayList<>(
                DictionaryFileSelectionSupport.getSelectedFiles(ensureDictionaryConfig(), this.slot)
        );
    }

    public void close(boolean keepChanges) {
        if (!keepChanges) {
            DictionaryFileSelectionSupport.setSelectedFiles(ensureDictionaryConfig(), slot, selectionBackup);
        }
        selectionBackup = new ArrayList<>();
        scrollOffset = 0;
        maxScroll = 0;
        listViewport = null;
        draggingScrollbar = false;
    }

    public boolean scrollBy(int rows) {
        if (maxScroll <= 0) {
            return false;
        }
        int next = Math.max(0, Math.min(maxScroll, scrollOffset + rows));
        if (next == scrollOffset) {
            return false;
        }
        scrollOffset = next;
        onStateChanged.run();
        return true;
    }

    public boolean pressScrollbar(double mouseX, double mouseY) {
        UiRect thumb = scrollbarThumbRect();
        if (thumb != null && thumb.contains(mouseX, mouseY)) {
            draggingScrollbar = true;
            return true;
        }
        UiRect track = scrollbarTrackRect();
        if (track == null || !track.contains(mouseX, mouseY)) {
            return false;
        }
        int next = offsetFromThumbMouseY(mouseY);
        if (next != scrollOffset) {
            scrollOffset = next;
            onStateChanged.run();
        }
        draggingScrollbar = true;
        return true;
    }

    public boolean isDraggingScrollbar() {
        return draggingScrollbar;
    }

    public void dragScrollbarTo(double mouseY) {
        int next = offsetFromThumbMouseY(mouseY);
        if (next != scrollOffset) {
            scrollOffset = next;
            onStateChanged.run();
        }
    }

    public void stopScrollbarDrag() {
        draggingScrollbar = false;
    }

    public void add(
            int screenWidth,
            int screenHeight,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            Style style,
            Consumer<Boolean> onCloseRequested
    ) {
        UiRect modalRect = ConfigUiModalSupport.dictionaryFilesModalRect(screenWidth, screenHeight);
        int contentX = modalRect.x + 16;
        int contentWidth = modalRect.width - 32;
        int rowY = modalRect.y + 48;
        int rowHeight = ROW_HEIGHT;
        int rowGap = ROW_GAP;
        int rowStep = rowHeight + rowGap;
        int maxRowsBottom = modalRect.bottom() - 58;

        DictionaryConfig dictionaryConfig = ensureDictionaryConfig();
        List<String> availableFiles = DictionaryFileSelectionSupport.listAvailableFiles();
        List<String> selectedFiles = DictionaryFileSelectionSupport.getSelectedFiles(dictionaryConfig, slot);

        actionBlockAdder.add(
                contentX,
                rowY,
                contentWidth,
                rowHeight,
                () -> translator.t("modal.dictionary_files.current", fileDisplayValue(translator, dictionaryConfig, slot)),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorText(),
                false
        );
        rowY += rowHeight + rowGap;

        actionBlockAdder.add(
                contentX,
                rowY,
                contentWidth,
                rowHeight,
                () -> translator.t("modal.dictionary_files.instructions"),
                () -> {
                },
                style.colorBlockMuted(),
                style.colorBlockMuted(),
                style.colorTextMuted(),
                false
        );
        rowY += rowHeight + 8;

        if (availableFiles.isEmpty()) {
            actionBlockAdder.add(
                    contentX,
                    rowY,
                    contentWidth,
                    rowHeight,
                    () -> translator.t("modal.dictionary_files.empty"),
                    () -> {
                    },
                    style.colorBlockMuted(),
                    style.colorBlockMuted(),
                    style.colorTextMuted(),
                    false
            );
        } else {
            int listTop = rowY;
            int maxVisible = Math.max(1, (maxRowsBottom - listTop + rowGap) / rowStep);
            maxScroll = Math.max(0, availableFiles.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            listViewport = new UiRect(
                    contentX,
                    listTop,
                    contentWidth,
                    Math.max(0, maxRowsBottom - listTop)
            );
            int rowWidth = maxScroll > 0
                    ? contentWidth - scrollbarWidth - LIST_GAP
                    : contentWidth;
            for (int i = scrollOffset; i < availableFiles.size(); i++) {
                if (rowY + rowHeight > maxRowsBottom) {
                    break;
                }
                String fileName = availableFiles.get(i);
                boolean selected = selectedFiles.contains(fileName);
                Component label = selected
                        ? translator.t("modal.dictionary_files.entry_selected", fileName)
                        : Component.literal(fileName);
                actionBlockAdder.add(
                        contentX,
                        rowY,
                        rowWidth,
                        rowHeight,
                        () -> label,
                        () -> selectFile(fileName),
                        selected ? style.colorBlockSelected() : style.colorBlock(),
                        selected ? style.colorBlockSelectedHover() : style.colorBlockHover(),
                        selected ? style.colorTextAccent() : style.colorText(),
                        false
                );
                rowY += rowStep;
            }
        }

        int buttonWidth = 104;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = modalRect.bottom() - 30;
        int doneX = modalRect.right() - 16 - buttonWidth;
        int cancelX = doneX - buttonGap - buttonWidth;
        int clearX = cancelX - buttonGap - buttonWidth;

        actionBlockAdder.add(
                clearX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.clear_selection"),
                () -> {
                    DictionaryFileSelectionSupport.setSelectedFiles(ensureDictionaryConfig(), slot, List.of());
                    onStateChanged.run();
                },
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
                () -> requestClose(onCloseRequested, false),
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true
        );

        actionBlockAdder.add(
                doneX,
                buttonY,
                buttonWidth,
                buttonHeight,
                () -> translator.t("button.done"),
                () -> requestClose(onCloseRequested, true),
                style.colorBlockAccent(),
                style.colorBlockAccentHover(),
                style.colorText(),
                true
        );
    }

    public void drawScrollbar(GuiGraphicsExtractor context, int mouseX, int mouseY, Style style) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null) {
            return;
        }
        context.fill(track.x, track.y, track.right(), track.bottom(), style.colorScrollTrack());
        boolean hovered = thumb.contains(mouseX, mouseY);
        int thumbColor = draggingScrollbar || hovered ? style.colorScrollThumbHover() : style.colorScrollThumb();
        context.fill(thumb.x, thumb.y, thumb.right(), thumb.bottom(), thumbColor);
        ConfigUiDraw.drawOutline(context, track.x, track.y, track.width, track.height, style.colorBorder());
    }

    private void selectFile(String fileName) {
        if (DictionaryFileSelectionSupport.isMultiSelect(slot)) {
            DictionaryFileSelectionSupport.toggleSelectedFile(ensureDictionaryConfig(), slot, fileName);
        } else {
            DictionaryFileSelectionSupport.setSelectedFile(ensureDictionaryConfig(), slot, fileName);
        }
        onStateChanged.run();
    }

    private void requestClose(Consumer<Boolean> onCloseRequested, boolean keepChanges) {
        onCloseRequested.accept(keepChanges);
        onStateChanged.run();
    }

    private UiRect scrollbarTrackRect() {
        if (listViewport == null || listViewport.height < ROW_HEIGHT || maxScroll <= 0) {
            return null;
        }
        int trackX = listViewport.right() - scrollbarWidth - 2;
        return new UiRect(trackX, listViewport.y, scrollbarWidth, listViewport.height);
    }

    private UiRect scrollbarThumbRect() {
        UiRect track = scrollbarTrackRect();
        if (track == null) {
            return null;
        }
        int extraHeight = maxScroll * (ROW_HEIGHT + ROW_GAP);
        int thumbHeight = Math.max(
                scrollbarMinThumbHeight,
                (int) Math.round(track.height * (track.height / (double) (track.height + extraHeight)))
        );
        thumbHeight = Math.min(track.height, thumbHeight);
        int travel = Math.max(0, track.height - thumbHeight);
        int thumbY = travel == 0
                ? track.y
                : track.y + (int) Math.round((scrollOffset / (double) Math.max(1, maxScroll)) * travel);
        return new UiRect(track.x, thumbY, track.width, thumbHeight);
    }

    private int offsetFromThumbMouseY(double mouseY) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null || maxScroll <= 0) {
            return scrollOffset;
        }
        int travel = Math.max(1, track.height - thumb.height);
        double anchorY = mouseY - thumb.height / 2.0;
        double ratio = Math.max(0.0, Math.min(1.0, (anchorY - track.y) / travel));
        return (int) Math.round(ratio * maxScroll);
    }

    private static Component slotLabel(Translator translator, DictionaryFileSelectionSupport.Slot slot) {
        return switch (slot) {
            case ITEM_SKILL -> translator.t("label.dictionary_slot_item_skill");
            case WYNNCRAFT_DIALOGUE -> translator.t("label.dictionary_slot_dialogue");
            case WYNNCRAFT_QUEST -> translator.t("label.dictionary_slot_quest");
        };
    }

    private static Component fileDisplayValue(
            Translator translator,
            DictionaryConfig dictionaryConfig,
            DictionaryFileSelectionSupport.Slot slot
    ) {
        String effectiveSelection = DictionaryFileSelectionSupport.describeEffectiveSelection(dictionaryConfig, slot);
        if (effectiveSelection == null || effectiveSelection.isBlank()) {
            return translator.t("value.dictionary_file_none_selected");
        }
        return Component.literal(effectiveSelection);
    }

    private static DictionaryConfig ensureDictionaryConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config.dictionary == null) {
            config.dictionary = new DictionaryConfig();
        }
        config.dictionary.normalize();
        return config.dictionary;
    }

    public record Style(
            int colorBlockMuted,
            int colorBlock,
            int colorBlockHover,
            int colorBlockSelected,
            int colorBlockSelectedHover,
            int colorBlockAccent,
            int colorBlockAccentHover,
            int colorBlockDanger,
            int colorBlockDangerHover,
            int colorText,
            int colorTextMuted,
            int colorTextAccent,
            int colorScrollTrack,
            int colorScrollThumb,
            int colorScrollThumbHover,
            int colorBorder
    ) {
    }
}
