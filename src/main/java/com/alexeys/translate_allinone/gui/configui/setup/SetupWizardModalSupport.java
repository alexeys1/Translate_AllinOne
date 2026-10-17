package com.alexeys.translate_allinone.gui.configui.setup;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.gui.configui.model.HotkeyTarget;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.gui.configui.sections.RouteModelSectionSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigContentScrollSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ActionBlockAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ProviderTypeLabelProvider;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.StatusSetter;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.TextFieldAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.ToggleAdder;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.gui.configui.support.ModelSettingsApplySupport;
import com.alexeys.translate_allinone.gui.configui.support.ModelSettingsDraftSupport;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.alexeys.translate_allinone.utils.config.ui.ProviderEditorSupport;
import com.alexeys.translate_allinone.utils.config.ui.ProviderManagerMutationSupport;
import com.alexeys.translate_allinone.utils.config.ui.ProviderProfileSupport;
import com.alexeys.translate_allinone.utils.config.ui.RouteSlot;
import com.alexeys.translate_allinone.utils.config.ui.SetupWizardSupport;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class SetupWizardModalSupport {
    private static final int SHELL_HEADER_HEIGHT = 36;
    private static final int HEADER_BAND_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int PADDING = 16;
    private static final int MIN_VIEWPORT_HEIGHT = 48;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_STEP = 24;
    private static final int DROPDOWN_OPTION_STEP = 22;
    private static final int DROPDOWN_OPTION_GAP = DROPDOWN_OPTION_STEP - ROW_HEIGHT;
    private static final int FIELD_STEP = 34;
    private static final int TEXT_ROW_STEP = 12;
    private static final int CONTENT_GAP = 14;
    private static final int GROUP_GAP = 20;
    private static final int COLUMN_GAP = 24;
    private static final int MIN_COLUMN_WIDTH = 200;
    private static final int TEXT_FIELD_MAX_LENGTH = 256;
    private static final int STEP_COUNT = 3;
    private static final int BUTTON_GAP = 8;
    private static final int SKIP_BUTTON_WIDTH = 120;
    private static final int BACK_BUTTON_WIDTH = 90;
    private static final int NEXT_BUTTON_WIDTH = 130;
    private static final int DETECT_BUTTON_WIDTH = 150;
    private static final int SECRET_BUTTON_WIDTH = 90;
    private static final int PROGRESS_BAR_HEIGHT = 4;

    private static final ModuleRow[] MAIN_MODULE_ROWS = {
            new ModuleRow(SetupWizardSupport.Module.CHAT_OUTPUT, "section.chat_output", "desc.chat_output_enabled"),
            new ModuleRow(SetupWizardSupport.Module.CHAT_INPUT, "section.chat_input", "desc.chat_input_enabled"),
            new ModuleRow(SetupWizardSupport.Module.ITEM, "section.item", "desc.item_enabled"),
            new ModuleRow(SetupWizardSupport.Module.SCOREBOARD, "section.scoreboard", "desc.scoreboard_enabled")
    };

    private static final ModuleRow[] OTHER_MODULE_ROWS = {
            new ModuleRow(SetupWizardSupport.Module.OTHER_SCREEN_UI, "label.screen_translation_enabled", "desc.screen_translation_enabled"),
            new ModuleRow(SetupWizardSupport.Module.OTHER_ADVANCEMENTS, "label.translate_vanilla_advancements", "desc.translate_vanilla_advancements"),
            new ModuleRow(SetupWizardSupport.Module.OTHER_SIGNS, "label.translate_signs", "desc.translate_signs"),
            new ModuleRow(SetupWizardSupport.Module.OTHER_ENTITY_TEXT, "label.translate_entity_text", "desc.translate_entity_text"),
            new ModuleRow(SetupWizardSupport.Module.OTHER_WRITTEN_BOOKS, "label.translate_written_books", "desc.translate_written_books")
    };

    private static final ModuleRow[] WYNNCRAFT_MODULE_ROWS = {
            new ModuleRow(SetupWizardSupport.Module.WYNN_NPC_DIALOGUE, "group.wynn_npc_dialogue", "desc.wynn_npc_dialogue_enabled"),
            new ModuleRow(SetupWizardSupport.Module.WYNNTILS_TASK_TRACKER, "group.wynntils_task_tracker", "desc.wynntils_task_tracker_enabled")
    };

    private static final ModuleRow[][] MODULE_ROW_TABLES = {MAIN_MODULE_ROWS, OTHER_MODULE_ROWS, WYNNCRAFT_MODULE_ROWS};

    private static final HotkeyGroup[] HOTKEY_GROUPS = {
            new HotkeyGroup(
                    "section.chat_input",
                    new SetupWizardSupport.Module[]{SetupWizardSupport.Module.CHAT_INPUT},
                    new HotkeyEntry[]{
                            new HotkeyEntry(HotkeyTarget.CHAT_INPUT, "desc.chat_input_hotkey", false)
                    }
            ),
            new HotkeyGroup(
                    "section.item",
                    new SetupWizardSupport.Module[]{SetupWizardSupport.Module.ITEM},
                    new HotkeyEntry[]{
                            new HotkeyEntry(HotkeyTarget.ITEM, "desc.item_hotkey_mode", true),
                            new HotkeyEntry(HotkeyTarget.ITEM, "desc.item_hotkey", false),
                            new HotkeyEntry(HotkeyTarget.ITEM_REFRESH, "desc.item_refresh_hotkey", false)
                    }
            ),
            new HotkeyGroup(
                    "section.scoreboard",
                    new SetupWizardSupport.Module[]{SetupWizardSupport.Module.SCOREBOARD},
                    new HotkeyEntry[]{
                            new HotkeyEntry(HotkeyTarget.SCOREBOARD, "desc.scoreboard_hotkey_mode", true),
                            new HotkeyEntry(HotkeyTarget.SCOREBOARD, "desc.scoreboard_hotkey", false),
                            new HotkeyEntry(HotkeyTarget.SCOREBOARD_REFRESH, "desc.scoreboard_refresh_hotkey", false)
                    }
            ),
            new HotkeyGroup(
                    "section.other_translations",
                    new SetupWizardSupport.Module[]{
                            SetupWizardSupport.Module.OTHER_SCREEN_UI,
                            SetupWizardSupport.Module.OTHER_ADVANCEMENTS,
                            SetupWizardSupport.Module.OTHER_SIGNS,
                            SetupWizardSupport.Module.OTHER_ENTITY_TEXT,
                            SetupWizardSupport.Module.OTHER_WRITTEN_BOOKS
                    },
                    new HotkeyEntry[]{
                            new HotkeyEntry(HotkeyTarget.OTHER_TRANSLATIONS, "desc.other_translations_hotkey_mode", true),
                            new HotkeyEntry(HotkeyTarget.OTHER_TRANSLATIONS, "desc.other_translations_hotkey", false),
                            new HotkeyEntry(HotkeyTarget.OTHER_TRANSLATIONS_REFRESH, "desc.other_translations_refresh_hotkey", false)
                    }
            ),
            new HotkeyGroup(
                    "group.wynntils_task_tracker",
                    new SetupWizardSupport.Module[]{SetupWizardSupport.Module.WYNNTILS_TASK_TRACKER},
                    new HotkeyEntry[]{
                            new HotkeyEntry(HotkeyTarget.WYNNTILS_TASK_TRACKER, "desc.wynn_hotkey_mode", true),
                            new HotkeyEntry(HotkeyTarget.WYNNTILS_TASK_TRACKER, "desc.wynn_hotkey", false),
                            new HotkeyEntry(HotkeyTarget.WYNNTILS_TASK_TRACKER_REFRESH, "desc.wynn_refresh_hotkey", false)
                    }
            )
    };

    private final ConfigContentScrollSupport scroll;
    private final Translator translator;
    private final StatusSetter statusSetter;
    private final int statusErrorColor;
    private final ActionBlockAdder contentAdder;
    private final ActionBlockAdder dropdownAdder;
    private final ActionBlockAdder footerAdder;
    private final ToggleAdder toggleAdder;
    private final TextFieldAdder textFieldAdder;
    private final SecretFieldAdder secretFieldAdder;
    private final ProviderTypeLabelProvider providerTypeLabelProvider;
    private final HotkeySupport hotkeys;
    private final LiveActionAdder liveActionAdder;
    private final Runtime runtime;
    private final Style style;

    private final List<TextRow> headerRows = new ArrayList<>();
    private final List<TextRow> contentRows = new ArrayList<>();
    private final List<LiveRow> liveRows = new ArrayList<>();

    private boolean open;
    private boolean forced;
    private int step = 1;
    private String providerId = "";
    private String landedModelId = "";
    private String nameDraft = "";
    private ApiProviderType typeDraft = ApiProviderType.OPENAI_COMPAT;
    private String baseUrlDraft = "";
    private String apiKeyDraft = "";
    private String modelIdDraft = "";
    private boolean typeDropdownOpen;
    private boolean apiKeyVisible;
    private boolean connectionTested;
    private boolean connectionOk;
    private boolean stepFocusPending;
    private EditBox nameField;
    private EditBox baseUrlField;
    private EditBox modelIdField;
    private UiRect viewport = new UiRect(0, 0, 0, 0);
    private int footerButtonY;
    private int contentWidth;

    public SetupWizardModalSupport(
            ConfigContentScrollSupport scroll,
            Translator translator,
            StatusSetter statusSetter,
            int statusErrorColor,
            ActionBlockAdder contentAdder,
            ActionBlockAdder dropdownAdder,
            ActionBlockAdder footerAdder,
            ToggleAdder toggleAdder,
            TextFieldAdder textFieldAdder,
            SecretFieldAdder secretFieldAdder,
            ProviderTypeLabelProvider providerTypeLabelProvider,
            HotkeySupport hotkeys,
            LiveActionAdder liveActionAdder,
            Runtime runtime,
            Style style
    ) {
        this.scroll = scroll;
        this.translator = translator;
        this.statusSetter = statusSetter;
        this.statusErrorColor = statusErrorColor;
        this.contentAdder = contentAdder;
        this.dropdownAdder = dropdownAdder;
        this.footerAdder = footerAdder;
        this.toggleAdder = toggleAdder;
        this.textFieldAdder = textFieldAdder;
        this.secretFieldAdder = secretFieldAdder;
        this.providerTypeLabelProvider = providerTypeLabelProvider;
        this.hotkeys = hotkeys;
        this.liveActionAdder = liveActionAdder;
        this.runtime = runtime;
        this.style = style;
    }

    public boolean isOpen() {
        return open;
    }

    public UiRect viewportRect() {
        return viewport;
    }

    public boolean isTypeDropdownOpen() {
        return typeDropdownOpen;
    }

    public void closeTypeDropdown() {
        this.typeDropdownOpen = false;
    }

    public void open(boolean forced) {
        this.open = true;
        this.forced = forced;
        this.step = 1;
        this.providerId = "";
        this.landedModelId = "";
        this.nameDraft = "";
        this.typeDraft = ApiProviderType.OPENAI_COMPAT;
        this.baseUrlDraft = ProviderEditorSupport.createProfileByType(ApiProviderType.OPENAI_COMPAT).base_url;
        this.apiKeyDraft = "";
        this.modelIdDraft = "";
        this.typeDropdownOpen = false;
        this.apiKeyVisible = false;
        this.connectionTested = false;
        this.connectionOk = false;
        if (!forced) {
            SetupWizardSupport.applyRecommendedSelection(config());
        }
        this.stepFocusPending = true;
        scroll.resetOffset();
        this.nameField = null;
        this.baseUrlField = null;
        this.modelIdField = null;
    }

    public void close() {
        this.open = false;
        this.typeDropdownOpen = false;
        scroll.resetOffset();
    }

    public void add(int screenWidth, int screenHeight) {
        if (!open) {
            return;
        }

        headerRows.clear();
        contentRows.clear();
        liveRows.clear();

        UiRect rect = ConfigUiModalSupport.setupWizardModalRect(screenWidth, screenHeight);
        int contentX = rect.x + PADDING;
        int available = Math.max(0, rect.height - SHELL_HEADER_HEIGHT);
        int headerBand = Math.min(HEADER_BAND_HEIGHT, available / 4);
        int footerHeight = Math.max(ROW_HEIGHT + 8, Math.min(FOOTER_HEIGHT, available / 2));
        footerHeight = Math.min(footerHeight, Math.max(ROW_HEIGHT, available));
        int footerTop = rect.bottom() - footerHeight;
        this.footerButtonY = Math.min(
                rect.bottom() - ROW_HEIGHT - 2,
                footerTop + Math.max(0, (footerHeight - ROW_HEIGHT) / 2)
        );

        int viewportBottom = Math.min(footerTop, footerButtonY);
        int viewportMinTop = rect.y + SHELL_HEADER_HEIGHT;
        int viewportDesiredTop = viewportMinTop + headerBand;
        int viewportTop = Math.max(viewportMinTop, Math.min(viewportDesiredTop, viewportBottom - MIN_VIEWPORT_HEIGHT));
        int viewportHeight = Math.max(ROW_STEP, viewportBottom - viewportTop);
        this.viewport = new UiRect(contentX, viewportTop, Math.max(80, rect.width - PADDING * 2), viewportHeight);
        scroll.useViewport(viewport);
        this.contentWidth = scroll.layoutWidth();

        addHeaderRows(rect);
        addFooter(rect);

        int contentTopY = viewport.y - scroll.currentRenderOffset();
        int contentBottom = switch (step) {
            case 2 -> addModulesStep(contentX, contentTopY);
            case 3 -> addRoutesAndHotkeysStep(contentX, contentTopY);
            default -> addProviderStep(contentX, contentTopY);
        };
        scroll.updateBounds(contentBottom);
    }

    public void renderContent(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight) {
        if (!open) {
            return;
        }

        UiRect rect = ConfigUiModalSupport.setupWizardModalRect(screenWidth, screenHeight);
        for (TextRow row : headerRows) {
            drawRow(context, font, row);
        }

        int barX = rect.x + PADDING;
        int barWidth = Math.max(1, rect.width - PADDING * 2);
        int barY = rect.y + SHELL_HEADER_HEIGHT + 24;
        context.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, style.colorProgressTrack());
        int filled = Math.max(1, barWidth * step / STEP_COUNT);
        context.fill(barX, barY, barX + filled, barY + PROGRESS_BAR_HEIGHT, style.colorProgressFill());

        ConfigUiDraw.withScissor(context, viewport, () -> {
            for (TextRow row : contentRows) {
                drawRow(context, font, row);
            }
            for (LiveRow row : liveRows) {
                Component text = row.text().get();
                if (text == null) {
                    continue;
                }
                drawText(context, font, row.x(), row.y(), row.maxWidth(), row.color().getAsInt(), text);
            }
        });
    }

    private void addHeaderRows(UiRect rect) {
        int y = rect.y + SHELL_HEADER_HEIGHT + 6;
        addHeaderRow(viewport.x, y, 0, translator.t(stepTitleKey()), false);
        addHeaderRow(viewport.right(), y, 0, translator.t("setup.step_indicator", step, STEP_COUNT), true);
    }

    private void addFooter(UiRect rect) {
        int y = footerButtonY;
        int nextX = viewport.right() - NEXT_BUTTON_WIDTH;
        int backX = nextX - BUTTON_GAP - BACK_BUTTON_WIDTH;

        footerAdder.add(
                rect.x + PADDING,
                y,
                SKIP_BUTTON_WIDTH,
                ROW_HEIGHT,
                () -> translator.t("setup.button.skip_all"),
                runtime::onSetupSkipped,
                style.colorBlockDanger(),
                style.colorBlockDangerHover(),
                style.colorText(),
                true,
                translator.t("setup.button.skip_all.tooltip")
        );

        if (step > 1) {
            footerAdder.add(
                    backX,
                    y,
                    BACK_BUTTON_WIDTH,
                    ROW_HEIGHT,
                    () -> translator.t("setup.button.back"),
                    () -> goToStep(step - 1),
                    style.colorBlock(),
                    style.colorBlockHover(),
                    style.colorText(),
                    true,
                    null
            );
        }

        liveActionAdder.add(
                nextX,
                y,
                NEXT_BUTTON_WIDTH,
                ROW_HEIGHT,
                () -> translator.t(step < STEP_COUNT ? "setup.button.next" : "setup.button.finish"),
                step < STEP_COUNT ? this::nextStep : this::complete,
                () -> stepAvailable() ? style.colorAccent() : style.colorBlockMuted(),
                () -> stepAvailable() ? style.colorAccentHover() : style.colorBlockHover(),
                style.colorText(),
                true,
                () -> stepAvailable() ? null : translator.t("setup.error.required_fields", missingFieldsLabel()),
                () -> true
        );
    }

    private int addProviderStep(int contentX, int top) {
        int width = contentWidth;
        int y = top;

        addContentRow(contentX, y, width, translator.t("setup.field.type"), false);
        addTypeSelector(contentX, y + 11, width);
        y += FIELD_STEP;

        y = addTextRow(contentX, y, width, "setup.field.name", "setup.placeholder.name", nameDraft, true,
                value -> nameDraft = value, field -> nameField = field);
        y = addTextRow(contentX, y, width, "setup.field.base_url", "setup.placeholder.base_url", baseUrlDraft, true,
                value -> baseUrlDraft = value, field -> baseUrlField = field);
        y = addApiKeyRow(contentX, y, width);
        y = addTextRow(contentX, y, width, "setup.field.model_id", "setup.placeholder.model_id", modelIdDraft, true,
                value -> modelIdDraft = value, field -> modelIdField = field);

        y += CONTENT_GAP;
        contentAdder.add(
                contentX,
                y,
                DETECT_BUTTON_WIDTH,
                ROW_HEIGHT,
                () -> translator.t("setup.button.detect"),
                this::testConnection,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true,
                translator.t("setup.button.detect.tooltip")
        );
        y += ROW_STEP;

        addLiveContentRow(contentX, y, width, this::connectionStatusLabel, this::connectionStatusColor);
        y += TEXT_ROW_STEP + CONTENT_GAP;

        if (forced) {
            addContentRow(contentX, y, width, translator.t("setup.provider.existing_untouched"), false);
        } else {
            addLiveContentRow(contentX, y, width, () -> translator.t("setup.provider.detected", ProviderEditorSupport.previewRequestAddress(buildProbeProfile())), style::colorTextMuted);
        }
        y += TEXT_ROW_STEP;

        if (stepFocusPending) {
            stepFocusPending = false;
            EditBox focusField = nameField != null ? nameField : (baseUrlField != null ? baseUrlField : modelIdField);
            if (focusField != null) {
                runtime.onRequestFocus(focusField);
            }
        }

        return y;
    }

    private void addTypeSelector(int x, int y, int width) {
        contentAdder.add(
                x,
                y,
                width,
                ROW_HEIGHT,
                () -> providerTypeLabelProvider.label(typeDraft),
                () -> {
                    typeDropdownOpen = !typeDropdownOpen;
                    runtime.onRebuild();
                },
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                false,
                translator.t("setup.field.type.tooltip")
        );

        if (!typeDropdownOpen) {
            return;
        }

        ApiProviderType[] types = ApiProviderType.values();
        int dropdownHeight = ROW_HEIGHT + (types.length - 1) * DROPDOWN_OPTION_STEP;
        int belowTop = y + DROPDOWN_OPTION_STEP;
        int aboveTop = y - dropdownHeight - DROPDOWN_OPTION_GAP;
        int availableBelow = Math.max(0, viewport.bottom() - belowTop);
        int availableAbove = Math.max(0, (y - DROPDOWN_OPTION_GAP) - viewport.y);
        boolean openUpward = dropdownHeight > availableBelow && availableAbove > availableBelow;
        int optionY = clampDropdownTop(openUpward ? aboveTop : belowTop, dropdownHeight);

        for (ApiProviderType type : types) {
            boolean selected = type == typeDraft;
            dropdownAdder.add(
                    x,
                    optionY,
                    width,
                    ROW_HEIGHT,
                    () -> providerTypeLabelProvider.label(type),
                    () -> selectProviderType(type),
                    selected ? style.colorAccent() : style.colorBlock(),
                    selected ? style.colorAccentHover() : style.colorBlockHover(),
                    selected ? style.colorTextAccent() : style.colorText(),
                    false,
                    null
            );
            optionY += DROPDOWN_OPTION_STEP;
        }
    }

    private int clampDropdownTop(int preferredTop, int dropdownHeight) {
        int maxTop = viewport.bottom() - dropdownHeight;
        if (maxTop < viewport.y) {
            return viewport.y;
        }
        return Math.max(viewport.y, Math.min(maxTop, preferredTop));
    }

    private int addTextRow(
            int x,
            int y,
            int width,
            String labelKey,
            String placeholderKey,
            String initialValue,
            boolean editable,
            Consumer<String> changed,
            Consumer<EditBox> fieldConsumer
    ) {
        addContentRow(x, y, width, translator.t(labelKey), false);
        EditBox field = textFieldAdder.add(
                x,
                y + 11,
                width,
                TEXT_FIELD_MAX_LENGTH,
                initialValue,
                translator.t(placeholderKey),
                changed,
                editable
        );
        fieldConsumer.accept(field);
        return y + FIELD_STEP;
    }

    private int addApiKeyRow(int x, int y, int width) {
        boolean localService = typeDraft == ApiProviderType.OLLAMA;
        addContentRow(x, y, width, translator.t("setup.field.api_key"), false);
        if (localService) {
            addContentRow(x, y + 11, width, translator.t("setup.field.api_key.local"), false);
            return y + FIELD_STEP;
        }

        int fieldWidth = Math.max(60, width - SECRET_BUTTON_WIDTH - BUTTON_GAP);
        secretFieldAdder.add(
                x,
                y + 11,
                fieldWidth,
                TEXT_FIELD_MAX_LENGTH,
                apiKeyDraft,
                translator.t("setup.placeholder.api_key"),
                value -> apiKeyDraft = value,
                !apiKeyVisible
        );
        contentAdder.add(
                x + fieldWidth + BUTTON_GAP,
                y + 11,
                SECRET_BUTTON_WIDTH,
                ROW_HEIGHT,
                () -> translator.t(apiKeyVisible ? "button.hide_key" : "button.show_key"),
                () -> {
                    apiKeyVisible = !apiKeyVisible;
                    runtime.onRebuild();
                },
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true,
                translator.t("desc.hide_key")
        );
        return y + FIELD_STEP;
    }

    private int addModulesStep(int contentX, int top) {
        int y = top;
        addContentRow(contentX, y, contentWidth, translator.t("setup.modules.setting"), false);
        y += TEXT_ROW_STEP + GROUP_GAP;

        boolean singleColumn = contentWidth < MIN_COLUMN_WIDTH * 2 + COLUMN_GAP;
        if (singleColumn) {
            y = addModuleGroup("setup.modules.group.main", MAIN_MODULE_ROWS, contentX, y, contentWidth);
            y += GROUP_GAP;
            y = addModuleGroup("setup.modules.group.other", OTHER_MODULE_ROWS, contentX, y, contentWidth);
            y += GROUP_GAP;
            return addModuleGroup("setup.modules.group.wynncraft", WYNNCRAFT_MODULE_ROWS, contentX, y, contentWidth);
        }

        int columnWidth = (contentWidth - COLUMN_GAP) / 2;
        int rightX = contentX + columnWidth + COLUMN_GAP;
        int leftBottom = addModuleGroup("setup.modules.group.main", MAIN_MODULE_ROWS, contentX, y, columnWidth);
        leftBottom += GROUP_GAP;
        leftBottom = addModuleGroup("setup.modules.group.wynncraft", WYNNCRAFT_MODULE_ROWS, contentX, leftBottom, columnWidth);
        int rightBottom = addModuleGroup("setup.modules.group.other", OTHER_MODULE_ROWS, rightX, y, columnWidth);
        return Math.max(leftBottom, rightBottom);
    }

    private int addModuleGroup(String titleKey, ModuleRow[] rows, int x, int y, int width) {
        addContentRow(x, y, width, translator.t(titleKey), false);
        y += TEXT_ROW_STEP + 2;

        for (ModuleRow row : rows) {
            SetupWizardSupport.Module module = row.module();
            toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t(row.labelKey()),
                        () -> SetupWizardSupport.isModuleEnabled(config(), module),
                        value -> {
                            SetupWizardSupport.setModuleEnabled(config(), module, value);
                            runtime.onRebuild();
                        },
                        translator.t(row.tooltipKey()),
                    module.recommended()
            );
            y += ROW_STEP;
        }
        return y;
    }

    private int addRoutesAndHotkeysStep(int contentX, int top) {
        boolean twoColumns = contentWidth >= MIN_COLUMN_WIDTH * 2 + COLUMN_GAP;
        int columnWidth = twoColumns ? (contentWidth - COLUMN_GAP) / 2 : contentWidth;
        int rightX = twoColumns ? contentX + columnWidth + COLUMN_GAP : contentX;

        int summaryBottom = addSummary(contentX, top, columnWidth);
        int hotkeysTop = twoColumns ? top : summaryBottom + GROUP_GAP;
        int hotkeysBottom = addHotkeysColumn(rightX, hotkeysTop, columnWidth);

        return Math.max(summaryBottom, hotkeysBottom);
    }

    private int addHotkeysColumn(int x, int top, int width) {
        int y = top;

        addContentRow(x, y, width, translator.t("setup.hotkeys.title"), false);
        y += TEXT_ROW_STEP + 4;
        addContentRow(x, y, width, translator.t("setup.hotkeys.filtered"), false);
        y += TEXT_ROW_STEP + 4;

        boolean anyGroup = false;
        for (HotkeyGroup group : HOTKEY_GROUPS) {
            if (!isHotkeyGroupVisible(group)) {
                continue;
            }
            anyGroup = true;
            addContentRow(x, y, width, translator.t(group.titleKey()), false);
            y += TEXT_ROW_STEP + 2;

            for (HotkeyEntry entry : group.entries()) {
                y = addHotkeyRow(x, y, width, hotkeyRowLabel(entry), hotkeyRowAction(entry), translator.t(entry.descKey()));
            }
            y += 6;
        }

        if (!anyGroup) {
            addContentRow(x, y, width, translator.t("setup.hotkeys.none"), false);
            y += TEXT_ROW_STEP;
        }

        return y;
    }

    private Supplier<Component> hotkeyRowLabel(HotkeyEntry entry) {
        return entry.modeRow() ? () -> hotkeys.modeLabel(entry.target()) : () -> hotkeys.bindingLabel(entry.target());
    }

    private Runnable hotkeyRowAction(HotkeyEntry entry) {
        return entry.modeRow() ? () -> hotkeys.cycleMode(entry.target()) : () -> hotkeys.startCapture(entry.target());
    }

    private int addHotkeyRow(int x, int y, int width, Supplier<Component> label, Runnable action, Component tooltip) {
        contentAdder.add(
                x,
                y,
                width,
                ROW_HEIGHT,
                label,
                action,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                true,
                tooltip
        );
        return y + ROW_STEP;
    }

    private boolean isHotkeyGroupVisible(HotkeyGroup group) {
        for (SetupWizardSupport.Module module : group.modules()) {
            if (SetupWizardSupport.isModuleEnabled(config(), module)) {
                return true;
            }
        }
        return false;
    }

    private int addSummary(int contentX, int top, int width) {
        int y = top;
        ApiProviderProfile profile = currentProfile();

        addContentRow(contentX, y, width, translator.t("setup.summary.title"), false);
        y += TEXT_ROW_STEP + 4;

        String providerText = profile == null
                ? translator.t("setup.summary.provider.missing").getString()
                : translator.t(
                        "setup.summary.provider",
                        ProviderProfileSupport.safeProviderName(profile),
                        providerTypeLabelProvider.label(profile.type).getString(),
                        modelIdDraft.trim()
                ).getString();
        addContentRow(contentX, y, width, Component.literal(providerText), false);
        y += TEXT_ROW_STEP;

        addContentRow(contentX, y, width, translator.t("setup.summary.modules", enabledModulesLabel()), false);
        y += TEXT_ROW_STEP;

        addContentRow(contentX, y, width, translator.t("setup.summary.routes", RouteSlot.selectableSlots().size(), enabledModuleCount()), false);
        y += TEXT_ROW_STEP;

        addContentRow(contentX, y, width, translator.t("setup.summary.api_key", apiKeyStateLabel()), false);
        y += TEXT_ROW_STEP;

        if (connectionTested && connectionOk) {
            addLiveContentRow(contentX, y, width, () -> translator.t("setup.connection.ok"), style::colorOk);
            y += TEXT_ROW_STEP;
        }

        SetupWizardSupport.SummaryChecks checks = SetupWizardSupport.evaluateSummary(config(), profile, connectionTested && connectionOk);
        if (checks.noModuleEnabled()) {
            addContentRow(contentX, y, width, translator.t("setup.warning.no_modules"), true);
            y += TEXT_ROW_STEP;
        }
        if (checks.apiKeyMissing()) {
            addContentRow(contentX, y, width, translator.t("setup.warning.api_key_missing"), true);
            y += TEXT_ROW_STEP;
        }
        if (checks.connectionUnverified()) {
            addContentRow(contentX, y, width, translator.t("setup.warning.connection_unverified"), true);
            y += TEXT_ROW_STEP;
        }

        return y;
    }

    private void nextStep() {
        if (!stepAvailable()) {
            statusSetter.set(translator.t("setup.error.required_fields", missingFieldsLabel()), statusErrorColor);
            return;
        }
        if (step == 1) {
            landProvider();
        }
        goToStep(step + 1);
    }

    private void goToStep(int nextStep) {
        this.step = nextStep;
        scroll.resetOffset();
        this.typeDropdownOpen = false;
        this.stepFocusPending = nextStep == 1;
        runtime.onRebuild();
    }

    private void complete() {
        if (!open) {
            return;
        }

        ApiProviderProfile profile = currentProfile();
        if (profile == null) {
            statusSetter.set(translator.t("setup.error.provider_missing"), statusErrorColor);
            return;
        }

        applyModel(profile);
        String modelId = landedModelId.isBlank() ? profile.model_id : landedModelId;
        String routeKey = ProviderManagerConfig.composeRouteKey(profile.id, modelId);
        for (RouteSlot slot : RouteSlot.selectableSlots()) {
            RouteModelSectionSupport.setRouteKey(config().providerManager, slot, routeKey);
        }
        runtime.onSetupCompleted();
    }

    private void selectProviderType(ApiProviderType type) {
        typeDraft = type;
        typeDropdownOpen = false;
        baseUrlDraft = ProviderEditorSupport.createProfileByType(type).base_url;
        runtime.onRebuild();
    }

    private void testConnection() {
        ApiProviderProfile probe = buildProbeProfile();
        runtime.onTestConnection(probe, success -> {
            connectionTested = true;
            connectionOk = success;
            runtime.onRebuild();
        });
    }

    private void landProvider() {
        ProviderManagerConfig manager = config().providerManager;
        manager.ensureDefaults();

        ApiProviderProfile profile = manager.findById(providerId);
        if (profile == null) {
            ProviderManagerMutationSupport.AddProviderResult result =
                    ProviderManagerMutationSupport.addProviderFromDraft(manager, nameDraft, typeDraft);
            profile = result.profile();
            providerId = result.selectedProviderId();
            landedModelId = "";
        }

        profile.name = resolvedName();
        profile.type = typeDraft;
        profile.base_url = baseUrlDraft.trim();
        profile.api_key = apiKeyDraft.trim();
        applyModel(profile);
        runtime.onProviderLanded(profile);
    }

    private void applyModel(ApiProviderProfile profile) {
        String modelId = modelIdDraft.trim();
        if (modelId.isEmpty()) {
            return;
        }

        ModelSettingsDraftSupport.Draft defaults = ModelSettingsDraftSupport.fromProfile(profile, "");
        ModelSettingsApplySupport.ApplyResult result = ModelSettingsApplySupport.apply(
                profile,
                landedModelId,
                modelId,
                defaults.chatTemperatureDraft(),
                defaults.itemTemperatureDraft(),
                defaults.scoreboardTemperatureDraft(),
                defaults.otherTranslationsTemperatureDraft(),
                defaults.wynntilsTaskTrackerTemperatureDraft(),
                defaults.wynnNpcDialogueTemperatureDraft(),
                defaults.keepAliveDraft(),
                defaults.supportsSystem(),
                defaults.systemPromptSuffixDraft(),
                defaults.customParametersDraft(),
                true
        );
        if (result.success()) {
            landedModelId = result.modelId();
        }
    }

    private ApiProviderProfile buildProbeProfile() {
        ApiProviderProfile profile = ProviderEditorSupport.createProfileByType(typeDraft);
        profile.name = resolvedName();
        profile.base_url = baseUrlDraft.trim();
        profile.api_key = apiKeyDraft.trim();
        profile.model_id = modelIdDraft.trim();
        return profile;
    }

    private ApiProviderProfile currentProfile() {
        if (providerId.isBlank()) {
            return null;
        }
        return config().providerManager.findById(providerId);
    }

    private String resolvedName() {
        String name = ProviderProfileSupport.sanitizeText(nameDraft).trim();
        return name.isEmpty() ? ProviderEditorSupport.defaultProviderName(typeDraft) : name;
    }

    private boolean stepAvailable() {
        if (step != 1) {
            return true;
        }
        return missingFieldsLabel().isEmpty();
    }

    private String missingFieldsLabel() {
        List<String> missing = new ArrayList<>();
        if (baseUrlDraft.trim().isEmpty()) {
            missing.add(translator.t("setup.field.base_url").getString());
        }
        if (typeDraft != ApiProviderType.OLLAMA && apiKeyDraft.trim().isEmpty()) {
            missing.add(translator.t("setup.field.api_key").getString());
        }
        if (modelIdDraft.trim().isEmpty()) {
            missing.add(translator.t("setup.field.model_id").getString());
        }
        return String.join(", ", missing);
    }

    private int connectionStatusColor() {
        if (!connectionTested) {
            return style.colorWarning();
        }
        return connectionOk ? style.colorOk() : style.colorError();
    }

    private Component connectionStatusLabel() {
        if (!connectionTested) {
            return translator.t("setup.connection.unverified");
        }
        return translator.t(connectionOk ? "setup.connection.ok" : "setup.connection.failed");
    }

    private String apiKeyStateLabel() {
        if (typeDraft == ApiProviderType.OLLAMA) {
            return translator.t("setup.summary.api_key.local").getString();
        }
        return translator.t(apiKeyDraft.trim().isEmpty() ? "setup.summary.api_key.missing" : "setup.summary.api_key.filled").getString();
    }

    private int enabledModuleCount() {
        int count = 0;
        for (ModuleRow[] table : MODULE_ROW_TABLES) {
            for (ModuleRow row : table) {
                if (SetupWizardSupport.isModuleEnabled(config(), row.module())) {
                    count++;
                }
            }
        }
        return count;
    }

    private String enabledModulesLabel() {
        List<String> names = new ArrayList<>();
        for (ModuleRow[] table : MODULE_ROW_TABLES) {
            for (ModuleRow row : table) {
                if (SetupWizardSupport.isModuleEnabled(config(), row.module())) {
                    names.add(translator.t(row.labelKey()).getString());
                }
            }
        }
        return names.isEmpty() ? translator.t("setup.summary.modules.none").getString() : String.join(", ", names);
    }

    private String stepTitleKey() {
        return switch (step) {
            case 2 -> "setup.step.modules";
            case 3 -> "setup.step.summary";
            default -> "setup.step.provider";
        };
    }

    private void addContentRow(int x, int y, int width, Component text, boolean warning) {
        contentRows.add(new TextRow(x, y, width, text, warning, false));
    }

    private void addLiveContentRow(int x, int y, int width, Supplier<Component> text, IntSupplier color) {
        liveRows.add(new LiveRow(x, y, width, text, color));
    }

    private void addHeaderRow(int x, int y, int width, Component text, boolean rightAligned) {
        headerRows.add(new TextRow(x, y, width, text, false, rightAligned));
    }

    private void drawRow(GuiGraphicsExtractor context, Font font, TextRow row) {
        Component text = trim(font, row.text(), row.maxWidth());
        int x = row.rightAligned() ? row.x() - font.width(text) : row.x();
        context.text(font, text, x, row.y(), row.warning() ? style.colorWarning() : style.colorTextMuted(), false);
    }

    private void drawText(GuiGraphicsExtractor context, Font font, int x, int y, int maxWidth, int color, Component text) {
        Component trimmed = trim(font, text, maxWidth);
        context.text(font, trimmed, x, y, color, false);
    }

    private static Component trim(Font font, Component text, int maxWidth) {
        if (text == null) {
            return Component.empty();
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }

        String content = text.getString();
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return Component.literal(font.plainSubstrByWidth(content, maxWidth));
        }
        return Component.literal(font.plainSubstrByWidth(content, maxWidth - ellipsisWidth) + ellipsis);
    }

    private static ModConfig config() {
        return Translate_AllinOne.getConfig();
    }

    private record ModuleRow(SetupWizardSupport.Module module, String labelKey, String tooltipKey) {
    }

    private record HotkeyGroup(String titleKey, SetupWizardSupport.Module[] modules, HotkeyEntry[] entries) {
    }

    private record HotkeyEntry(HotkeyTarget target, String descKey, boolean modeRow) {
    }

    private record TextRow(int x, int y, int maxWidth, Component text, boolean warning, boolean rightAligned) {
    }

    private record LiveRow(int x, int y, int maxWidth, Supplier<Component> text, IntSupplier color) {
    }

    public interface LiveActionAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Component> labelSupplier,
                Runnable action,
                IntSupplier colorSupplier,
                IntSupplier hoverColorSupplier,
                int textColor,
                boolean centered,
                Supplier<Component> tooltipSupplier,
                BooleanSupplier enabled
        );
    }

    public interface HotkeySupport {
        Component modeLabel(HotkeyTarget target);

        Component bindingLabel(HotkeyTarget target);

        void cycleMode(HotkeyTarget target);

        void startCapture(HotkeyTarget target);
    }

    @FunctionalInterface
    public interface SecretFieldAdder {
        EditBox add(
                int x,
                int y,
                int width,
                int maxLength,
                String initialValue,
                Component placeholder,
                Consumer<String> changed,
                boolean masked
        );
    }

    public interface Runtime {
        void onRebuild();

        void onRequestFocus(EditBox field);

        void onProviderLanded(ApiProviderProfile profile);

        void onTestConnection(ApiProviderProfile probe, Consumer<Boolean> onResult);

        void onSetupCompleted();

        void onSetupSkipped();
    }

    public record Style(
            int colorBlock,
            int colorBlockMuted,
            int colorBlockHover,
            int colorBlockDanger,
            int colorBlockDangerHover,
            int colorAccent,
            int colorAccentHover,
            int colorText,
            int colorTextMuted,
            int colorTextAccent,
            int colorOk,
            int colorError,
            int colorWarning,
            int colorProgressTrack,
            int colorProgressFill
    ) {
    }
}
