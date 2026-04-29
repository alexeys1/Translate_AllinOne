package com.cedarxuesong.translate_allinone.gui;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.registration.ConfigManager;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.ActionBlockRegistry;
import com.cedarxuesong.translate_allinone.gui.configui.controls.CheckboxBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.GroupBox;
import com.cedarxuesong.translate_allinone.gui.configui.controls.IntSliderBlock;
import com.cedarxuesong.translate_allinone.gui.configui.controls.StaticTextRow;
import com.cedarxuesong.translate_allinone.gui.configui.interaction.ConfigUiInteractionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.interaction.ConfigUiModalInteractionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.model.ConfigSection;
import com.cedarxuesong.translate_allinone.gui.configui.model.FocusTarget;
import com.cedarxuesong.translate_allinone.gui.configui.model.RouteSlot;
import com.cedarxuesong.translate_allinone.gui.configui.model.UiRect;
import com.cedarxuesong.translate_allinone.gui.configui.modals.AddProviderModalSupport;
import com.cedarxuesong.translate_allinone.gui.configui.modals.CustomParametersModalSupport;
import com.cedarxuesong.translate_allinone.gui.configui.modals.ModelSettingsModalSupport;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiControlRenderer;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.cedarxuesong.translate_allinone.gui.configui.render.ConfigUiScreenRenderSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.ConfigChromeActionsSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.ConfigSectionContentSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.ProviderDetailSectionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.ProviderListSectionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.ProviderManagerSectionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.RouteModelSectionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.sections.RouteModelSelectorSectionSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ConfigUiFocusSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ConfigUiTextFieldSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.CustomParameterTreeSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ModelSettingsApplySupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ModelSettingsDraftSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ModelSettingsMutationSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ProviderManagerMutationSupport;
import com.cedarxuesong.translate_allinone.gui.configui.support.ProviderProfileSupport;
import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ApiProviderType;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CustomParameterEntry;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DictionaryConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.DictionaryFileSelectionSupport;
import com.cedarxuesong.translate_allinone.utils.update.UpdateCheckManager;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ModConfigScreen extends Screen {
    private static final String I18N_PREFIX = "text.translate_allinone.configscreen.";
    private static final Gson CONFIG_STATE_GSON = new Gson();
    private static final String DEBUG_SECTION_ACCOUNT_UUID = "ef7acee7-f759-4d1e-a1ba-5a8dc7656d01";
    private static final String SCREEN_STATE_FILE_NAME = "config_screen_state.json";

    private static final int COLOR_BG = 0xFF0C0C0C;
    private static final int COLOR_TOP_BAR = 0xFF151515;
    private static final int COLOR_LEFT_PANEL = 0xFF131313;
    private static final int COLOR_MAIN_PANEL = 0xFF101010;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_BLOCK = 0xFF1A1A1A;
    private static final int COLOR_BLOCK_HOVER = 0xFF242424;
    private static final int COLOR_BLOCK_MUTED = 0xFF141414;
    private static final int COLOR_BLOCK_SELECTED = 0xFF214936;
    private static final int COLOR_BLOCK_SELECTED_HOVER = 0xFF295B43;
    private static final int COLOR_BLOCK_ACCENT = 0xFF2B6A4C;
    private static final int COLOR_BLOCK_ACCENT_HOVER = 0xFF337B59;
    private static final int COLOR_BLOCK_DANGER = 0xFF3C1C1C;
    private static final int COLOR_BLOCK_DANGER_HOVER = 0xFF4B2525;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_MUTED = 0xFF9A9A9A;
    private static final int COLOR_TEXT_ACCENT = 0xFF9CF0C0;
    private static final int COLOR_TEXT_LINK = 0xFF78DFA6;
    private static final int COLOR_TEXT_LINK_HOVER = 0xFF9CF0C0;
    private static final int COLOR_STATUS_OK = 0xFF59D185;
    private static final int COLOR_STATUS_ERROR = 0xFFFF6A6A;
    private static final int COLOR_MODAL_OVERLAY = 0x98000000;
    private static final int COLOR_SLIDER_TRACK = 0xFF0E0E0E;
    private static final int COLOR_SLIDER_FILL = 0xFF276749;
    private static final int COLOR_SLIDER_FILL_ACTIVE = 0xFF359362;
    private static final int COLOR_SLIDER_KNOB = 0xFFE3E3E3;
    private static final int COLOR_SLIDER_KNOB_ACTIVE = 0xFFA6F4C7;
    private static final int COLOR_CHECKBOX_OFF = 0xFF0F0F0F;
    private static final int COLOR_CHECKBOX_ON = 0xFF2B6A4C;
    private static final int COLOR_CHECKBOX_BORDER_ON = 0xFF9CF0C0;
    private static final int COLOR_GROUP_BG = 0x66141414;
    private static final int COLOR_GROUP_BORDER = 0xFF2C2C2C;
    private static final int COLOR_GROUP_TITLE = 0xFFB5B5B5;
    private static final int COLOR_SCROLL_TRACK = 0xAA171717;
    private static final int COLOR_SCROLL_THUMB = 0xFF3A3A3A;
    private static final int COLOR_SCROLL_THUMB_HOVER = 0xFF4A4A4A;
    private static final int SLIDER_BLOCK_HEIGHT = 26;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 26;
    private static final int SCROLL_STEP = 28;
    private static final double SCROLL_ANIMATION_SPEED = 15.0;
    private static final double OVER_SCROLL_RESISTANCE = 0.42;
    private static final double OVER_SCROLL_MAX = 56.0;
    private static final double OVER_SCROLL_SPRING = 58.0;
    private static final double OVER_SCROLL_DAMPING = 12.0;
    private static final double OVER_SCROLL_WHEEL_OFFSET_FACTOR = 0.2;
    private static final double OVER_SCROLL_WHEEL_VELOCITY_FACTOR = 9.0;
    private static final int CONTENT_CLIP_SIDE_PADDING = 6;
    private static final int CONTENT_CLIP_TOP_PADDING = 6;
    private static final int CONTENT_CLIP_BOTTOM_PADDING = 8;
    private static final int CONTENT_SCROLLBAR_GAP = CONTENT_CLIP_SIDE_PADDING;
    private static final IntSliderBlock.Style SLIDER_STYLE = new IntSliderBlock.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BORDER,
            COLOR_TEXT,
            COLOR_TEXT_ACCENT,
            COLOR_TEXT_MUTED,
            COLOR_BLOCK_ACCENT,
            COLOR_SLIDER_TRACK,
            COLOR_SLIDER_FILL,
            COLOR_SLIDER_FILL_ACTIVE,
            COLOR_SLIDER_KNOB,
            COLOR_SLIDER_KNOB_ACTIVE
    );
    private static final CheckboxBlock.Style CHECKBOX_STYLE = new CheckboxBlock.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_BORDER,
            COLOR_TEXT,
            COLOR_TEXT_ACCENT,
            COLOR_CHECKBOX_OFF,
            COLOR_CHECKBOX_ON,
            COLOR_BORDER,
            COLOR_CHECKBOX_BORDER_ON
    );
    private static final GroupBox.Style GROUP_BOX_STYLE = new GroupBox.Style(
            COLOR_GROUP_BG,
            COLOR_GROUP_BORDER,
            COLOR_GROUP_TITLE,
            COLOR_MAIN_PANEL
    );
    private static final ProviderListSectionSupport.Style PROVIDER_LIST_STYLE = new ProviderListSectionSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_TEXT_ACCENT,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER
    );
    private static final ProviderDetailSectionSupport.Style PROVIDER_DETAIL_STYLE = new ProviderDetailSectionSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_TEXT_ACCENT,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER
    );
    private static final AddProviderModalSupport.Style ADD_PROVIDER_MODAL_STYLE = new AddProviderModalSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_TEXT,
            COLOR_TEXT_ACCENT
    );
    private static final ModelSettingsModalSupport.Style MODEL_SETTINGS_MODAL_STYLE = new ModelSettingsModalSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_TEXT
    );
    private static final CustomParametersModalSupport.Style CUSTOM_PARAMETERS_MODAL_STYLE = new CustomParametersModalSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_TEXT_ACCENT,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER
    );
    private static final RouteModelSelectorSectionSupport.Style ROUTE_MODEL_SELECTOR_STYLE = new RouteModelSelectorSectionSupport.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_TEXT,
            COLOR_TEXT_ACCENT
    );
    private static final ConfigUiScreenRenderSupport.Style SCREEN_RENDER_STYLE = new ConfigUiScreenRenderSupport.Style(
            COLOR_BG,
            COLOR_TOP_BAR,
            COLOR_LEFT_PANEL,
            COLOR_MAIN_PANEL,
            COLOR_BORDER,
            COLOR_TEXT,
            COLOR_MODAL_OVERLAY
    );
    private static final ConfigChromeActionsSupport.Style CHROME_ACTIONS_STYLE = new ConfigChromeActionsSupport.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_TEXT,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER
    );

    private static final int TOP_BAR_HEIGHT = 40;
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int TOP_BAR_TITLE_X = 14;
    private static final int TOP_BAR_TEXT_Y = 14;
    private static final int TOP_BAR_STATUS_X = LEFT_PANEL_WIDTH + 20;
    private static final int TOP_BAR_ACTIONS_LEFT_MARGIN = 270;
    private static final int TOP_BAR_LINK_GAP = 16;

    private final Screen parent;
    private final ModConfig originalConfigSnapshot;
    private final String originalConfigSnapshotJson;
    private final String modVersion;
    private final String repositoryUrl;
    private final List<ActionBlock> actionBlocks = new ArrayList<>();
    private final List<ActionBlock> contentActionBlocks = new ArrayList<>();
    private final List<ActionBlock> floatingActionBlocks = new ArrayList<>();
    private final List<GroupBox> groupBoxes = new ArrayList<>();
    private final List<StaticTextRow> staticTextRows = new ArrayList<>();
    private final List<CheckboxBlock> checkboxBlocks = new ArrayList<>();
    private final List<CheckboxBlock> floatingCheckboxBlocks = new ArrayList<>();
    private final ActionBlockRegistry actionBlockRegistry = new ActionBlockRegistry(actionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry contentActionBlockRegistry = new ActionBlockRegistry(contentActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry floatingActionBlockRegistry = new ActionBlockRegistry(floatingActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final List<IntSliderBlock> sliderBlocks = new ArrayList<>();
    private final List<TextFieldWidget> providerEditorFields = new ArrayList<>();
    private final List<TextFieldWidget> floatingEditorFields = new ArrayList<>();
    private IntSliderBlock draggingSlider;
    private long sliderAnimationLastNanos = System.nanoTime();

    private ConfigSection selectedSection;
    private int selectedProviderIndex;
    private String selectedProviderId = "";
    private boolean providerApiKeyVisible = true;
    private String providerSearchQuery = "";
    private RouteSlot routeDropdownSlot;

    private TextFieldWidget providerSearchField;
    private TextFieldWidget addProviderNameField;
    private TextFieldWidget modelSettingsField;

    private boolean addProviderModalOpen;
    private String addProviderNameDraft = "";
    private ApiProviderType addProviderTypeDraft = ApiProviderType.OPENAI_COMPAT;
    private boolean addProviderTypeDropdownOpen;

    private boolean modelSettingsModalOpen;
    private String modelSettingsProviderId = "";
    private String modelSettingsOriginalId = "";
    private String modelSettingsDraft = "";
    private String modelSettingsTemperatureDraft = "";
    private String modelSettingsKeepAliveDraft = "";
    private boolean modelSettingsSupportsSystemDraft;
    private boolean modelSettingsInjectPromptIntoUserDraft = true;
    private boolean modelSettingsStructuredOutputDraft;
    private String modelSettingsSystemPromptSuffixDraft = "";
    private List<CustomParameterEntry> modelSettingsCustomParametersDraft = new ArrayList<>();
    private List<CustomParameterEntry> customParametersBackup = new ArrayList<>();
    private boolean customParametersModalOpen;
    private boolean dictionaryFilesModalOpen;
    private boolean resetConfirmModalOpen;
    private boolean updateNoticeModalOpen;
    private boolean unsavedChangesConfirmModalOpen;
    private boolean updateNoticeAutoPrompted;
    private String selectedCustomParameterPath = "";
    private TextFieldWidget customParameterNameField;
    private TextFieldWidget customParameterValueField;
    private boolean modelSettingsSetDefault;
    private DictionaryFileSelectionSupport.Slot dictionaryFilesModalSlot = DictionaryFileSelectionSupport.Slot.WYNNCRAFT_DIALOGUE;
    private List<String> dictionaryFilesSelectionBackup = new ArrayList<>();

    private FocusTarget pendingFocusTarget = FocusTarget.NONE;
    private ConfigSectionContentSupport.HotkeyTarget hotkeyCaptureTarget;
    private UiRect contentViewport = new UiRect(0, 0, 0, 0);
    private UiRect versionLinkRect = new UiRect(0, 0, 0, 0);
    private final int[] sectionScrollOffsets = new int[ConfigSection.values().length];
    private int contentScrollOffset;
    private double contentVisualOffset;
    private int contentScrollMaxOffset;
    private double contentElasticOffset;
    private double contentElasticVelocity;
    private boolean draggingContentScrollbar;
    private boolean draggingContentByMouse;
    private int contentDragStartOffset;
    private double contentDragStartMouseY;

    private Text statusMessage = Text.empty();
    private int statusColor = COLOR_STATUS_OK;
    private long statusExpireAtMillis;
    private boolean restoreSnapshotOnClose = true;

    public ModConfigScreen(Screen parent) {
        this(parent, loadPersistedSelectedSection());
    }

    private ModConfigScreen(Screen parent, ConfigSection selectedSection) {
        super(t("title"));
        this.parent = parent;
        this.originalConfigSnapshot = ConfigManager.copyCurrentConfig();
        this.originalConfigSnapshotJson = CONFIG_STATE_GSON.toJson(this.originalConfigSnapshot);
        this.providerApiKeyVisible = isProviderApiKeyVisible(this.originalConfigSnapshot.providerManager);
        this.modVersion = resolveCurrentVersion();
        this.repositoryUrl = resolveRepositoryUrl();
        this.selectedSection = selectedSection;
    }

    private static Text t(String key, Object... args) {
        return Text.translatable(I18N_PREFIX + key, args);
    }

    private static Path resolveScreenStatePath() {
        return ConfigManager.getConfigPath().resolveSibling(SCREEN_STATE_FILE_NAME);
    }

    private static ConfigSection loadPersistedSelectedSection() {
        try {
            Path statePath = resolveScreenStatePath();
            if (!Files.exists(statePath)) {
                return ConfigSection.CHAT_OUTPUT;
            }

            PersistedScreenState state = CONFIG_STATE_GSON.fromJson(Files.readString(statePath), PersistedScreenState.class);
            if (state == null) {
                return ConfigSection.CHAT_OUTPUT;
            }
            return resolveSectionByKey(state.selectedSectionKey());
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to load config screen state", e);
            return ConfigSection.CHAT_OUTPUT;
        }
    }

    private static ConfigSection resolveSectionByKey(String key) {
        if (key == null || key.isBlank()) {
            return ConfigSection.CHAT_OUTPUT;
        }

        for (ConfigSection section : ConfigSection.values()) {
            if (section.key().equalsIgnoreCase(key)) {
                return section;
            }
        }
        return ConfigSection.CHAT_OUTPUT;
    }

    private void persistScreenState() {
        try {
            Path statePath = resolveScreenStatePath();
            Path parent = statePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ConfigSection section = selectedSection == null ? ConfigSection.CHAT_OUTPUT : selectedSection;
            Files.writeString(statePath, CONFIG_STATE_GSON.toJson(new PersistedScreenState(section.key())));
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to persist config screen state", e);
        }
    }

    private static String resolveCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(Translate_AllinOne.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String resolveRepositoryUrl() {
        return FabricLoader.getInstance()
                .getModContainer(Translate_AllinOne.MOD_ID)
                .map(container -> {
                    var contact = container.getMetadata().getContact();
                    return contact.get("sources")
                            .or(() -> contact.get("homepage"))
                            .orElse("");
                })
                .orElse("");
    }

    private Text versionLinkText() {
        return t("version_link", modVersion);
    }

    private boolean isStatusVisible(long nowMillis) {
        return !statusMessage.getString().isEmpty() && nowMillis <= statusExpireAtMillis;
    }

    private UiRect resolveVersionLinkRect(long nowMillis) {
        if (repositoryUrl.isBlank()) {
            return new UiRect(0, 0, 0, 0);
        }

        Text versionText = versionLinkText();
        int textWidth = this.textRenderer.getWidth(versionText);
        if (textWidth <= 0) {
            return new UiRect(0, 0, 0, 0);
        }

        int minX = TOP_BAR_TITLE_X + this.textRenderer.getWidth(this.title) + TOP_BAR_LINK_GAP;
        if (isStatusVisible(nowMillis)) {
            minX = Math.max(minX, TOP_BAR_STATUS_X + this.textRenderer.getWidth(statusMessage) + TOP_BAR_LINK_GAP);
        }

        int x = this.width - TOP_BAR_ACTIONS_LEFT_MARGIN - TOP_BAR_LINK_GAP - textWidth;
        if (x < minX) {
            return new UiRect(0, 0, 0, 0);
        }
        return new UiRect(x, TOP_BAR_TEXT_Y, textWidth, this.textRenderer.fontHeight + 2);
    }

    private void updateVersionLinkRect(long nowMillis) {
        versionLinkRect = resolveVersionLinkRect(nowMillis);
    }

    private void renderVersionLink(DrawContext context, int mouseX, int mouseY, long nowMillis) {
        updateVersionLinkRect(nowMillis);
        if (versionLinkRect.width <= 0 || versionLinkRect.height <= 0) {
            return;
        }

        boolean hovered = versionLinkRect.contains(mouseX, mouseY);
        int color = hovered ? COLOR_TEXT_LINK_HOVER : COLOR_TEXT_LINK;
        Text versionText = versionLinkText();
        context.drawText(this.textRenderer, versionText, versionLinkRect.x, versionLinkRect.y, color, false);

        int underlineY = versionLinkRect.y + this.textRenderer.fontHeight + 1;
        context.fill(versionLinkRect.x, underlineY, versionLinkRect.right(), underlineY + 1, color);
    }

    private boolean hasUnsavedChanges() {
        return !originalConfigSnapshotJson.equals(CONFIG_STATE_GSON.toJson(ConfigManager.copyCurrentConfig()));
    }

    private boolean isAnyModalOpen() {
        return ConfigUiModalSupport.isAnyModalOpen(
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen
        );
    }

    private boolean isInsideOpenModal(double mouseX, double mouseY) {
        return ConfigUiModalSupport.isInsideOpenModal(
                mouseX,
                mouseY,
                this.width,
                this.height,
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen
        );
    }

    private void finishClose() {
        persistScreenState();
        if (restoreSnapshotOnClose) {
            ConfigManager.replaceConfig(originalConfigSnapshot);
        }
        restoreSnapshotOnClose = false;
        unsavedChangesConfirmModalOpen = false;

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    protected void init() {
        if (!updateNoticeAutoPrompted && UpdateCheckManager.shouldShowConfigNotice()) {
            openUpdateNoticeModal();
        }
        rebuildActionBlocks();
    }

    @Override
    public void tick() {
        super.tick();
        if (!updateNoticeAutoPrompted && UpdateCheckManager.shouldShowConfigNotice()) {
            openUpdateNoticeModal();
            rebuildActionBlocks();
        }
    }

    private void rebuildActionBlocks() {
        rebuildActionBlocks(FocusTarget.NONE);
    }

    private void rebuildActionBlocks(FocusTarget focusTarget) {
        ensureSelectedSectionVisible();
        clearChildren();
        actionBlocks.clear();
        contentActionBlocks.clear();
        floatingActionBlocks.clear();
        groupBoxes.clear();
        staticTextRows.clear();
        checkboxBlocks.clear();
        floatingCheckboxBlocks.clear();
        sliderBlocks.clear();
        draggingSlider = null;
        sliderAnimationLastNanos = System.nanoTime();
        providerEditorFields.clear();
        floatingEditorFields.clear();
        providerSearchField = null;
        addProviderNameField = null;
        modelSettingsField = null;
        customParameterNameField = null;
        customParameterValueField = null;
        pendingFocusTarget = focusTarget;
        contentViewport = computeContentViewport();
        contentScrollOffset = Math.max(0, sectionScrollOffsets[selectedSection.ordinal()]);
        if (!Double.isFinite(contentVisualOffset)) {
            contentVisualOffset = contentScrollOffset;
        }

        ConfigChromeActionsSupport.renderTopBarActions(
                this.width,
                actionBlockRegistry::add,
                ModConfigScreen::t,
                this::openResetConfirmation,
                this::cancelAndClose,
                this::saveAndClose,
                CHROME_ACTIONS_STYLE
        );

        ConfigChromeActionsSupport.renderSectionActions(
                selectedSection,
                visibleSections(),
                TOP_BAR_HEIGHT,
                LEFT_PANEL_WIDTH,
                actionBlockRegistry::add,
                ModConfigScreen::t,
                section -> {
                    if (selectedSection != section) {
                        sectionScrollOffsets[selectedSection.ordinal()] = contentScrollOffset;
                        hotkeyCaptureTarget = null;
                        selectedSection = section;
                        contentScrollOffset = Math.max(0, sectionScrollOffsets[selectedSection.ordinal()]);
                        contentVisualOffset = contentScrollOffset;
                        contentElasticOffset = 0.0;
                        contentElasticVelocity = 0.0;
                        addProviderTypeDropdownOpen = false;
                        routeDropdownSlot = null;
                        rebuildActionBlocks();
                    }
                },
                CHROME_ACTIONS_STYLE
        );

        addSectionSpecificActions();
        if (dictionaryFilesModalOpen) {
            addDictionaryFilesModal();
        } else if (updateNoticeModalOpen) {
            addUpdateNoticeModal();
        } else if (resetConfirmModalOpen) {
            addResetConfirmModal();
        } else if (unsavedChangesConfirmModalOpen) {
            addUnsavedChangesConfirmModal();
        }
        ConfigUiFocusSupport.applyPendingFocus(
                this,
                pendingFocusTarget,
                providerSearchField,
                addProviderNameField,
                modelSettingsField,
                customParameterNameField
        );
        pendingFocusTarget = FocusTarget.NONE;
    }

    private void ensureSelectedSectionVisible() {
        if (isSectionVisible(selectedSection)) {
            return;
        }

        selectedSection = firstVisibleSection();
        hotkeyCaptureTarget = null;
        addProviderTypeDropdownOpen = false;
        routeDropdownSlot = null;
    }

    private List<ConfigSection> visibleSections() {
        List<ConfigSection> sections = new ArrayList<>();
        for (ConfigSection section : ConfigSection.values()) {
            if (isSectionVisible(section)) {
                sections.add(section);
            }
        }
        return sections;
    }

    private ConfigSection firstVisibleSection() {
        for (ConfigSection section : ConfigSection.values()) {
            if (isSectionVisible(section)) {
                return section;
            }
        }
        return ConfigSection.CHAT_OUTPUT;
    }

    private boolean isSectionVisible(ConfigSection section) {
        return section != ConfigSection.DEBUG || shouldShowDebugSection();
    }

    private boolean shouldShowDebugSection() {
        return this.client != null
                && this.client.player != null
                && DEBUG_SECTION_ACCOUNT_UUID.equalsIgnoreCase(this.client.player.getUuid().toString());
    }

    private void addSectionSpecificActions() {
        ModConfig config = Translate_AllinOne.getConfig();
        config.providerManager.ensureDefaults();
        providerApiKeyVisible = isProviderApiKeyVisible(config.providerManager);

        int x = LEFT_PANEL_WIDTH + 20;
        int y = TOP_BAR_HEIGHT + 28;
        int width = contentLayoutWidth();
        int contentStartY = y - currentRenderScrollOffset();

        int contentBottomY = ConfigSectionContentSupport.render(
                selectedSection,
                config,
                x,
                contentStartY,
                width,
                contentViewport.height,
                ModConfigScreen::t,
                this::addGroupBox,
                this::addToggleAction,
                this::addIntSliderAction,
                this::addActionRow,
                this::addTextInputRow,
                this::hotkeyBindingLabel,
                this::startHotkeyCapture,
                this::clearHotkeyBinding,
                this::cycleHotkeyMode,
                this::openDictionaryFilesModal,
                this::openDictionaryDirectory,
                this::openCacheDirectory,
                this::openWynnDialogueHudEditor,
                this::addRouteModelSelector,
                this::addProviderManagerActions
        );

        updateScrollBounds(contentBottomY);
    }

    private UiRect computeContentViewport() {
        int x = LEFT_PANEL_WIDTH + 20;
        int y = TOP_BAR_HEIGHT + 28;
        int width = Math.max(0, this.width - x - 16);
        int height = Math.max(0, this.height - y - 12);
        return new UiRect(x, y, width, height);
    }

    private int contentLayoutWidth() {
        return Math.max(0, contentViewport.width - SCROLLBAR_WIDTH - CONTENT_SCROLLBAR_GAP);
    }

    private int contentLayoutRight() {
        return contentViewport.x + contentLayoutWidth();
    }

    private UiRect contentClipViewport() {
        if (contentViewport.width <= 0 || contentViewport.height <= 0) {
            return contentViewport;
        }

        int clipX = Math.max(LEFT_PANEL_WIDTH, contentViewport.x - CONTENT_CLIP_SIDE_PADDING);
        int clipY = Math.max(TOP_BAR_HEIGHT, contentViewport.y - CONTENT_CLIP_TOP_PADDING);
        int clipRight = Math.min(this.width, contentLayoutRight() + CONTENT_CLIP_SIDE_PADDING);
        int clipBottom = Math.min(this.height, contentViewport.bottom() + CONTENT_CLIP_BOTTOM_PADDING);
        return new UiRect(
                clipX,
                clipY,
                Math.max(0, clipRight - clipX),
                Math.max(0, clipBottom - clipY)
        );
    }

    private int currentRenderScrollOffset() {
        return (int) Math.round(contentVisualOffset - contentElasticOffset);
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateScrollBounds(int contentBottomY) {
        double naturalBottomY = contentBottomY + currentRenderScrollOffset();
        int viewportBottom = contentViewport.bottom();
        contentScrollMaxOffset = Math.max(0, (int) Math.ceil(naturalBottomY - viewportBottom));
        int clamped = clampScrollOffset(contentScrollOffset);
        if (clamped != contentScrollOffset) {
            contentScrollOffset = clamped;
            sectionScrollOffsets[selectedSection.ordinal()] = clamped;
        }
    }

    private int clampScrollOffset(int value) {
        return Math.max(0, Math.min(contentScrollMaxOffset, value));
    }

    private boolean tryScrollBy(int delta) {
        if (delta == 0) {
            return false;
        }

        int rawNext = contentScrollOffset + delta;
        int next = clampScrollOffset(rawNext);
        if (next == contentScrollOffset && rawNext == next) {
            return false;
        }

        if (rawNext != next) {
            applyWheelOverscroll(delta);
        }

        applyScrollOffset(next, false);
        return true;
    }

    private void applyScrollOffset(int nextOffset, boolean immediateVisual) {
        contentScrollOffset = nextOffset;
        sectionScrollOffsets[selectedSection.ordinal()] = nextOffset;
        if (immediateVisual) {
            contentVisualOffset = nextOffset;
            if (!draggingContentByMouse) {
                contentElasticOffset = 0.0;
                contentElasticVelocity = 0.0;
            }
        }
        draggingSlider = null;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        rebuildActionBlocks();
    }

    private void applyWheelOverscroll(int delta) {
        double nextElastic = contentElasticOffset + (-delta) * OVER_SCROLL_WHEEL_OFFSET_FACTOR;
        contentElasticOffset = clampDouble(nextElastic, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);
        contentElasticVelocity += (-delta) * OVER_SCROLL_WHEEL_VELOCITY_FACTOR;
        contentElasticVelocity = clampDouble(contentElasticVelocity, -900.0, 900.0);
    }

    private UiRect scrollbarTrackRect() {
        if (contentViewport.width <= 0 || contentViewport.height <= 0 || contentScrollMaxOffset <= 0) {
            return null;
        }
        return new UiRect(
                contentViewport.right() - SCROLLBAR_WIDTH,
                contentViewport.y,
                SCROLLBAR_WIDTH,
                contentViewport.height
        );
    }

    private UiRect scrollbarThumbRect() {
        UiRect track = scrollbarTrackRect();
        if (track == null) {
            return null;
        }

        int thumbHeight = Math.max(
                SCROLLBAR_MIN_THUMB_HEIGHT,
                (int) Math.round(track.height * (track.height / (double) (track.height + contentScrollMaxOffset)))
        );
        thumbHeight = Math.min(track.height, thumbHeight);
        int travel = Math.max(0, track.height - thumbHeight);
        double thumbOffset = clampDouble(contentVisualOffset, 0.0, contentScrollMaxOffset);
        int thumbY = travel == 0
                ? track.y
                : track.y + (int) Math.round((thumbOffset / Math.max(1.0, contentScrollMaxOffset)) * travel);
        return new UiRect(track.x, thumbY, track.width, thumbHeight);
    }

    private int scrollOffsetFromThumbMouseY(double mouseY) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null) {
            return contentScrollOffset;
        }

        int travel = Math.max(1, track.height - thumb.height);
        double anchorY = mouseY - thumb.height / 2.0;
        double ratio = (anchorY - track.y) / travel;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return clampScrollOffset((int) Math.round(ratio * contentScrollMaxOffset));
    }

    private void startScrollbarDrag() {
        draggingContentScrollbar = true;
        draggingContentByMouse = false;
        contentElasticOffset = 0.0;
        contentElasticVelocity = 0.0;
    }

    private void stopScrollingDrag() {
        draggingContentScrollbar = false;
        draggingContentByMouse = false;
    }

    private void drawContentScrollbar(DrawContext context, int mouseX, int mouseY) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null) {
            return;
        }

        context.fill(track.x, track.y, track.right(), track.bottom(), COLOR_SCROLL_TRACK);
        boolean hovered = thumb.contains(mouseX, mouseY);
        int thumbColor = draggingContentScrollbar || hovered ? COLOR_SCROLL_THUMB_HOVER : COLOR_SCROLL_THUMB;
        context.fill(thumb.x, thumb.y, thumb.right(), thumb.bottom(), thumbColor);
        ConfigUiDraw.drawOutline(context, track.x, track.y, track.width, track.height, COLOR_BORDER);
    }

    private Text providerTypeDisplayName(ApiProviderType providerType) {
        return t("provider_type." + providerType.name().toLowerCase(Locale.ROOT));
    }

    private static boolean isProviderApiKeyVisible(ProviderManagerConfig providerManager) {
        return providerManager == null || providerManager.api_key_visible == null || providerManager.api_key_visible;
    }

    private void setProviderApiKeyVisible(boolean visible) {
        providerApiKeyVisible = visible;
        ModConfig config = Translate_AllinOne.getConfig();
        if (config.providerManager == null) {
            config.providerManager = new ProviderManagerConfig();
        }
        config.providerManager.api_key_visible = visible;
    }

    private void addRouteModelSelector(ProviderManagerConfig manager, RouteSlot routeSlot, int x, int y, int width) {
        RouteModelSelectorSectionSupport.render(
                manager,
                routeSlot,
                x,
                y,
                width,
                contentViewport.y,
                contentViewport.bottom(),
                routeDropdownSlot == routeSlot,
                ModConfigScreen::t,
                contentActionBlockRegistry::add,
                floatingActionBlockRegistry::add,
                () -> {
                    routeDropdownSlot = routeDropdownSlot == routeSlot ? null : routeSlot;
                    rebuildActionBlocks();
                },
                (routeKey, displayLabel) -> {
                    RouteModelSectionSupport.setRouteKey(manager, routeSlot, routeKey);
                    routeDropdownSlot = null;
                    setStatus(t("status.assigned_route", t(routeSlot.translationKey()), displayLabel), COLOR_STATUS_OK);
                    rebuildActionBlocks();
                },
                ROUTE_MODEL_SELECTOR_STYLE
        );
    }

    private int addProviderManagerActions(ProviderManagerConfig providerManager, int x, int y, int width, int viewportHeight) {
        ProviderManagerSectionSupport.RenderResult result = ProviderManagerSectionSupport.render(
                providerManager,
                viewportHeight,
                x,
                y,
                width,
                providerSearchQuery,
                selectedProviderId,
                selectedProviderIndex,
                providerApiKeyVisible,
                PROVIDER_LIST_STYLE,
                PROVIDER_DETAIL_STYLE,
                ModConfigScreen::t,
                this::addGroupBox,
                this::providerTypeDisplayName,
                contentActionBlockRegistry::add,
                this::addTextField,
                value -> {
                    String next = ProviderProfileSupport.sanitizeText(value);
                    if (!providerSearchQuery.equals(next)) {
                        providerSearchQuery = next;
                        rebuildActionBlocks(FocusTarget.PROVIDER_SEARCH);
                    }
                },
                providerId -> {
                    selectedProviderId = providerId;
                    modelSettingsModalOpen = false;
                    rebuildActionBlocks();
                },
                () -> {
                    openAddProviderModal();
                    rebuildActionBlocks(FocusTarget.ADD_PROVIDER_NAME);
                },
                contentActionBlockRegistry::add,
                this::addTextField,
                targetProfile -> {
                    targetProfile.enabled = !targetProfile.enabled;
                    rebuildActionBlocks();
                },
                targetProfile -> deleteProvider(providerManager, targetProfile),
                () -> {
                    setProviderApiKeyVisible(!providerApiKeyVisible);
                    rebuildActionBlocks();
                },
                profile -> ConfigUiRuntimeSupport.testProviderConnection(
                        profile,
                        ModConfigScreen::t,
                        this::setStatus,
                        COLOR_STATUS_OK,
                        COLOR_STATUS_ERROR,
                        runnable -> {
                            if (this.client != null) {
                                this.client.execute(runnable);
                            } else {
                                runnable.run();
                            }
                        }
                ),
                (targetProfile, modelId) -> {
                    targetProfile.model_id = modelId;
                    targetProfile.ensureModelSettings();
                    setStatus(t("status.model_set_default", modelId), COLOR_STATUS_OK);
                    rebuildActionBlocks();
                },
                (targetProfile, modelId) -> {
                    openModelSettingsModal(targetProfile, modelId);
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
                this::removeModel,
                targetProfile -> {
                    openModelSettingsModal(targetProfile, "");
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                }
        );
        providerSearchField = result.providerSearchField();
        selectedProviderId = result.selectedProviderId();

        if (addProviderModalOpen) {
            addAddProviderModal(providerManager);
        }
        if (modelSettingsModalOpen && !customParametersModalOpen) {
            addModelSettingsModal(providerManager);
        }
        if (customParametersModalOpen) {
            addCustomParametersModal();
        }

        return result.contentBottomY();
    }

    private TextFieldWidget addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
            Consumer<String> changed,
            boolean editable
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, value -> true, editable, false);
    }

    private TextFieldWidget addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, textPredicate, editable, false);
    }

    private TextFieldWidget addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
            Consumer<String> changed,
            boolean editable,
            boolean floating
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, value -> true, editable, floating);
    }

    private TextFieldWidget addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Text placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable,
            boolean floating
    ) {
        return ConfigUiTextFieldSupport.create(
                this.textRenderer,
                this::addDrawableChild,
                providerEditorFields,
                floatingEditorFields,
                x,
                y,
                width,
                maxLength,
                initialValue,
                placeholder,
                changed,
                textPredicate,
                editable,
                floating,
                isAnyModalOpen()
        );
    }

    private void addAddProviderModal(ProviderManagerConfig providerManager) {
        addProviderNameField = AddProviderModalSupport.render(
                this.width,
                this.height,
                addProviderNameDraft,
                addProviderTypeDraft,
                addProviderTypeDropdownOpen,
                ModConfigScreen::t,
                this::providerTypeDisplayName,
                floatingActionBlockRegistry::add,
                (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                        addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
                value -> addProviderNameDraft = ProviderProfileSupport.sanitizeText(value),
                () -> {
                    addProviderTypeDropdownOpen = !addProviderTypeDropdownOpen;
                    rebuildActionBlocks(FocusTarget.ADD_PROVIDER_NAME);
                },
                type -> {
                    addProviderTypeDraft = type;
                    addProviderTypeDropdownOpen = false;
                    rebuildActionBlocks(FocusTarget.ADD_PROVIDER_NAME);
                },
                () -> {
                    closeAddProviderModal();
                    rebuildActionBlocks();
                },
                () -> addProviderFromModal(providerManager),
                ADD_PROVIDER_MODAL_STYLE
        );
    }

    private void addProviderFromModal(ProviderManagerConfig providerManager) {
        ProviderManagerMutationSupport.AddProviderResult result = ProviderManagerMutationSupport.addProviderFromDraft(
                providerManager,
                addProviderNameDraft,
                addProviderTypeDraft
        );
        ApiProviderProfile profile = result.profile();
        selectedProviderId = result.selectedProviderId();
        selectedProviderIndex = result.selectedProviderIndex();

        closeAddProviderModal();
        setStatus(t("status.added_provider", profile.name), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void openAddProviderModal() {
        addProviderModalOpen = true;
        addProviderTypeDropdownOpen = false;
        addProviderTypeDraft = ApiProviderType.OPENAI_COMPAT;
        addProviderNameDraft = "";
    }

    private void closeAddProviderModal() {
        addProviderModalOpen = false;
        addProviderTypeDropdownOpen = false;
        addProviderNameDraft = "";
    }

    private void openModelSettingsModal(ApiProviderProfile profile, String originalModelId) {
        modelSettingsModalOpen = true;
        ModelSettingsDraftSupport.Draft draft = ModelSettingsDraftSupport.fromProfile(profile, originalModelId);
        modelSettingsProviderId = draft.providerId();
        modelSettingsOriginalId = draft.originalModelId();
        modelSettingsDraft = draft.modelIdDraft();
        modelSettingsTemperatureDraft = draft.temperatureDraft();
        modelSettingsKeepAliveDraft = draft.keepAliveDraft();
        modelSettingsSupportsSystemDraft = draft.supportsSystem();
        modelSettingsInjectPromptIntoUserDraft = draft.injectPromptIntoUser();
        modelSettingsStructuredOutputDraft = draft.structuredOutput();
        modelSettingsSystemPromptSuffixDraft = draft.systemPromptSuffixDraft();
        modelSettingsCustomParametersDraft = draft.customParametersDraft();
        customParametersBackup = draft.customParametersBackup();
        customParametersModalOpen = false;
        selectedCustomParameterPath = "";
        modelSettingsSetDefault = draft.setDefault();
    }

    private void closeModelSettingsModal() {
        if (customParametersModalOpen) {
            closeCustomParametersModal(true);
        }

        ModelSettingsDraftSupport.Draft empty = ModelSettingsDraftSupport.empty();
        modelSettingsModalOpen = false;
        modelSettingsProviderId = empty.providerId();
        modelSettingsOriginalId = empty.originalModelId();
        modelSettingsDraft = empty.modelIdDraft();
        modelSettingsTemperatureDraft = empty.temperatureDraft();
        modelSettingsKeepAliveDraft = empty.keepAliveDraft();
        modelSettingsSupportsSystemDraft = empty.supportsSystem();
        modelSettingsInjectPromptIntoUserDraft = empty.injectPromptIntoUser();
        modelSettingsStructuredOutputDraft = empty.structuredOutput();
        modelSettingsSystemPromptSuffixDraft = empty.systemPromptSuffixDraft();
        modelSettingsCustomParametersDraft = empty.customParametersDraft();
        customParametersBackup = empty.customParametersBackup();
        selectedCustomParameterPath = "";
        modelSettingsSetDefault = empty.setDefault();
    }

    private void openCustomParametersModal() {
        customParametersModalOpen = true;
        customParametersBackup = CustomParameterEntry.deepCopyList(modelSettingsCustomParametersDraft);
        selectedCustomParameterPath = CustomParametersModalSupport.ensureSelection(modelSettingsCustomParametersDraft, selectedCustomParameterPath);
    }

    private void closeCustomParametersModal(boolean keepChanges) {
        if (!keepChanges) {
            modelSettingsCustomParametersDraft = CustomParameterEntry.deepCopyList(customParametersBackup);
        }
        customParametersModalOpen = false;
        selectedCustomParameterPath = "";
    }

    private void addCustomParametersModal() {
        CustomParametersModalSupport.Fields fields = CustomParametersModalSupport.render(
                this.width,
                this.height,
                modelSettingsCustomParametersDraft,
                () -> selectedCustomParameterPath,
                value -> selectedCustomParameterPath = ProviderProfileSupport.sanitizeText(value),
                ModConfigScreen::t,
                floatingActionBlockRegistry::add,
                (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                        addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
                message -> setStatus(message, COLOR_STATUS_ERROR),
                () -> rebuildActionBlocks(FocusTarget.CUSTOM_PARAMETER_NAME),
                () -> {
                    closeCustomParametersModal(false);
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
                () -> {
                    closeCustomParametersModal(true);
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
                CUSTOM_PARAMETERS_MODAL_STYLE
        );
        customParameterNameField = fields.nameField();
        customParameterValueField = fields.valueField();
    }

    private void addModelSettingsModal(ProviderManagerConfig providerManager) {
        ApiProviderProfile profile = providerManager.findById(modelSettingsProviderId);
        if (profile == null) {
            closeModelSettingsModal();
            return;
        }

        modelSettingsField = ModelSettingsModalSupport.render(
                profile,
                this.width,
                this.height,
                modelSettingsDraft,
                modelSettingsTemperatureDraft,
                modelSettingsKeepAliveDraft,
                modelSettingsSystemPromptSuffixDraft,
                CustomParameterTreeSupport.countEntries(modelSettingsCustomParametersDraft),
                modelSettingsSupportsSystemDraft,
                modelSettingsInjectPromptIntoUserDraft,
                modelSettingsStructuredOutputDraft,
                modelSettingsSetDefault,
                ModConfigScreen::t,
                floatingActionBlockRegistry::add,
                this::addFloatingCheckbox,
                (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                        addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
                value -> modelSettingsDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsKeepAliveDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsSystemPromptSuffixDraft = ProviderProfileSupport.sanitizeText(value),
                () -> {
                    openCustomParametersModal();
                    rebuildActionBlocks(FocusTarget.CUSTOM_PARAMETER_NAME);
                },
                value -> {
                    modelSettingsSupportsSystemDraft = value;
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
                value -> modelSettingsInjectPromptIntoUserDraft = value,
                value -> modelSettingsStructuredOutputDraft = value,
                value -> modelSettingsSetDefault = value,
                () -> {
                    closeModelSettingsModal();
                    rebuildActionBlocks();
                },
                () -> applyModelSettings(profile),
                MODEL_SETTINGS_MODAL_STYLE,
                CHECKBOX_STYLE
        );
    }

    private void applyModelSettings(ApiProviderProfile profile) {
        ModelSettingsApplySupport.ApplyResult result = ModelSettingsApplySupport.apply(
                profile,
                modelSettingsOriginalId,
                modelSettingsDraft,
                modelSettingsTemperatureDraft,
                modelSettingsKeepAliveDraft,
                modelSettingsSupportsSystemDraft,
                modelSettingsInjectPromptIntoUserDraft,
                modelSettingsStructuredOutputDraft,
                modelSettingsSystemPromptSuffixDraft,
                modelSettingsCustomParametersDraft,
                modelSettingsSetDefault
        );
        if (!result.success()) {
            setStatus(
                    result.errorArg() == null ? t(result.errorKey()) : t(result.errorKey(), result.errorArg()),
                    COLOR_STATUS_ERROR
            );
            return;
        }

        closeModelSettingsModal();
        setStatus(result.creating() ? t("status.added_model", result.modelId()) : t("status.updated_model", result.modelId()), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void removeModel(ApiProviderProfile profile, String modelId) {
        boolean removed = ModelSettingsMutationSupport.removeModel(profile, modelId);
        if (!removed) {
            return;
        }

        setStatus(t("status.removed_model", modelId), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void deleteProvider(ProviderManagerConfig providerManager, ApiProviderProfile profile) {
        ProviderManagerMutationSupport.DeleteProviderResult result = ProviderManagerMutationSupport.deleteProvider(
                providerManager,
                selectedProviderId,
                profile
        );
        String removedId = result.removedProviderId();
        selectedProviderId = result.selectedProviderId();

        setStatus(t("status.removed_profile", removedId), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void addToggleAction(int x, int y, int width, Text label, BooleanSupplier getter, Consumer<Boolean> setter) {
        checkboxBlocks.add(new CheckboxBlock(
                x,
                y,
                width,
                20,
                () -> label,
                getter.getAsBoolean(),
                setter,
                CHECKBOX_STYLE
        ));
    }

    private void addGroupBox(int x, int y, int width, int height, Text title) {
        groupBoxes.add(new GroupBox(x, y, width, height, title, GROUP_BOX_STYLE));
    }

    private void addActionRow(int x, int y, int width, Text label, Runnable action) {
        contentActionBlockRegistry.add(x, y, width, 20, label, action);
    }

    private void addTextInputRow(
            int x,
            int y,
            int width,
            Text label,
            int maxLength,
            String initialValue,
            Text placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable
    ) {
        if (!editable) {
            addStaticTextRow(x, y, width, label, Text.literal(initialValue == null ? "" : initialValue));
            return;
        }

        int labelWidth = responsiveLabelWidth(width);
        int fieldGap = 6;
        int fieldX = x + labelWidth + fieldGap;
        int fieldWidth = Math.max(72, width - labelWidth - fieldGap);

        contentActionBlockRegistry.add(x, y, labelWidth, 20, label, () -> {
        });
        addTextField(
                fieldX,
                y,
                fieldWidth,
                maxLength,
                initialValue == null ? "" : initialValue,
                placeholder,
                changed,
                textPredicate,
                editable
        );
    }

    private void addStaticTextRow(int x, int y, int width, Text label, Text value) {
        int labelWidth = responsiveLabelWidth(width);
        staticTextRows.add(new StaticTextRow(
                x,
                y,
                width,
                labelWidth,
                label,
                value,
                COLOR_TEXT,
                COLOR_TEXT_MUTED
        ));
    }

    private int responsiveLabelWidth(int width) {
        int minimum = width < 360 ? 88 : 120;
        return Math.min(180, Math.max(minimum, width / 3));
    }

    private Text hotkeyBindingLabel(ConfigSectionContentSupport.HotkeyTarget target, InputBindingConfig binding) {
        if (hotkeyCaptureTarget == target) {
            return hotkeyBindingLabelText(target, t("state.hotkey_listening"));
        }

        if (!KeybindingManager.isBound(binding)) {
            return hotkeyBindingLabelText(target, t("state.hotkey_unbound"));
        }

        return hotkeyBindingLabelText(target, Text.literal(KeybindingManager.displayName(binding)));
    }

    private void startHotkeyCapture(ConfigSectionContentSupport.HotkeyTarget target) {
        hotkeyCaptureTarget = target;
        setStatus(t("status.hotkey_listening", sectionLabel(target)), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void clearHotkeyBinding(ConfigSectionContentSupport.HotkeyTarget target) {
        if (target == ConfigSectionContentSupport.HotkeyTarget.ITEM) {
            ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(Translate_AllinOne.getConfig());
            if (keybinding.binding == null) {
                keybinding.binding = new InputBindingConfig();
            }
            if (keybinding.refreshBinding == null) {
                keybinding.refreshBinding = new InputBindingConfig();
            }

            if (hotkeyCaptureTarget == ConfigSectionContentSupport.HotkeyTarget.ITEM
                    || hotkeyCaptureTarget == ConfigSectionContentSupport.HotkeyTarget.ITEM_REFRESH) {
                hotkeyCaptureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            setStatus(t("status.hotkey_cleared", sectionLabel(target)), COLOR_STATUS_OK);
            rebuildActionBlocks();
            return;
        }

        if (target == ConfigSectionContentSupport.HotkeyTarget.WYNNTILS_TASK_TRACKER) {
            WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(Translate_AllinOne.getConfig());
            if (keybinding.binding == null) {
                keybinding.binding = new InputBindingConfig();
            }
            if (keybinding.refreshBinding == null) {
                keybinding.refreshBinding = new InputBindingConfig();
            }

            if (hotkeyCaptureTarget == ConfigSectionContentSupport.HotkeyTarget.WYNNTILS_TASK_TRACKER
                    || hotkeyCaptureTarget == ConfigSectionContentSupport.HotkeyTarget.WYNNTILS_TASK_TRACKER_REFRESH) {
                hotkeyCaptureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            setStatus(t("status.hotkey_cleared", sectionLabel(target)), COLOR_STATUS_OK);
            rebuildActionBlocks();
            return;
        }

        InputBindingConfig binding = ensureBinding(target);
        if (binding == null) {
            return;
        }

        hotkeyCaptureTarget = hotkeyCaptureTarget == target ? null : hotkeyCaptureTarget;
        KeybindingManager.clear(binding);
        setStatus(t("status.hotkey_cleared", sectionLabel(target)), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void cycleHotkeyMode(ConfigSectionContentSupport.HotkeyTarget target) {
        ModConfig config = Translate_AllinOne.getConfig();
        switch (target) {
            case ITEM -> {
                ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(config);
                ItemTranslateConfig.KeybindingMode[] modes = ItemTranslateConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                setStatus(t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), COLOR_STATUS_OK);
            }
            case SCOREBOARD -> {
                ScoreboardConfig.KeybindingConfig keybinding = ensureScoreboardKeybinding(config);
                ScoreboardConfig.KeybindingMode[] modes = ScoreboardConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                setStatus(t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), COLOR_STATUS_OK);
            }
            case WYNNTILS_TASK_TRACKER -> {
                WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(config);
                WynnCraftConfig.KeybindingMode[] modes = WynnCraftConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                setStatus(t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), COLOR_STATUS_OK);
            }
            default -> {
                return;
            }
        }
        rebuildActionBlocks();
    }

    private InputBindingConfig ensureBinding(ConfigSectionContentSupport.HotkeyTarget target) {
        ModConfig config = Translate_AllinOne.getConfig();
        return switch (target) {
            case CHAT_INPUT -> {
                if (config.chatTranslate.input.keybinding == null) {
                    config.chatTranslate.input.keybinding = new InputBindingConfig();
                }
                yield config.chatTranslate.input.keybinding;
            }
            case ITEM -> {
                ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(config);
                if (keybinding.binding == null) {
                    keybinding.binding = new InputBindingConfig();
                }
                yield keybinding.binding;
            }
            case ITEM_REFRESH -> {
                ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(config);
                if (keybinding.refreshBinding == null) {
                    keybinding.refreshBinding = new InputBindingConfig();
                }
                yield keybinding.refreshBinding;
            }
            case SCOREBOARD -> {
                ScoreboardConfig.KeybindingConfig keybinding = ensureScoreboardKeybinding(config);
                if (keybinding.binding == null) {
                    keybinding.binding = new InputBindingConfig();
                }
                yield keybinding.binding;
            }
            case WYNNTILS_TASK_TRACKER -> {
                WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(config);
                if (keybinding.binding == null) {
                    keybinding.binding = new InputBindingConfig();
                }
                yield keybinding.binding;
            }
            case WYNNTILS_TASK_TRACKER_REFRESH -> {
                WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(config);
                if (keybinding.refreshBinding == null) {
                    keybinding.refreshBinding = new InputBindingConfig();
                }
                yield keybinding.refreshBinding;
            }
        };
    }

    private ItemTranslateConfig.KeybindingConfig ensureItemKeybinding(ModConfig config) {
        if (config.itemTranslate.keybinding == null) {
            config.itemTranslate.keybinding = new ItemTranslateConfig.KeybindingConfig();
        }
        return config.itemTranslate.keybinding;
    }

    private ScoreboardConfig.KeybindingConfig ensureScoreboardKeybinding(ModConfig config) {
        if (config.scoreboardTranslate.keybinding == null) {
            config.scoreboardTranslate.keybinding = new ScoreboardConfig.KeybindingConfig();
        }
        return config.scoreboardTranslate.keybinding;
    }

    private WynnCraftConfig.KeybindingConfig ensureWynntilsTaskTrackerKeybinding(ModConfig config) {
        if (config.wynnCraft == null) {
            config.wynnCraft = new WynnCraftConfig();
        }
        if (config.wynnCraft.wynntils_task_tracker == null) {
            config.wynnCraft.wynntils_task_tracker = new WynnCraftConfig.WynntilsTaskTrackerConfig();
        }
        if (config.wynnCraft.wynntils_task_tracker.keybinding == null) {
            config.wynnCraft.wynntils_task_tracker.keybinding = new WynnCraftConfig.KeybindingConfig();
        }
        if (config.wynnCraft.wynntils_task_tracker.keybinding.refreshBinding == null) {
            config.wynnCraft.wynntils_task_tracker.keybinding.refreshBinding = new InputBindingConfig();
        }
        return config.wynnCraft.wynntils_task_tracker.keybinding;
    }

    private void applyCapturedBinding(InputBindingConfig captured) {
        if (hotkeyCaptureTarget == null || captured == null) {
            return;
        }

        ConfigSectionContentSupport.HotkeyTarget target = hotkeyCaptureTarget;
        InputBindingConfig binding = ensureBinding(target);
        KeybindingManager.apply(binding, captured);
        hotkeyCaptureTarget = null;
        setStatus(t("status.hotkey_bound", sectionLabel(target), Text.literal(KeybindingManager.displayName(binding))), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void cancelHotkeyCapture() {
        if (hotkeyCaptureTarget == null) {
            return;
        }
        hotkeyCaptureTarget = null;
        setStatus(t("status.hotkey_capture_cancelled"), COLOR_TEXT_MUTED);
        rebuildActionBlocks();
    }

    private Text sectionLabel(ConfigSectionContentSupport.HotkeyTarget target) {
        return switch (target) {
            case CHAT_INPUT -> t("section.chat_input");
            case ITEM -> t("section.item");
            case ITEM_REFRESH -> t("section.item");
            case SCOREBOARD -> t("section.scoreboard");
            case WYNNTILS_TASK_TRACKER -> t("group.wynntils_task_tracker");
            case WYNNTILS_TASK_TRACKER_REFRESH -> t("group.wynntils_task_tracker");
        };
    }

    private Text hotkeyBindingLabelText(ConfigSectionContentSupport.HotkeyTarget target, Text bindingLabel) {
        return switch (target) {
            case ITEM_REFRESH -> t("label.item_refresh_hotkey_binding", bindingLabel);
            case WYNNTILS_TASK_TRACKER_REFRESH -> t("label.item_refresh_hotkey_binding", bindingLabel);
            default -> t("label.hotkey_binding", bindingLabel);
        };
    }

    private Text modeLabel(String modeName) {
        return switch (modeName) {
            case "HOLD_TO_TRANSLATE" -> t("state.hold_to_translate");
            case "HOLD_TO_SEE_ORIGINAL" -> t("state.hold_to_see_original");
            default -> t("state.disabled");
        };
    }

    private void addFloatingCheckbox(
            int x,
            int y,
            int width,
            int height,
            Supplier<Text> labelSupplier,
            BooleanSupplier checked,
            Consumer<Boolean> changed,
            CheckboxBlock.Style style
    ) {
        floatingCheckboxBlocks.add(new CheckboxBlock(
                x,
                y,
                width,
                height,
                labelSupplier,
                checked.getAsBoolean(),
                changed,
                style
        ));
    }

    private void addIntSliderAction(
            int x,
            int y,
            int width,
            Text label,
            int min,
            int max,
            IntSupplier getter,
            IntConsumer setter
    ) {
        sliderBlocks.add(new IntSliderBlock(x, y, width, SLIDER_BLOCK_HEIGHT, label, min, max, getter, setter, textRenderer, SLIDER_STYLE));
    }

    private void setStatus(Text message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusExpireAtMillis = System.currentTimeMillis() + 4000;
    }

    private void openCacheDirectory() {
        Path cacheDirectory = CacheBackupManager.getCacheDirectory();
        try {
            Files.createDirectories(cacheDirectory);
            Util.getOperatingSystem().open(cacheDirectory.toUri());
            setStatus(t("status.opened_cache_directory", Text.literal(cacheDirectory.toString())), COLOR_STATUS_OK);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to open cache directory {}", cacheDirectory, e);
            setStatus(t("status.failed_open_cache_directory"), COLOR_STATUS_ERROR);
        }
    }

    private void openDictionaryDirectory() {
        Path dictionaryDirectory = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Translate_AllinOne.MOD_ID)
                .resolve("dictionary");
        try {
            Files.createDirectories(dictionaryDirectory);
            Util.getOperatingSystem().open(dictionaryDirectory.toUri());
            setStatus(t("status.opened_dictionary_directory", Text.literal(dictionaryDirectory.toString())), COLOR_STATUS_OK);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to open dictionary directory {}", dictionaryDirectory, e);
            setStatus(t("status.failed_open_dictionary_directory"), COLOR_STATUS_ERROR);
        }
    }

    private void openWynnDialogueHudEditor() {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new WynnDialogueHudEditorScreen(this));
    }

    private void openRepositoryLink() {
        if (repositoryUrl.isBlank()) {
            setStatus(t("status.failed_open_repository"), COLOR_STATUS_ERROR);
            return;
        }

        try {
            Util.getOperatingSystem().open(repositoryUrl);
            setStatus(t("status.opened_repository"), COLOR_STATUS_OK);
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to open repository url {}", repositoryUrl, e);
            setStatus(t("status.failed_open_repository"), COLOR_STATUS_ERROR);
        }
    }

    private void saveAndClose() {
        boolean saved = ConfigUiRuntimeSupport.saveConfig(
                ModConfigScreen::t,
                this::setStatus,
                COLOR_STATUS_OK,
                COLOR_STATUS_ERROR,
                (message, throwable) -> Translate_AllinOne.LOGGER.error(message, throwable)
        );
        if (!saved) {
            return;
        }
        restoreSnapshotOnClose = false;
        finishClose();
    }

    private void cancelAndClose() {
        close();
    }

    private void discardChangesAndClose() {
        finishClose();
    }

    private void openResetConfirmation() {
        resetConfirmModalOpen = true;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        rebuildActionBlocks();
    }

    private void openUpdateNoticeModal() {
        updateNoticeAutoPrompted = true;
        updateNoticeModalOpen = true;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
    }

    private void openDictionaryFilesModal(DictionaryFileSelectionSupport.Slot slot) {
        dictionaryFilesModalSlot = slot == null ? DictionaryFileSelectionSupport.Slot.WYNNCRAFT_DIALOGUE : slot;
        dictionaryFilesModalOpen = true;
        dictionaryFilesSelectionBackup = new ArrayList<>(
                DictionaryFileSelectionSupport.getSelectedFiles(ensureDictionaryConfig(), dictionaryFilesModalSlot)
        );
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        rebuildActionBlocks();
    }

    private void closeUpdateNoticeModal() {
        updateNoticeModalOpen = false;
    }

    private void closeDictionaryFilesModal(boolean keepChanges) {
        if (!keepChanges) {
            DictionaryFileSelectionSupport.setSelectedFiles(
                    ensureDictionaryConfig(),
                    dictionaryFilesModalSlot,
                    dictionaryFilesSelectionBackup
            );
        }
        dictionaryFilesModalOpen = false;
        dictionaryFilesSelectionBackup = new ArrayList<>();
    }

    private void closeResetConfirmModal() {
        resetConfirmModalOpen = false;
    }

    private void openUnsavedChangesConfirmModal() {
        unsavedChangesConfirmModalOpen = true;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
    }

    private void closeUnsavedChangesConfirmModal() {
        unsavedChangesConfirmModalOpen = false;
    }

    private void addResetConfirmModal() {
        UiRect modalRect = ConfigUiModalSupport.resetConfirmModalRect(this.width, this.height);
        int buttonWidth = 92;
        int buttonHeight = 20;
        int buttonGap = 10;
        int buttonY = modalRect.bottom() - 30;
        int resetX = modalRect.right() - 16 - buttonWidth;
        int cancelX = resetX - buttonGap - buttonWidth;

        floatingActionBlockRegistry.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.cancel"),
                () -> {
                    closeResetConfirmModal();
                    rebuildActionBlocks();
                },
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                resetX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.reset"),
                this::applyResetToDefaults,
                COLOR_BLOCK_DANGER,
                COLOR_BLOCK_DANGER_HOVER,
                COLOR_TEXT,
                true
        );
    }

    private void addUpdateNoticeModal() {
        UiRect modalRect = ConfigUiModalSupport.updateNoticeModalRect(this.width, this.height);
        int buttonWidth = 108;
        int buttonHeight = 20;
        int buttonGap = 10;
        int buttonY = modalRect.bottom() - 30;
        int openX = modalRect.right() - 16 - buttonWidth;
        int cancelX = openX - buttonGap - buttonWidth;

        floatingActionBlockRegistry.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.cancel"),
                () -> {
                    closeUpdateNoticeModal();
                    rebuildActionBlocks();
                },
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                openX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.open_release"),
                () -> {
                    UpdateCheckManager.openLatestReleasePage();
                    closeUpdateNoticeModal();
                    rebuildActionBlocks();
                },
                COLOR_BLOCK_ACCENT,
                COLOR_BLOCK_ACCENT_HOVER,
                COLOR_TEXT,
                true
        );
    }

    private void addUnsavedChangesConfirmModal() {
        UiRect modalRect = ConfigUiModalSupport.unsavedChangesConfirmModalRect(this.width, this.height);
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = modalRect.bottom() - 30;
        int cancelX = modalRect.right() - 16 - buttonWidth;
        int discardX = cancelX - buttonGap - buttonWidth;
        int saveX = discardX - buttonGap - buttonWidth;

        floatingActionBlockRegistry.add(
                saveX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.save_and_close"),
                this::saveAndClose,
                COLOR_BLOCK_ACCENT,
                COLOR_BLOCK_ACCENT_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                discardX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.close_without_saving"),
                this::discardChangesAndClose,
                COLOR_BLOCK_DANGER,
                COLOR_BLOCK_DANGER_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.cancel"),
                () -> {
                    closeUnsavedChangesConfirmModal();
                    rebuildActionBlocks();
                },
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true
        );
    }

    private void addDictionaryFilesModal() {
        UiRect modalRect = ConfigUiModalSupport.dictionaryFilesModalRect(this.width, this.height);
        int contentX = modalRect.x + 16;
        int contentWidth = modalRect.width - 32;
        int rowY = modalRect.y + 48;
        int rowHeight = 20;
        int rowGap = 4;
        int maxRowsBottom = modalRect.bottom() - 58;

        DictionaryConfig dictionaryConfig = ensureDictionaryConfig();
        List<String> availableFiles = DictionaryFileSelectionSupport.listAvailableFiles();
        List<String> selectedFiles = DictionaryFileSelectionSupport.getSelectedFiles(dictionaryConfig, dictionaryFilesModalSlot);

        floatingActionBlockRegistry.add(
                contentX,
                rowY,
                contentWidth,
                rowHeight,
                t("modal.dictionary_files.current", dictionaryFileDisplayValue(dictionaryConfig, dictionaryFilesModalSlot)),
                () -> {
                },
                COLOR_BLOCK_MUTED,
                COLOR_BLOCK_MUTED,
                COLOR_TEXT,
                false
        );
        rowY += rowHeight + rowGap;

        floatingActionBlockRegistry.add(
                contentX,
                rowY,
                contentWidth,
                rowHeight,
                t("modal.dictionary_files.instructions"),
                () -> {
                },
                COLOR_BLOCK_MUTED,
                COLOR_BLOCK_MUTED,
                COLOR_TEXT_MUTED,
                false
        );
        rowY += rowHeight + 8;

        if (availableFiles.isEmpty()) {
            floatingActionBlockRegistry.add(
                    contentX,
                    rowY,
                    contentWidth,
                    rowHeight,
                    t("modal.dictionary_files.empty"),
                    () -> {
                    },
                    COLOR_BLOCK_MUTED,
                    COLOR_BLOCK_MUTED,
                    COLOR_TEXT_MUTED,
                    false
            );
        } else {
            for (String fileName : availableFiles) {
                if (rowY + rowHeight > maxRowsBottom) {
                    break;
                }
                boolean selected = selectedFiles.contains(fileName);
                Text label = selected
                        ? t("modal.dictionary_files.entry_selected", fileName)
                        : Text.literal(fileName);
                floatingActionBlockRegistry.add(
                        contentX,
                        rowY,
                        contentWidth,
                        rowHeight,
                        label,
                        () -> {
                            if (DictionaryFileSelectionSupport.isMultiSelect(dictionaryFilesModalSlot)) {
                                DictionaryFileSelectionSupport.toggleSelectedFile(
                                        ensureDictionaryConfig(),
                                        dictionaryFilesModalSlot,
                                        fileName
                                );
                            } else {
                                DictionaryFileSelectionSupport.setSelectedFile(
                                        ensureDictionaryConfig(),
                                        dictionaryFilesModalSlot,
                                        fileName
                                );
                            }
                            rebuildActionBlocks();
                        },
                        selected ? COLOR_BLOCK_SELECTED : COLOR_BLOCK,
                        selected ? COLOR_BLOCK_SELECTED_HOVER : COLOR_BLOCK_HOVER,
                        selected ? COLOR_TEXT_ACCENT : COLOR_TEXT,
                        false
                );
                rowY += rowHeight + rowGap;
            }
        }

        int buttonWidth = 104;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = modalRect.bottom() - 30;
        int doneX = modalRect.right() - 16 - buttonWidth;
        int cancelX = doneX - buttonGap - buttonWidth;
        int clearX = cancelX - buttonGap - buttonWidth;

        floatingActionBlockRegistry.add(
                clearX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.clear_selection"),
                () -> {
                    DictionaryFileSelectionSupport.setSelectedFiles(
                            ensureDictionaryConfig(),
                            dictionaryFilesModalSlot,
                            List.of()
                    );
                    rebuildActionBlocks();
                },
                COLOR_BLOCK_DANGER,
                COLOR_BLOCK_DANGER_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.cancel"),
                () -> {
                    closeDictionaryFilesModal(false);
                    rebuildActionBlocks();
                },
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true
        );

        floatingActionBlockRegistry.add(
                doneX,
                buttonY,
                buttonWidth,
                buttonHeight,
                t("button.done"),
                () -> {
                    closeDictionaryFilesModal(true);
                    rebuildActionBlocks();
                },
                COLOR_BLOCK_ACCENT,
                COLOR_BLOCK_ACCENT_HOVER,
                COLOR_TEXT,
                true
        );
    }

    private void renderResetConfirmModalMessage(DrawContext context) {
        UiRect modalRect = ConfigUiModalSupport.resetConfirmModalRect(this.width, this.height);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<OrderedText> lines = this.textRenderer.wrapLines(t("modal.reset_confirm.message"), maxWidth);
        int lineY = textY;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, textX, lineY, COLOR_TEXT_MUTED, false);
            lineY += this.textRenderer.fontHeight + 2;
        }
    }

    private void renderUpdateNoticeModalMessage(DrawContext context) {
        UiRect modalRect = ConfigUiModalSupport.updateNoticeModalRect(this.width, this.height);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<OrderedText> lines = this.textRenderer.wrapLines(
                t("modal.update_notice.message", UpdateCheckManager.latestVersion(), UpdateCheckManager.currentVersion()),
                maxWidth
        );
        int lineY = textY;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, textX, lineY, COLOR_TEXT_MUTED, false);
            lineY += this.textRenderer.fontHeight + 2;
        }
    }

    private void renderUnsavedChangesConfirmModalMessage(DrawContext context) {
        UiRect modalRect = ConfigUiModalSupport.unsavedChangesConfirmModalRect(this.width, this.height);
        int textX = modalRect.x + 16;
        int textY = modalRect.y + 48;
        int maxWidth = Math.max(10, modalRect.width - 32);
        List<OrderedText> lines = this.textRenderer.wrapLines(t("modal.unsaved_changes.message"), maxWidth);
        int lineY = textY;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, textX, lineY, COLOR_TEXT_MUTED, false);
            lineY += this.textRenderer.fontHeight + 2;
        }
    }

    private void applyResetToDefaults() {
        closeResetConfirmModal();
        ConfigManager.resetToDefaults();
        hotkeyCaptureTarget = null;
        selectedProviderIndex = 0;
        selectedProviderId = "";
        providerApiKeyVisible = isProviderApiKeyVisible(Translate_AllinOne.getConfig().providerManager);
        providerSearchQuery = "";
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        stopScrollingDrag();
        contentElasticOffset = 0.0;
        contentElasticVelocity = 0.0;
        setStatus(t("status.config_reset_pending"), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    @Override
    public void close() {
        hotkeyCaptureTarget = null;
        stopScrollingDrag();
        ConfigUiModalInteractionSupport.ModalCloseAction action = ConfigUiModalInteractionSupport.closeByPriority(
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen
        );

        switch (action) {
            case CLOSE_UPDATE_NOTICE -> {
                closeUpdateNoticeModal();
                rebuildActionBlocks();
                return;
            }
            case CLOSE_RESET_CONFIRM -> {
                closeResetConfirmModal();
                rebuildActionBlocks();
                return;
            }
            case CLOSE_UNSAVED_CHANGES -> {
                closeUnsavedChangesConfirmModal();
                rebuildActionBlocks();
                return;
            }
            case CLOSE_DICTIONARY_FILES -> {
                closeDictionaryFilesModal(false);
                rebuildActionBlocks();
                return;
            }
            case CLOSE_ADD_PROVIDER -> {
                closeAddProviderModal();
                rebuildActionBlocks();
                return;
            }
            case CLOSE_CUSTOM_PARAMETERS -> {
                closeCustomParametersModal(true);
                rebuildActionBlocks(FocusTarget.MODEL_NAME);
                return;
            }
            case CLOSE_MODEL_SETTINGS -> {
                closeModelSettingsModal();
                rebuildActionBlocks();
                return;
            }
            default -> {
            }
        }

        if (restoreSnapshotOnClose && hasUnsavedChanges()) {
            openUnsavedChangesConfirmModal();
            rebuildActionBlocks();
            return;
        }
        finishClose();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (hotkeyCaptureTarget != null) {
            if (hotkeyCaptureTarget != ConfigSectionContentSupport.HotkeyTarget.CHAT_INPUT) {
                InputBindingConfig captured = KeybindingManager.captureMouseBinding(click.button());
                if (captured != null) {
                    applyCapturedBinding(captured);
                }
            }
            return true;
        }

        if (click.button() != 0) {
            return super.mouseClicked(click, doubled);
        }

        double mouseX = click.x();
        double mouseY = click.y();
        boolean modalOpen = isAnyModalOpen();

        if (modalOpen) {
            draggingSlider = null;
            if (!isInsideOpenModal(mouseX, mouseY)) {
                ConfigUiModalInteractionSupport.ModalCloseAction action = ConfigUiModalInteractionSupport.outsideModalClickAction(
                        addProviderModalOpen,
                        modelSettingsModalOpen,
                        customParametersModalOpen,
                        dictionaryFilesModalOpen,
                        resetConfirmModalOpen,
                        updateNoticeModalOpen,
                        unsavedChangesConfirmModalOpen
                );
                switch (action) {
                    case CLOSE_UPDATE_NOTICE -> {
                        closeUpdateNoticeModal();
                        rebuildActionBlocks();
                        return true;
                    }
                    case CLOSE_RESET_CONFIRM -> {
                        closeResetConfirmModal();
                        rebuildActionBlocks();
                        return true;
                    }
                    case CLOSE_UNSAVED_CHANGES -> {
                        closeUnsavedChangesConfirmModal();
                        rebuildActionBlocks();
                        return true;
                    }
                    case CLOSE_DICTIONARY_FILES -> {
                        closeDictionaryFilesModal(false);
                        rebuildActionBlocks();
                        return true;
                    }
                    case CLOSE_CUSTOM_PARAMETERS -> {
                        closeCustomParametersModal(true);
                        rebuildActionBlocks(FocusTarget.MODEL_NAME);
                        return true;
                    }
                    case CLOSE_ADD_PROVIDER -> {
                        closeAddProviderModal();
                        rebuildActionBlocks();
                        return true;
                    }
                    case CLOSE_MODEL_SETTINGS -> {
                        closeModelSettingsModal();
                        rebuildActionBlocks();
                        return true;
                    }
                    default -> {
                    }
                }
            }

            if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingActionBlocks, mouseX, mouseY)) {
                return true;
            }

            if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(floatingCheckboxBlocks, mouseX, mouseY)) {
                return true;
            }

            if (super.mouseClicked(click, doubled)) {
                return true;
            }
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingActionBlocks, mouseX, mouseY)) {
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(floatingCheckboxBlocks, mouseX, mouseY)) {
            return true;
        }

        updateVersionLinkRect(System.currentTimeMillis());
        if (versionLinkRect.width > 0 && versionLinkRect.height > 0 && versionLinkRect.contains(mouseX, mouseY)) {
            openRepositoryLink();
            return true;
        }

        if (click.button() == 0) {
            UiRect thumb = scrollbarThumbRect();
            if (thumb != null && thumb.contains(mouseX, mouseY)) {
                startScrollbarDrag();
                return true;
            }

            UiRect track = scrollbarTrackRect();
            if (track != null && track.contains(mouseX, mouseY)) {
                int nextOffset = scrollOffsetFromThumbMouseY(mouseY);
                if (nextOffset != contentScrollOffset) {
                    applyScrollOffset(nextOffset, false);
                }
                startScrollbarDrag();
                return true;
            }
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(actionBlocks, mouseX, mouseY)) {
            return true;
        }

        if (!contentViewport.contains(mouseX, mouseY)) {
            if (addProviderTypeDropdownOpen || routeDropdownSlot != null) {
                addProviderTypeDropdownOpen = false;
                routeDropdownSlot = null;
                rebuildActionBlocks();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        IntSliderBlock selectedSlider = ConfigUiInteractionSupport.pickSlider(sliderBlocks, mouseX, mouseY);
        if (selectedSlider != null) {
            draggingSlider = selectedSlider;
            selectedSlider.startDrag(mouseX);
            return true;
        }

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(contentActionBlocks, mouseX, mouseY)) {
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(checkboxBlocks, mouseX, mouseY)) {
            return true;
        }

        if (contentScrollMaxOffset > 0) {
            draggingContentByMouse = true;
            contentDragStartMouseY = mouseY;
            contentDragStartOffset = contentScrollOffset;
            draggingContentScrollbar = false;
            return true;
        }

        if (addProviderTypeDropdownOpen || routeDropdownSlot != null) {
            addProviderTypeDropdownOpen = false;
            routeDropdownSlot = null;
            rebuildActionBlocks();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingSlider != null && click.button() == 0) {
            draggingSlider.dragTo(click.x());
            return true;
        }

        if (draggingContentScrollbar && click.button() == 0) {
            int nextOffset = scrollOffsetFromThumbMouseY(click.y());
            if (nextOffset != contentScrollOffset) {
                applyScrollOffset(nextOffset, true);
            }
            return true;
        }

        if (draggingContentByMouse && click.button() == 0) {
            double delta = click.y() - contentDragStartMouseY;
            double rawOffset = contentDragStartOffset - delta;
            int nextOffset = clampScrollOffset((int) Math.round(rawOffset));
            if (nextOffset != contentScrollOffset) {
                applyScrollOffset(nextOffset, true);
            }

            double overflow = rawOffset - nextOffset;
            if (Math.abs(overflow) > 0.001) {
                contentElasticOffset = clampDouble(-overflow * OVER_SCROLL_RESISTANCE, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);
                contentElasticVelocity = 0.0;
                rebuildActionBlocks();
            } else if (Math.abs(contentElasticOffset) > 0.001 || Math.abs(contentElasticVelocity) > 0.001) {
                contentElasticOffset = 0.0;
                contentElasticVelocity = 0.0;
                rebuildActionBlocks();
            }
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingSlider != null && click.button() == 0) {
            draggingSlider.release();
            draggingSlider = null;
            return true;
        }

        if ((draggingContentScrollbar || draggingContentByMouse) && click.button() == 0) {
            stopScrollingDrag();
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isAnyModalOpen()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (!contentViewport.contains(mouseX, mouseY) || contentScrollMaxOffset <= 0 || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int delta = (int) Math.round(-verticalAmount * SCROLL_STEP);
        if (delta == 0) {
            delta = verticalAmount > 0 ? -SCROLL_STEP : SCROLL_STEP;
        }
        if (tryScrollBy(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (hotkeyCaptureTarget != null) {
            if (KeybindingManager.isEscape(input)) {
                cancelHotkeyCapture();
                return true;
            }

            InputBindingConfig captured = KeybindingManager.captureKeyboardBinding(input);
            if (captured != null) {
                applyCapturedBinding(captured);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (hotkeyCaptureTarget != null) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateControlAnimations();

        long nowMillis = System.currentTimeMillis();
        boolean modalOpen = isAnyModalOpen();
        ConfigUiScreenRenderSupport.renderChrome(
                context,
                this.textRenderer,
                this.width,
                this.height,
                TOP_BAR_HEIGHT,
                LEFT_PANEL_WIDTH,
                this.title,
                t("panel", t(selectedSection.translationKey())),
                statusMessage,
                statusColor,
                statusExpireAtMillis,
                nowMillis,
                selectedSection == ConfigSection.PROVIDERS,
                SCREEN_RENDER_STYLE
        );
        renderVersionLink(context, mouseX, mouseY, nowMillis);

        ConfigUiControlRenderer.drawActionBlocks(context, this.textRenderer, actionBlocks, mouseX, mouseY, COLOR_BORDER);

        ConfigUiDraw.withScissor(context, contentClipViewport(), () -> {
            ConfigUiControlRenderer.drawGroupBoxes(context, this.textRenderer, groupBoxes);
            ConfigUiControlRenderer.drawActionBlocks(context, this.textRenderer, contentActionBlocks, mouseX, mouseY, COLOR_BORDER);
            ConfigUiControlRenderer.drawStaticTextRows(context, this.textRenderer, staticTextRows);
            ConfigUiControlRenderer.drawSliderBlocks(context, sliderBlocks, mouseX, mouseY);
            ConfigUiControlRenderer.drawCheckboxBlocks(context, this.textRenderer, checkboxBlocks, mouseX, mouseY);
            if (!modalOpen) {
                super.render(context, mouseX, mouseY, delta);
            }
        });

        if (contentScrollMaxOffset > 0 && !modalOpen) {
            drawContentScrollbar(context, mouseX, mouseY);
        }

        ConfigUiScreenRenderSupport.renderModalOverlayAndShell(
                context,
                this.textRenderer,
                this.width,
                this.height,
                TOP_BAR_HEIGHT,
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen,
                t("modal.add_provider.title"),
                t("modal.model.title"),
                t("custom_params.title"),
                t("modal.dictionary_files.title", dictionarySlotLabel(dictionaryFilesModalSlot)),
                t("modal.reset_confirm.title"),
                t("modal.update_notice.title"),
                t("modal.unsaved_changes.title"),
                SCREEN_RENDER_STYLE
        );

        if (updateNoticeModalOpen) {
            renderUpdateNoticeModalMessage(context);
        } else if (resetConfirmModalOpen) {
            renderResetConfirmModalMessage(context);
        } else if (unsavedChangesConfirmModalOpen) {
            renderUnsavedChangesConfirmModalMessage(context);
        }

        if (modalOpen) {
            super.render(context, mouseX, mouseY, delta);
        }
        ConfigUiControlRenderer.drawCheckboxBlocks(context, this.textRenderer, floatingCheckboxBlocks, mouseX, mouseY);
        ConfigUiControlRenderer.drawActionBlocks(context, this.textRenderer, floatingActionBlocks, mouseX, mouseY, COLOR_BORDER);
    }

    private Text dictionarySlotLabel(DictionaryFileSelectionSupport.Slot slot) {
        return switch (slot) {
            case ITEM_SKILL -> t("label.dictionary_slot_item_skill");
            case WYNNCRAFT_DIALOGUE -> t("label.dictionary_slot_dialogue");
            case WYNNCRAFT_QUEST -> t("label.dictionary_slot_quest");
        };
    }

    private Text dictionaryFileDisplayValue(DictionaryConfig dictionaryConfig, DictionaryFileSelectionSupport.Slot slot) {
        String effectiveSelection = DictionaryFileSelectionSupport.describeEffectiveSelection(dictionaryConfig, slot);
        if (effectiveSelection == null || effectiveSelection.isBlank()) {
            return t("value.dictionary_file_none_selected");
        }
        return Text.literal(effectiveSelection);
    }

    private DictionaryConfig ensureDictionaryConfig() {
        ModConfig config = Translate_AllinOne.getConfig();
        if (config.dictionary == null) {
            config.dictionary = new DictionaryConfig();
        }
        config.dictionary.normalize();
        return config.dictionary;
    }

    private void updateControlAnimations() {
        long now = System.nanoTime();
        double deltaSeconds = (now - sliderAnimationLastNanos) / 1_000_000_000.0;
        sliderAnimationLastNanos = now;
        deltaSeconds = Math.max(0.0, Math.min(0.05, deltaSeconds));
        for (IntSliderBlock slider : sliderBlocks) {
            slider.update(deltaSeconds);
        }
        for (CheckboxBlock checkbox : checkboxBlocks) {
            checkbox.update(deltaSeconds);
        }
        for (CheckboxBlock checkbox : floatingCheckboxBlocks) {
            checkbox.update(deltaSeconds);
        }

        int beforeRenderOffset = currentRenderScrollOffset();

        if (draggingContentScrollbar || draggingContentByMouse) {
            contentVisualOffset = contentScrollOffset;
        } else {
            double blend = 1.0 - Math.exp(-deltaSeconds * SCROLL_ANIMATION_SPEED);
            contentVisualOffset += (contentScrollOffset - contentVisualOffset) * blend;

            double acceleration = -contentElasticOffset * OVER_SCROLL_SPRING - contentElasticVelocity * OVER_SCROLL_DAMPING;
            contentElasticVelocity += acceleration * deltaSeconds;
            contentElasticOffset += contentElasticVelocity * deltaSeconds;
            contentElasticOffset = clampDouble(contentElasticOffset, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);

            if (Math.abs(contentElasticOffset) < 0.01 && Math.abs(contentElasticVelocity) < 0.01) {
                contentElasticOffset = 0.0;
                contentElasticVelocity = 0.0;
            }
        }

        int afterRenderOffset = currentRenderScrollOffset();
        if (afterRenderOffset != beforeRenderOffset) {
            rebuildActionBlocks();
        }
    }

    private record PersistedScreenState(String selectedSectionKey) {
    }

}
