package com.alexeys.translate_allinone.gui;

import com.alexeys.translate_allinone.versionapi.MinecraftScreens;
import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.registration.ConfigManager;
import com.alexeys.translate_allinone.gui.configui.controls.ActionBlock;
import com.alexeys.translate_allinone.gui.configui.controls.ActionBlockRegistry;
import com.alexeys.translate_allinone.gui.configui.controls.CheckboxBlock;
import com.alexeys.translate_allinone.gui.configui.controls.GroupBox;
import com.alexeys.translate_allinone.gui.configui.controls.IntSliderBlock;
import com.alexeys.translate_allinone.gui.configui.controls.StaticTextRow;
import com.alexeys.translate_allinone.gui.configui.interaction.ConfigUiInteractionSupport;
import com.alexeys.translate_allinone.gui.configui.model.HotkeyTarget;
import com.alexeys.translate_allinone.utils.config.ui.ConfigUiModalInteractionSupport;
import com.alexeys.translate_allinone.utils.config.ui.ConfigSection;
import com.alexeys.translate_allinone.utils.config.ui.ConfigUiModalState;
import com.alexeys.translate_allinone.utils.config.ui.FocusTarget;
import com.alexeys.translate_allinone.utils.config.ui.RouteSlot;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import com.alexeys.translate_allinone.gui.configui.modals.AddProviderModalSupport;
import com.alexeys.translate_allinone.gui.configui.modals.ConfirmModalSupport;
import com.alexeys.translate_allinone.gui.configui.modals.CustomParametersModalSupport;
import com.alexeys.translate_allinone.gui.configui.modals.DictionaryFilesModalSupport;
import com.alexeys.translate_allinone.gui.configui.modals.ModelSettingsModalSupport;
import com.alexeys.translate_allinone.gui.configui.modals.ModelTemperatureModalSupport;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiControlRenderer;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiModalSupport;
import com.alexeys.translate_allinone.gui.configui.render.ConfigUiScreenRenderSupport;
import com.alexeys.translate_allinone.gui.configui.sections.ConfigChromeActionsSupport;
import com.alexeys.translate_allinone.gui.configui.sections.ConfigSectionContentSupport;
import com.alexeys.translate_allinone.gui.configui.sections.ProviderDetailSectionSupport;
import com.alexeys.translate_allinone.gui.configui.sections.ProviderListSectionSupport;
import com.alexeys.translate_allinone.gui.configui.sections.ProviderManagerActionsSupport;
import com.alexeys.translate_allinone.gui.configui.sections.RouteModelSectionSupport;
import com.alexeys.translate_allinone.gui.configui.sections.RouteModelSelectorSectionSupport;
import com.alexeys.translate_allinone.gui.configui.setup.SetupWizardModalSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigContentScrollSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigScreenStateSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiFocusSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiTextFieldSupport;
import com.alexeys.translate_allinone.gui.configui.support.HotkeyBindingEditSupport;
import com.alexeys.translate_allinone.utils.config.ui.CustomParameterTreeSupport;
import com.alexeys.translate_allinone.gui.configui.support.ModelCustomParameterDefaultsSupport;
import com.alexeys.translate_allinone.gui.configui.support.ModelSettingsApplySupport;
import com.alexeys.translate_allinone.gui.configui.support.ModelSettingsDraftSupport;
import com.alexeys.translate_allinone.gui.configui.support.ModelSettingsMutationSupport;
import com.alexeys.translate_allinone.utils.config.ui.ModelSettingsValueSupport;
import com.alexeys.translate_allinone.utils.config.ui.ProviderManagerMutationSupport;
import com.alexeys.translate_allinone.utils.config.ui.ProviderProfileSupport;
import com.alexeys.translate_allinone.utils.config.ui.SetupWizardSupport;
import com.alexeys.translate_allinone.utils.cache.CacheBackupManager;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderType;
import com.alexeys.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.CustomParameterEntry;
import com.alexeys.translate_allinone.utils.config.pojos.DebugConfig;
import com.alexeys.translate_allinone.utils.config.pojos.LogLevel;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.translate.DictionaryFileSelectionSupport;
import com.alexeys.translate_allinone.utils.update.UpdateCheckManager;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final int RESET_SLOT_WIDTH = 20;
    private static final int RESET_SLOT_GAP = 2;

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
    private static final int COLOR_STATUS_WARNING = 0xFFFFC061;
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
    private static final ConfigContentScrollSupport.Style CONTENT_SCROLL_STYLE = new ConfigContentScrollSupport.Style(
            COLOR_SCROLL_TRACK,
            COLOR_SCROLL_THUMB,
            COLOR_SCROLL_THUMB_HOVER,
            COLOR_BORDER
    );
    private static final ConfirmModalSupport.Style CONFIRM_MODAL_STYLE = new ConfirmModalSupport.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_STATUS_ERROR
    );
    private static final DictionaryFilesModalSupport.Style DICTIONARY_FILES_MODAL_STYLE = new DictionaryFilesModalSupport.Style(
            COLOR_BLOCK_MUTED,
            COLOR_BLOCK,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_SELECTED,
            COLOR_BLOCK_SELECTED_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_TEXT_ACCENT,
            COLOR_SCROLL_TRACK,
            COLOR_SCROLL_THUMB,
            COLOR_SCROLL_THUMB_HOVER,
            COLOR_BORDER
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
    private static final SetupWizardModalSupport.Style SETUP_WIZARD_STYLE = new SetupWizardModalSupport.Style(
            COLOR_BLOCK,
            COLOR_BLOCK_MUTED,
            COLOR_BLOCK_HOVER,
            COLOR_BLOCK_DANGER,
            COLOR_BLOCK_DANGER_HOVER,
            COLOR_BLOCK_ACCENT,
            COLOR_BLOCK_ACCENT_HOVER,
            COLOR_TEXT,
            COLOR_TEXT_MUTED,
            COLOR_TEXT_ACCENT,
            COLOR_STATUS_OK,
            COLOR_STATUS_ERROR,
            COLOR_STATUS_WARNING,
            COLOR_SLIDER_TRACK,
            COLOR_SLIDER_FILL
    );

    private static final int TOP_BAR_HEIGHT = 40;
    private static final int LEFT_PANEL_WIDTH = 200;
    private static final int TOP_BAR_TITLE_X = 14;
    private static final int TOP_BAR_TEXT_Y = 14;
    private static final int TOP_BAR_ACTIONS_LEFT_MARGIN = 270;
    private static final int TOP_BAR_LINK_GAP = 16;

    private final Screen parent;
    private ModConfig originalConfigSnapshot;
    private String originalConfigSnapshotJson;
    private final String modVersion;
    private final String repositoryUrl;
    private final List<ActionBlock> actionBlocks = new ArrayList<>();
    private final List<ActionBlock> contentActionBlocks = new ArrayList<>();
    private final List<ActionBlock> floatingActionBlocks = new ArrayList<>();
    private final List<ActionBlock> floatingContentActionBlocks = new ArrayList<>();
    private final List<ActionBlock> floatingChromeActionBlocks = new ArrayList<>();
    private final List<GroupBox> groupBoxes = new ArrayList<>();
    private final List<StaticTextRow> staticTextRows = new ArrayList<>();
    private final List<CheckboxBlock> checkboxBlocks = new ArrayList<>();
    private final List<CheckboxBlock> floatingCheckboxBlocks = new ArrayList<>();
    private final ActionBlockRegistry actionBlockRegistry = new ActionBlockRegistry(actionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry contentActionBlockRegistry = new ActionBlockRegistry(contentActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry floatingActionBlockRegistry = new ActionBlockRegistry(floatingActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry floatingContentActionBlockRegistry = new ActionBlockRegistry(floatingContentActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final ActionBlockRegistry floatingChromeActionBlockRegistry = new ActionBlockRegistry(floatingChromeActionBlocks, COLOR_BLOCK, COLOR_BLOCK_HOVER, COLOR_TEXT);
    private final List<IntSliderBlock> sliderBlocks = new ArrayList<>();
    private final List<EditBox> providerEditorFields = new ArrayList<>();
    private final List<EditBox> floatingEditorFields = new ArrayList<>();
    private IntSliderBlock draggingSlider;
    private long sliderAnimationLastNanos = System.nanoTime();

    private ConfigSection selectedSection;
    private int selectedProviderIndex;
    private String selectedProviderId = "";
    private boolean providerApiKeyVisible = true;
    private String providerSearchQuery = "";
    private RouteSlot routeDropdownSlot;

    private EditBox providerSearchField;
    private EditBox addProviderNameField;
    private EditBox modelSettingsField;
    private EditBox modelTemperatureChatField;

    private boolean addProviderModalOpen;
    private String addProviderNameDraft = "";
    private ApiProviderType addProviderTypeDraft = ApiProviderType.OPENAI_COMPAT;
    private boolean addProviderTypeDropdownOpen;

    private boolean modelSettingsModalOpen;
    private String modelSettingsProviderId = "";
    private String modelSettingsOriginalId = "";
    private String modelSettingsDraft = "";
    private String modelSettingsChatTemperatureDraft = "";
    private String modelSettingsItemTemperatureDraft = "";
    private String modelSettingsScoreboardTemperatureDraft = "";
    private String modelSettingsOtherTranslationsTemperatureDraft = "";
    private String modelSettingsWynntilsTaskTrackerTemperatureDraft = "";
    private String modelSettingsWynnNpcDialogueTemperatureDraft = "";
    private String modelSettingsChatTemperatureBackup = "";
    private String modelSettingsItemTemperatureBackup = "";
    private String modelSettingsScoreboardTemperatureBackup = "";
    private String modelSettingsOtherTranslationsTemperatureBackup = "";
    private String modelSettingsWynntilsTaskTrackerTemperatureBackup = "";
    private String modelSettingsWynnNpcDialogueTemperatureBackup = "";
    private String modelSettingsKeepAliveDraft = "";
    private boolean modelSettingsSupportsSystemDraft;
    private String modelSettingsSystemPromptSuffixDraft = "";
    private List<CustomParameterEntry> modelSettingsCustomParametersDraft = new ArrayList<>();
    private List<CustomParameterEntry> customParametersBackup = new ArrayList<>();
    private boolean customParametersModalOpen;
    private boolean modelTemperatureModalOpen;
    private boolean dictionaryFilesModalOpen;
    private boolean resetConfirmModalOpen;
    private boolean updateNoticeModalOpen;
    private boolean unsavedChangesConfirmModalOpen;
    private ConfirmModalSupport.WarningConfirmation warningConfirmation;
    private String promptEditorProviderId = "";
    private boolean updateNoticeAutoPrompted;
    private String selectedCustomParameterPath = "";
    private EditBox customParameterNameField;
    private EditBox customParameterValueField;
    private boolean modelSettingsSetDefault;
    private final DictionaryFilesModalSupport dictionaryFilesModal =
            new DictionaryFilesModalSupport(SCROLLBAR_WIDTH, SCROLLBAR_MIN_THUMB_HEIGHT, this::rebuildActionBlocks);

    private FocusTarget pendingFocusTarget = FocusTarget.NONE;
    private final HotkeyBindingEditSupport hotkeyBindingEdit = new HotkeyBindingEditSupport(
            ModConfigScreen::t,
            this::setStatus,
            COLOR_STATUS_OK,
            COLOR_TEXT_MUTED,
            this::rebuildActionBlocks
    );
    private UiRect versionLinkRect = new UiRect(0, 0, 0, 0);
    private final ConfigContentScrollSupport contentScroll = new ConfigContentScrollSupport(
            LEFT_PANEL_WIDTH,
            TOP_BAR_HEIGHT,
            SCROLLBAR_WIDTH,
            SCROLLBAR_MIN_THUMB_HEIGHT,
            this::onContentScrollOffsetApplied,
            this::rebuildActionBlocks
    );

    private Component statusMessage = Component.empty();
    private int statusColor = COLOR_STATUS_OK;
    private long statusExpireAtMillis;
    private boolean restoreSnapshotOnClose = true;
    private final ProviderManagerActionsSupport.Runtime providerManagerRuntime = new ProviderManagerRuntime();
    private boolean setupWizardOpen;
    private boolean setupWizardPrompted;
    private boolean setupCompleted;
    private boolean forcedSetupWizard;
    private ModConfig setupEntrySnapshot;
    private final ConfigContentScrollSupport setupWizardScroll = new ConfigContentScrollSupport(
            0,
            0,
            SCROLLBAR_WIDTH,
            SCROLLBAR_MIN_THUMB_HEIGHT,
            this::rebuildActionBlocks,
            this::rebuildActionBlocks
    );
    private final SetupWizardModalSupport.Runtime setupWizardRuntime = new SetupWizardRuntime();
    private final SetupWizardModalSupport.HotkeySupport setupWizardHotkeys = new SetupWizardHotkeySupport();
    private final SetupWizardModalSupport setupWizard = new SetupWizardModalSupport(
            setupWizardScroll,
            ModConfigScreen::t,
            this::setStatus,
            COLOR_STATUS_ERROR,
            floatingContentActionBlockRegistry::add,
            floatingActionBlockRegistry::add,
            floatingChromeActionBlockRegistry::add,
            this::addFloatingToggleAction,
            (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                    addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
            (x, y, width, maxLength, initialValue, placeholder, changed, masked) ->
                    addSecretTextField(x, y, width, maxLength, initialValue, placeholder, changed, masked, false, true),
            type -> ProviderManagerActionsSupport.providerTypeDisplayName(ModConfigScreen::t, type),
            setupWizardHotkeys,
            floatingChromeActionBlockRegistry::addLive,
            setupWizardRuntime,
            SETUP_WIZARD_STYLE
    );

    public ModConfigScreen(Screen parent) {
        this(parent, ConfigScreenStateSupport.loadPersistedSelectedSection());
    }

    public ModConfigScreen(Screen parent, boolean setupWizardRequested) {
        this(parent, ConfigScreenStateSupport.loadPersistedSelectedSection());
        this.forcedSetupWizard = setupWizardRequested;
    }

    public ModConfigScreen(Screen parent, String providerId) {
        this(parent, ConfigSection.PROVIDERS);
        this.selectedProviderId = providerId == null ? "" : providerId;
        this.selectedProviderIndex = resolveSelectedProviderIndex(providerId);
    }

    private ModConfigScreen(Screen parent, ConfigSection selectedSection) {
        super(t("title"));
        this.parent = parent;
        this.originalConfigSnapshot = ConfigManager.copyCurrentConfig();
        this.originalConfigSnapshotJson = CONFIG_STATE_GSON.toJson(this.originalConfigSnapshot);
        this.providerApiKeyVisible = ProviderManagerActionsSupport.isProviderApiKeyVisible(this.originalConfigSnapshot.providerManager);
        this.modVersion = resolveCurrentVersion();
        this.repositoryUrl = resolveRepositoryUrl();
        this.selectedSection = selectedSection;
        this.setupCompleted = ConfigScreenStateSupport.loadSetupCompleted();
    }

    private int resolveSelectedProviderIndex(String providerId) {
        if (providerId == null || providerId.isBlank() || originalConfigSnapshot == null || originalConfigSnapshot.providerManager == null) {
            return 0;
        }
        List<ApiProviderProfile> providers = originalConfigSnapshot.providerManager.providers;
        for (int i = 0; i < providers.size(); i++) {
            if (providerId.equals(providers.get(i).id)) {
                return i;
            }
        }
        return 0;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable(I18N_PREFIX + key, args);
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
                    return contact.get("modrinth")
                            .orElse("");
                })
                .orElse("");
    }

    private Component versionLinkText() {
        return t("version_link", modVersion);
    }

    private UiRect resolveVersionLinkRect() {
        if (repositoryUrl.isBlank()) {
            return new UiRect(0, 0, 0, 0);
        }

        Component versionText = versionLinkText();
        int textWidth = this.font.width(versionText);
        if (textWidth <= 0) {
            return new UiRect(0, 0, 0, 0);
        }

        int minX = TOP_BAR_TITLE_X + this.font.width(this.title) + TOP_BAR_LINK_GAP;
        int x = this.width - TOP_BAR_ACTIONS_LEFT_MARGIN - TOP_BAR_LINK_GAP - textWidth;
        if (x < minX) {
            return new UiRect(0, 0, 0, 0);
        }
        return new UiRect(x, TOP_BAR_TEXT_Y, textWidth, this.font.lineHeight + 2);
    }

    private void updateVersionLinkRect() {
        versionLinkRect = resolveVersionLinkRect();
    }

    private void renderVersionLink(GuiGraphicsExtractor context, int mouseX, int mouseY, long nowMillis) {
        updateVersionLinkRect();
        if (versionLinkRect.width <= 0 || versionLinkRect.height <= 0) {
            return;
        }

        boolean hovered = versionLinkRect.contains(mouseX, mouseY);
        int color = hovered ? COLOR_TEXT_LINK_HOVER : COLOR_TEXT_LINK;
        Component versionText = versionLinkText();
        context.text(this.font, versionText, versionLinkRect.x, versionLinkRect.y, color, false);

        int underlineY = versionLinkRect.y + this.font.lineHeight + 1;
        context.fill(versionLinkRect.x, underlineY, versionLinkRect.right(), underlineY + 1, color);
    }

    private boolean hasUnsavedChanges() {
        return !originalConfigSnapshotJson.equals(CONFIG_STATE_GSON.toJson(ConfigManager.copyCurrentConfig()));
    }

    private ConfigUiModalState modalState() {
        return new ConfigUiModalState(
                addProviderModalOpen,
                modelSettingsModalOpen,
                customParametersModalOpen,
                modelTemperatureModalOpen,
                dictionaryFilesModalOpen,
                resetConfirmModalOpen,
                updateNoticeModalOpen,
                unsavedChangesConfirmModalOpen,
                isWarningConfirmationOpen(),
                setupWizardOpen
        );
    }

    private boolean isAnyModalOpen() {
        return modalState().isAnyOpen();
    }

    private boolean isInsideOpenModal(double mouseX, double mouseY) {
        return ConfigUiModalSupport.isInsideOpenModal(
                mouseX,
                mouseY,
                this.width,
                this.height,
                modalState()
        );
    }

    private void finishClose() {
        ConfigScreenStateSupport.persistScreenState(selectedSection, setupCompleted);
        if (restoreSnapshotOnClose) {
            ConfigManager.replaceConfig(originalConfigSnapshot);
        }
        restoreSnapshotOnClose = false;
        unsavedChangesConfirmModalOpen = false;

        if (this.minecraft != null) {
            MinecraftScreens.INSTANCE.open(parent);
        }
    }

    @Override
    protected void init() {
        if (!setupWizardPrompted) {
            setupWizardPrompted = true;
            if (SetupWizardSupport.shouldShowSetupWizard(setupCompleted, forcedSetupWizard, Translate_AllinOne.getConfig())) {
                openSetupWizard(forcedSetupWizard);
            }
        }
        if (!setupWizardOpen && !updateNoticeAutoPrompted && UpdateCheckManager.shouldShowConfigNotice()) {
            openUpdateNoticeModal();
        }
        rebuildActionBlocks();
    }

    @Override
    public void tick() {
        super.tick();
        if (!setupWizardOpen && !updateNoticeAutoPrompted && UpdateCheckManager.shouldShowConfigNotice()) {
            openUpdateNoticeModal();
            rebuildActionBlocks();
        }
    }

    private void openSetupWizard(boolean forced) {
        setupEntrySnapshot = ConfigManager.copyCurrentConfig();
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        setupWizard.open(forced);
        setupWizardOpen = true;
    }

    private void closeSetupWizard() {
        setupWizard.close();
        setupWizardOpen = false;
        setupEntrySnapshot = null;
    }

    private boolean completeSetupWizard() {
        applyModelCustomParameterDefaults();
        boolean saved = ConfigUiRuntimeSupport.saveConfig(
                ModConfigScreen::t,
                this::setStatus,
                COLOR_STATUS_OK,
                COLOR_STATUS_ERROR,
                (message, throwable) -> Translate_AllinOne.LOGGER.error(message, throwable)
        );
        if (!saved) {
            return false;
        }

        refreshConfigSnapshotBaseline();
        setupCompleted = true;
        ConfigScreenStateSupport.persistScreenState(selectedSection, true);
        closeSetupWizard();
        rebuildActionBlocks();
        return true;
    }

    private void skipSetupWizard() {
        if (setupEntrySnapshot != null) {
            ConfigManager.replaceConfig(setupEntrySnapshot);
        }
        setupCompleted = true;
        ConfigScreenStateSupport.persistScreenState(selectedSection, true);
        closeSetupWizard();
        setStatus(t("status.setup_skipped"), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void refreshConfigSnapshotBaseline() {
        originalConfigSnapshot = ConfigManager.copyCurrentConfig();
        originalConfigSnapshotJson = CONFIG_STATE_GSON.toJson(originalConfigSnapshot);
    }

    private void rebuildActionBlocks() {
        rebuildActionBlocks(FocusTarget.NONE);
    }

    private void rebuildActionBlocks(FocusTarget focusTarget) {
        ensureSelectedSectionVisible();
        clearWidgets();
        actionBlocks.clear();
        contentActionBlocks.clear();
        floatingActionBlocks.clear();
        floatingContentActionBlocks.clear();
        floatingChromeActionBlocks.clear();
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
        modelTemperatureChatField = null;
        customParameterNameField = null;
        customParameterValueField = null;
        pendingFocusTarget = focusTarget;
        contentScroll.updateViewport(this.width, this.height);
        contentScroll.selectSection(selectedSection);

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
                        contentScroll.switchSection(selectedSection, section);
                        hotkeyBindingEdit.clearCaptureTarget();
                        selectedSection = section;
                        addProviderTypeDropdownOpen = false;
                        routeDropdownSlot = null;
                        rebuildActionBlocks();
                    }
                },
                CHROME_ACTIONS_STYLE
        );

        addSectionSpecificActions();
        if (setupWizardOpen) {
            setupWizard.add(this.width, this.height);
        } else if (dictionaryFilesModalOpen) {
            dictionaryFilesModal.add(
                    this.width,
                    this.height,
                    ModConfigScreen::t,
                    floatingActionBlockRegistry::add,
                    DICTIONARY_FILES_MODAL_STYLE,
                    this::closeDictionaryFilesModal
            );
        } else if (updateNoticeModalOpen) {
            ConfirmModalSupport.addUpdateNotice(
                    this.width,
                    this.height,
                    ModConfigScreen::t,
                    floatingActionBlockRegistry::add,
                    CONFIRM_MODAL_STYLE,
                    this::dismissUpdateNotice
            );
        } else if (resetConfirmModalOpen) {
            ConfirmModalSupport.addResetConfirm(
                    this.width,
                    this.height,
                    ModConfigScreen::t,
                    floatingActionBlockRegistry::add,
                    CONFIRM_MODAL_STYLE,
                    this::dismissResetConfirm,
                    this::applyResetToDefaults
            );
        } else if (unsavedChangesConfirmModalOpen) {
            ConfirmModalSupport.addUnsavedChanges(
                    this.width,
                    this.height,
                    ModConfigScreen::t,
                    floatingActionBlockRegistry::add,
                    CONFIRM_MODAL_STYLE,
                    this::dismissUnsavedChanges,
                    this::saveAndClose,
                    this::discardChangesAndClose
            );
        } else if (isWarningConfirmationOpen()) {
            ConfirmModalSupport.addWarningConfirm(
                    this.width,
                    this.height,
                    ModConfigScreen::t,
                    floatingActionBlockRegistry::add,
                    CONFIRM_MODAL_STYLE,
                    this::dismissWarningConfirmation,
                    this::openPromptEditorScreen
            );
        }
        ConfigUiFocusSupport.applyPendingFocus(
                this,
                pendingFocusTarget,
                providerSearchField,
                addProviderNameField,
                modelSettingsField,
                customParameterNameField,
                modelTemperatureChatField
        );
        pendingFocusTarget = FocusTarget.NONE;
    }

    private void ensureSelectedSectionVisible() {
        if (isSectionVisible(selectedSection)) {
            return;
        }

        selectedSection = firstVisibleSection();
        hotkeyBindingEdit.clearCaptureTarget();
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
        return ConfigSection.PROVIDERS;
    }

    private boolean isSectionVisible(ConfigSection section) {
        return section != ConfigSection.DEBUG || shouldShowDebugSection();
    }

    private boolean shouldShowDebugSection() {
        return this.minecraft != null
                && this.minecraft.player != null
                && DEBUG_SECTION_ACCOUNT_UUID.equalsIgnoreCase(this.minecraft.player.getUUID().toString());
    }

    private void addSectionSpecificActions() {
        ModConfig config = Translate_AllinOne.getConfig();
        config.providerManager.ensureDefaults();
        providerApiKeyVisible = ProviderManagerActionsSupport.isProviderApiKeyVisible(config.providerManager);

        int x = LEFT_PANEL_WIDTH + 20;
        int y = TOP_BAR_HEIGHT + 28;
        int width = contentScroll.layoutWidth();
        int contentStartY = y - contentScroll.currentRenderOffset();

        int contentBottomY = ConfigSectionContentSupport.render(
                selectedSection,
                config,
                x,
                contentStartY,
                width,
                contentScroll.viewport().height,
                ModConfigScreen::t,
                this::addGroupBox,
                this::addToggleAction,
                this::addIntSliderAction,
                this::addActionRow,
                this::addTextInputRow,
                hotkeyBindingEdit::bindingLabel,
                hotkeyBindingEdit::startCapture,
                hotkeyBindingEdit::clearBinding,
                hotkeyBindingEdit::cycleMode,
                this::cycleExternalScoreboardMode,
                this::cycleOriginalDisplayMode,
                this::cycleDebugLevel,
                this::setScreenTranslationEnabled,
                this::openDictionaryFilesModal,
                this::openDictionaryDirectory,
                this::openCacheDirectory,
                this::openWynnDialogueHudEditor,
                this::addRouteModelSelector,
                this::addProviderManagerActions
        );

        contentScroll.updateBounds(contentBottomY);
    }

    private void onContentScrollOffsetApplied() {
        draggingSlider = null;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        rebuildActionBlocks();
    }

    private void setProviderApiKeyVisible(boolean visible) {
        providerApiKeyVisible = visible;
        ProviderManagerActionsSupport.storeProviderApiKeyVisible(visible);
    }

    private void addRouteModelSelector(ProviderManagerConfig manager, RouteSlot routeSlot, int x, int y, int width) {
        RouteModelSelectorSectionSupport.addSelector(
                manager,
                routeSlot,
                x,
                y,
                width,
                contentScroll.viewport(),
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
        return ProviderManagerActionsSupport.render(
                providerManager,
                x,
                y,
                width,
                viewportHeight,
                providerSearchQuery,
                selectedProviderId,
                selectedProviderIndex,
                providerApiKeyVisible,
                PROVIDER_LIST_STYLE,
                PROVIDER_DETAIL_STYLE,
                ModConfigScreen::t,
                this::addToggleAction,
                this::addGroupBox,
                contentActionBlockRegistry::add,
                this::addTextField,
                this::addSecretTextField,
                providerManagerRuntime
        );
    }

    private EditBox addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            boolean editable
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, value -> true, editable, false);
    }

    private EditBox addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, textPredicate, editable, false);
    }

    private EditBox addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            boolean editable,
            boolean floating
    ) {
        return addTextField(x, y, width, maxLength, initialValue, placeholder, changed, value -> true, editable, floating);
    }

    private EditBox addTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable,
            boolean floating
    ) {
        return ConfigUiTextFieldSupport.create(
                this.font,
                this::addRenderableWidget,
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

    private void addSecretTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            boolean masked,
            boolean clearOnFirstEdit
    ) {
        addSecretTextField(x, y, width, maxLength, initialValue, placeholder, changed, masked, clearOnFirstEdit, false);
    }

    private EditBox addSecretTextField(
            int x,
            int y,
            int width,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            boolean masked,
            boolean clearOnFirstEdit,
            boolean floating
    ) {
        return ConfigUiTextFieldSupport.createSecret(
                this.font,
                this::addRenderableWidget,
                providerEditorFields,
                floatingEditorFields,
                x,
                y,
                width,
                maxLength,
                initialValue,
                placeholder,
                changed,
                masked,
                clearOnFirstEdit,
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
                providerType -> ProviderManagerActionsSupport.providerTypeDisplayName(ModConfigScreen::t, providerType),
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
        modelSettingsChatTemperatureDraft = draft.chatTemperatureDraft();
        modelSettingsItemTemperatureDraft = draft.itemTemperatureDraft();
        modelSettingsScoreboardTemperatureDraft = draft.scoreboardTemperatureDraft();
        modelSettingsOtherTranslationsTemperatureDraft = draft.otherTranslationsTemperatureDraft();
        modelSettingsWynntilsTaskTrackerTemperatureDraft = draft.wynntilsTaskTrackerTemperatureDraft();
        modelSettingsWynnNpcDialogueTemperatureDraft = draft.wynnNpcDialogueTemperatureDraft();
        modelSettingsChatTemperatureBackup = modelSettingsChatTemperatureDraft;
        modelSettingsItemTemperatureBackup = modelSettingsItemTemperatureDraft;
        modelSettingsScoreboardTemperatureBackup = modelSettingsScoreboardTemperatureDraft;
        modelSettingsOtherTranslationsTemperatureBackup = modelSettingsOtherTranslationsTemperatureDraft;
        modelSettingsWynntilsTaskTrackerTemperatureBackup = modelSettingsWynntilsTaskTrackerTemperatureDraft;
        modelSettingsWynnNpcDialogueTemperatureBackup = modelSettingsWynnNpcDialogueTemperatureDraft;
        modelSettingsKeepAliveDraft = draft.keepAliveDraft();
        modelSettingsSupportsSystemDraft = draft.supportsSystem();
        modelSettingsSystemPromptSuffixDraft = draft.systemPromptSuffixDraft();
        modelSettingsCustomParametersDraft = draft.customParametersDraft();
        customParametersBackup = draft.customParametersBackup();
        customParametersModalOpen = false;
        modelTemperatureModalOpen = false;
        selectedCustomParameterPath = "";
        modelSettingsSetDefault = draft.setDefault();
    }

    private void closeModelSettingsModal() {
        if (customParametersModalOpen) {
            closeCustomParametersModal(true);
        }
        if (modelTemperatureModalOpen) {
            closeModelTemperatureModal(true);
        }

        ModelSettingsDraftSupport.Draft empty = ModelSettingsDraftSupport.empty();
        modelSettingsModalOpen = false;
        modelSettingsProviderId = empty.providerId();
        modelSettingsOriginalId = empty.originalModelId();
        modelSettingsDraft = empty.modelIdDraft();
        modelSettingsChatTemperatureDraft = empty.chatTemperatureDraft();
        modelSettingsItemTemperatureDraft = empty.itemTemperatureDraft();
        modelSettingsScoreboardTemperatureDraft = empty.scoreboardTemperatureDraft();
        modelSettingsOtherTranslationsTemperatureDraft = empty.otherTranslationsTemperatureDraft();
        modelSettingsWynntilsTaskTrackerTemperatureDraft = empty.wynntilsTaskTrackerTemperatureDraft();
        modelSettingsWynnNpcDialogueTemperatureDraft = empty.wynnNpcDialogueTemperatureDraft();
        modelSettingsChatTemperatureBackup = "";
        modelSettingsItemTemperatureBackup = "";
        modelSettingsScoreboardTemperatureBackup = "";
        modelSettingsOtherTranslationsTemperatureBackup = "";
        modelSettingsWynntilsTaskTrackerTemperatureBackup = "";
        modelSettingsWynnNpcDialogueTemperatureBackup = "";
        modelSettingsKeepAliveDraft = empty.keepAliveDraft();
        modelSettingsSupportsSystemDraft = empty.supportsSystem();
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

    private void openModelTemperatureModal() {
        modelTemperatureModalOpen = true;
        modelSettingsChatTemperatureBackup = modelSettingsChatTemperatureDraft;
        modelSettingsItemTemperatureBackup = modelSettingsItemTemperatureDraft;
        modelSettingsScoreboardTemperatureBackup = modelSettingsScoreboardTemperatureDraft;
        modelSettingsOtherTranslationsTemperatureBackup = modelSettingsOtherTranslationsTemperatureDraft;
        modelSettingsWynntilsTaskTrackerTemperatureBackup = modelSettingsWynntilsTaskTrackerTemperatureDraft;
        modelSettingsWynnNpcDialogueTemperatureBackup = modelSettingsWynnNpcDialogueTemperatureDraft;
    }

    private void closeModelTemperatureModal(boolean keepChanges) {
        if (!keepChanges) {
            modelSettingsChatTemperatureDraft = modelSettingsChatTemperatureBackup;
            modelSettingsItemTemperatureDraft = modelSettingsItemTemperatureBackup;
            modelSettingsScoreboardTemperatureDraft = modelSettingsScoreboardTemperatureBackup;
            modelSettingsOtherTranslationsTemperatureDraft = modelSettingsOtherTranslationsTemperatureBackup;
            modelSettingsWynntilsTaskTrackerTemperatureDraft = modelSettingsWynntilsTaskTrackerTemperatureBackup;
            modelSettingsWynnNpcDialogueTemperatureDraft = modelSettingsWynnNpcDialogueTemperatureBackup;
        }
        modelTemperatureModalOpen = false;
    }

    private void applyModelTemperatureModal() {
        if (ModelSettingsValueSupport.parseTemperatureInput(modelSettingsChatTemperatureDraft) == null
                || ModelSettingsValueSupport.parseTemperatureInput(modelSettingsItemTemperatureDraft) == null
                || ModelSettingsValueSupport.parseTemperatureInput(modelSettingsScoreboardTemperatureDraft) == null
                || ModelSettingsValueSupport.parseTemperatureInput(modelSettingsOtherTranslationsTemperatureDraft) == null
                || ModelSettingsValueSupport.parseTemperatureInput(modelSettingsWynntilsTaskTrackerTemperatureDraft) == null
                || ModelSettingsValueSupport.parseTemperatureInput(modelSettingsWynnNpcDialogueTemperatureDraft) == null) {
            setStatus(t("error.temperature_invalid"), COLOR_STATUS_ERROR);
            return;
        }

        closeModelTemperatureModal(true);
        rebuildActionBlocks(FocusTarget.MODEL_NAME);
    }

    private void openPromptEditorWarning(ApiProviderProfile profile) {
        warningConfirmation = ConfirmModalSupport.WarningConfirmation.PROMPT_EDITOR;
        promptEditorProviderId = profile.id;
    }

    private void setScreenTranslationEnabled(boolean enabled) {
        Translate_AllinOne.getConfig().otherTranslations.enabled_screen_translation = enabled;
    }

    private boolean isWarningConfirmationOpen() {
        return warningConfirmation != null;
    }

    private void closeWarningConfirmation() {
        warningConfirmation = null;
        promptEditorProviderId = "";
    }

    private void dismissWarningConfirmation() {
        closeWarningConfirmation();
        rebuildActionBlocks();
    }

    private void openPromptEditorScreen() {
        warningConfirmation = null;
        if (this.minecraft != null) {
            MinecraftScreens.INSTANCE.open(new PromptEditorScreen(this, promptEditorProviderId));
        }
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

    private void addModelTemperatureModal() {
        ModelTemperatureModalSupport.Fields fields = ModelTemperatureModalSupport.render(
                this.width,
                this.height,
                modelSettingsChatTemperatureDraft,
                modelSettingsItemTemperatureDraft,
                modelSettingsScoreboardTemperatureDraft,
                modelSettingsOtherTranslationsTemperatureDraft,
                modelSettingsWynntilsTaskTrackerTemperatureDraft,
                modelSettingsWynnNpcDialogueTemperatureDraft,
                ModConfigScreen::t,
                floatingActionBlockRegistry::add,
                (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                        addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
                value -> modelSettingsChatTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsItemTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsScoreboardTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsOtherTranslationsTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsWynntilsTaskTrackerTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsWynnNpcDialogueTemperatureDraft = ProviderProfileSupport.sanitizeText(value),
                () -> {
                    closeModelTemperatureModal(false);
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
                this::applyModelTemperatureModal,
                MODEL_SETTINGS_MODAL_STYLE
        );
        modelTemperatureChatField = fields.chatField();
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
                modelSettingsKeepAliveDraft,
                modelSettingsSystemPromptSuffixDraft,
                CustomParameterTreeSupport.countEntries(modelSettingsCustomParametersDraft),
                modelSettingsSupportsSystemDraft,
                modelSettingsSetDefault,
                ModConfigScreen::t,
                floatingActionBlockRegistry::add,
                this::addFloatingCheckbox,
                (x, y, width, maxLength, initialValue, placeholder, changed, editable) ->
                        addTextField(x, y, width, maxLength, initialValue, placeholder, changed, editable, true),
                value -> modelSettingsDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsKeepAliveDraft = ProviderProfileSupport.sanitizeText(value),
                value -> modelSettingsSystemPromptSuffixDraft = ProviderProfileSupport.sanitizeText(value),
                () -> {
                    openModelTemperatureModal();
                    rebuildActionBlocks(FocusTarget.MODEL_TEMPERATURE_CHAT);
                },
                () -> {
                    openCustomParametersModal();
                    rebuildActionBlocks(FocusTarget.CUSTOM_PARAMETER_NAME);
                },
                value -> {
                    modelSettingsSupportsSystemDraft = value;
                    rebuildActionBlocks(FocusTarget.MODEL_NAME);
                },
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
                modelSettingsChatTemperatureDraft,
                modelSettingsItemTemperatureDraft,
                modelSettingsScoreboardTemperatureDraft,
                modelSettingsOtherTranslationsTemperatureDraft,
                modelSettingsWynntilsTaskTrackerTemperatureDraft,
                modelSettingsWynnNpcDialogueTemperatureDraft,
                modelSettingsKeepAliveDraft,
                modelSettingsSupportsSystemDraft,
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

    private void addToggleAction(
            int x,
            int y,
            int width,
            Component label,
            BooleanSupplier getter,
            Consumer<Boolean> setter,
            Component tooltip,
            boolean defaultValue
    ) {
        int controlWidth = optionControlWidth(width);
        int controlHeight = 20;
        checkboxBlocks.add(new CheckboxBlock(
                x,
                y,
                controlWidth,
                controlHeight,
                () -> label,
                getter.getAsBoolean(),
                setter,
                CHECKBOX_STYLE,
                tooltip
        ));
        addResetSlot(
                x + controlWidth + RESET_SLOT_GAP,
                y,
                controlHeight,
                () -> getter.getAsBoolean() != defaultValue,
                () -> setter.accept(defaultValue)
        );
    }

    private void addResetSlot(int x, int y, int height, BooleanSupplier modified, Runnable resetAction) {
        contentActionBlockRegistry.add(
                x,
                y,
                RESET_SLOT_WIDTH,
                height,
                () -> Component.empty(),
                () -> {
                    resetAction.run();
                    rebuildActionBlocks();
                },
                COLOR_BLOCK,
                COLOR_BLOCK_HOVER,
                COLOR_TEXT,
                true,
                t("desc.reset_option"),
                modified,
                true
        );
    }

    private static int optionControlWidth(int width) {
        return Math.max(40, width - RESET_SLOT_WIDTH - RESET_SLOT_GAP);
    }

    private void addGroupBox(int x, int y, int width, int height, Component title) {
        groupBoxes.add(new GroupBox(x, y, width, height, title, GROUP_BOX_STYLE));
    }

    private void addActionRow(
            int x,
            int y,
            int width,
            Component label,
            Runnable action,
            Component tooltip,
            BooleanSupplier enabled,
            Runnable resetAction,
            BooleanSupplier modified
    ) {
        if (resetAction == null) {
            contentActionBlockRegistry.add(x, y, width, 20, label, action, tooltip, enabled);
            return;
        }

        int controlWidth = optionControlWidth(width);
        int controlHeight = 20;
        contentActionBlockRegistry.add(x, y, controlWidth, controlHeight, label, action, tooltip, enabled);
        addResetSlot(x + controlWidth + RESET_SLOT_GAP, y, controlHeight, modified, resetAction);
    }

    private void addActionRow(int x, int y, int width, Component label, Runnable action, Component tooltip, BooleanSupplier enabled) {
        addActionRow(x, y, width, label, action, tooltip, enabled, null, null);
    }

    private void addActionRow(int x, int y, int width, Component label, Runnable action, Component tooltip) {
        addActionRow(x, y, width, label, action, tooltip, () -> true, null, null);
    }

    private void addTextInputRow(
            int x,
            int y,
            int width,
            Component label,
            int maxLength,
            String initialValue,
            Component placeholder,
            Consumer<String> changed,
            Predicate<String> textPredicate,
            boolean editable,
            Component tooltip,
            String defaultValue
    ) {
        if (!editable) {
            addStaticTextRow(x, y, width, label, Component.literal(initialValue == null ? "" : initialValue));
            return;
        }

        boolean hasResetSlot = defaultValue != null;
        int controlWidth = hasResetSlot ? optionControlWidth(width) : width;
        int labelWidth = responsiveLabelWidth(controlWidth);
        int fieldGap = 6;
        int fieldX = x + labelWidth + fieldGap;
        int fieldWidth = Math.max(72, controlWidth - labelWidth - fieldGap);
        int controlHeight = 20;

        contentActionBlockRegistry.add(x, y, labelWidth, controlHeight, label, () -> {
        }, tooltip);
        EditBox field = addTextField(
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
        if (hasResetSlot) {
            addResetSlot(
                    x + controlWidth + RESET_SLOT_GAP,
                    y,
                    controlHeight,
                    () -> !field.getValue().equals(defaultValue),
                    () -> field.setValue(defaultValue)
            );
        }
    }

    private void addStaticTextRow(int x, int y, int width, Component label, Component value) {
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

    private void cycleExternalScoreboardMode() {
        ScoreboardConfig scoreboard = Translate_AllinOne.getConfig().scoreboardTranslate;
        if (scoreboard.external_custom_scoreboard_mode == null) {
            scoreboard.external_custom_scoreboard_mode =
                    ScoreboardConfig.ExternalCustomScoreboardMode.DISABLED;
        }
        ScoreboardConfig.ExternalCustomScoreboardMode[] modes =
                ScoreboardConfig.ExternalCustomScoreboardMode.values();
        scoreboard.external_custom_scoreboard_mode =
                modes[(scoreboard.external_custom_scoreboard_mode.ordinal() + 1) % modes.length];
        setStatus(
                t(
                        "status.external_scoreboard_mode_changed",
                        externalScoreboardModeLabel(scoreboard.external_custom_scoreboard_mode)
                ),
                COLOR_STATUS_OK
        );
        rebuildActionBlocks();
    }

    private void cycleDebugLevel(ConfigSectionContentSupport.DebugDimension dimension) {
        DebugConfig debug = Translate_AllinOne.getConfig().debug;
        if (debug == null) {
            debug = new DebugConfig();
            Translate_AllinOne.getConfig().debug = debug;
        }
        LogLevel next = ConfigSectionContentSupport.nextLevel(ConfigSectionContentSupport.levelOf(debug, dimension));
        ConfigSectionContentSupport.setLevel(debug, dimension, next);
        ComponentTranslationDebugLogger.refresh(Translate_AllinOne.getConfig());
        setStatus(t("status.debug_level_changed", t(dimension.nameKey()), t(ConfigSectionContentSupport.levelStateKey(next))), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private void cycleOriginalDisplayMode() {
        ChatTranslateConfig.ChatOutputTranslateConfig output = Translate_AllinOne.getConfig().chatTranslate.output;
        if (ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_HOVER.equals(output.original_display_mode)) {
            output.original_display_mode = ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_SUBTITLE;
        } else if (ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_SUBTITLE.equals(output.original_display_mode)) {
            output.original_display_mode = ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_OFF;
        } else {
            output.original_display_mode = ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_HOVER;
        }
        setStatus(t("status.original_display_mode_changed", originalDisplayModeLabel(output.original_display_mode)), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    private Component originalDisplayModeLabel(String mode) {
        if (ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_HOVER.equals(mode)) {
            return t("state.original_display_hover");
        }
        if (ChatTranslateConfig.ChatOutputTranslateConfig.ORIGINAL_DISPLAY_SUBTITLE.equals(mode)) {
            return t("state.original_display_subtitle");
        }
        return t("state.original_display_off");
    }

    private Component externalScoreboardModeLabel(ScoreboardConfig.ExternalCustomScoreboardMode mode) {
        return switch (mode) {
            case DISABLED -> t("state.external_scoreboard_disabled");
            case AUTO -> t("state.external_scoreboard_auto");
            case FORCE -> t("state.external_scoreboard_force");
        };
    }

    private void addFloatingCheckbox(
            int x,
            int y,
            int width,
            int height,
            Supplier<Component> labelSupplier,
            BooleanSupplier checked,
            Consumer<Boolean> changed,
            CheckboxBlock.Style style,
            Component tooltip
    ) {
        floatingCheckboxBlocks.add(new CheckboxBlock(
                x,
                y,
                width,
                height,
                labelSupplier,
                checked.getAsBoolean(),
                changed,
                style,
                tooltip
        ));
    }

    private void addFloatingToggleAction(
            int x,
            int y,
            int width,
            Component label,
            BooleanSupplier getter,
            Consumer<Boolean> setter,
            Component tooltip,
            boolean defaultValue
    ) {
        addFloatingCheckbox(x, y, width, 20, () -> label, getter, setter, CHECKBOX_STYLE, tooltip);
    }

    private void addIntSliderAction(
            int x,
            int y,
            int width,
            Component label,
            int min,
            int max,
            IntSupplier getter,
            IntConsumer setter,
            Component tooltip,
            int defaultValue
    ) {
        int controlWidth = optionControlWidth(width);
        sliderBlocks.add(new IntSliderBlock(x, y, controlWidth, SLIDER_BLOCK_HEIGHT, label, min, max, getter, setter, font, SLIDER_STYLE, tooltip));
        addResetSlot(
                x + controlWidth + RESET_SLOT_GAP,
                y,
                SLIDER_BLOCK_HEIGHT,
                () -> getter.getAsInt() != defaultValue,
                () -> setter.accept(defaultValue)
        );
    }

    private Component resolveHoveredTooltip(double mouseX, double mouseY, boolean modalOpen) {
        if (modalOpen || routeDropdownSlot != null) {
            if (!setupWizardOpen || setupWizard.viewportRect().contains(mouseX, mouseY)) {
                for (CheckboxBlock checkbox : floatingCheckboxBlocks) {
                    if (checkbox.contains(mouseX, mouseY)) {
                        Component t = checkbox.tooltip();
                        if (t != null && !t.getString().isEmpty()) {
                            return t;
                        }
                    }
                }
                for (ActionBlock action : floatingContentActionBlocks) {
                    if (action.contains(mouseX, mouseY)) {
                        Component t = action.tooltip();
                        if (t != null && !t.getString().isEmpty()) {
                            return t;
                        }
                    }
                }
            }
            for (ActionBlock action : floatingChromeActionBlocks) {
                if (action.contains(mouseX, mouseY)) {
                    Component t = action.tooltip();
                    if (t != null && !t.getString().isEmpty()) {
                        return t;
                    }
                }
            }
            for (ActionBlock action : floatingActionBlocks) {
                if (action.contains(mouseX, mouseY)) {
                    Component t = action.tooltip();
                    if (t != null && !t.getString().isEmpty()) {
                        return t;
                    }
                }
            }
            return null;
        }

        if (!contentScroll.containsMouse(mouseX, mouseY)) {
            return null;
        } else {
            for (CheckboxBlock checkbox : checkboxBlocks) {
                if (checkbox.contains(mouseX, mouseY)) {
                    Component t = checkbox.tooltip();
                    if (t != null && !t.getString().isEmpty()) {
                        return t;
                    }
                }
            }
            for (IntSliderBlock slider : sliderBlocks) {
                if (slider.contains(mouseX, mouseY)) {
                    Component t = slider.tooltip();
                    if (t != null && !t.getString().isEmpty()) {
                        return t;
                    }
                }
            }
            for (ActionBlock action : contentActionBlocks) {
                if (action.contains(mouseX, mouseY)) {
                    Component t = action.tooltip();
                    if (t != null && !t.getString().isEmpty()) {
                        return t;
                    }
                }
            }
        }
        return null;
    }

    private void setStatus(Component message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusExpireAtMillis = System.currentTimeMillis() + 3000;
    }

    private void openCacheDirectory() {
        Path cacheDirectory = CacheBackupManager.getComponentCacheDirectory();
        try {
            Files.createDirectories(cacheDirectory);
            Util.getPlatform().openUri(cacheDirectory.toUri());
            setStatus(t("status.opened_cache_directory", Component.literal(cacheDirectory.toString())), COLOR_STATUS_OK);
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
            Util.getPlatform().openUri(dictionaryDirectory.toUri());
            setStatus(t("status.opened_dictionary_directory", Component.literal(dictionaryDirectory.toString())), COLOR_STATUS_OK);
        } catch (IOException e) {
            Translate_AllinOne.LOGGER.warn("Failed to open dictionary directory {}", dictionaryDirectory, e);
            setStatus(t("status.failed_open_dictionary_directory"), COLOR_STATUS_ERROR);
        }
    }

    private void openWynnDialogueHudEditor() {
        if (this.minecraft == null) {
            return;
        }
        MinecraftScreens.INSTANCE.open(new WynnDialogueHudEditorScreen(this));
    }

    private void openRepositoryLink() {
        if (repositoryUrl.isBlank()) {
            setStatus(t("status.failed_open_repository"), COLOR_STATUS_ERROR);
            return;
        }

        try {
            Util.getPlatform().openUri(repositoryUrl);
            setStatus(t("status.opened_repository"), COLOR_STATUS_OK);
        } catch (Exception e) {
            Translate_AllinOne.LOGGER.warn("Failed to open repository url {}", repositoryUrl, e);
            setStatus(t("status.failed_open_repository"), COLOR_STATUS_ERROR);
        }
    }

    private void saveAndClose() {
        applyModelCustomParameterDefaults();
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
        onClose();
    }

    private void discardChangesAndClose() {
        finishClose();
    }

    private void applyModelCustomParameterDefaults() {
        List<String> appliedRoutes = ModelCustomParameterDefaultsSupport.applyDefaultsToAllModels(
                ConfigManager.getConfig().providerManager
        );
        if (!appliedRoutes.isEmpty()) {
            Translate_AllinOne.LOGGER.debug("Applied thinking disabled defaults to models: {}", appliedRoutes);
        }
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
        dictionaryFilesModal.open(slot);
        dictionaryFilesModalOpen = true;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        rebuildActionBlocks();
    }

    private void closeUpdateNoticeModal() {
        updateNoticeModalOpen = false;
    }

    private void dismissUpdateNotice() {
        closeUpdateNoticeModal();
        rebuildActionBlocks();
    }

    private void closeDictionaryFilesModal(boolean keepChanges) {
        dictionaryFilesModal.close(keepChanges);
        dictionaryFilesModalOpen = false;
    }

    private void closeResetConfirmModal() {
        resetConfirmModalOpen = false;
    }

    private void dismissResetConfirm() {
        closeResetConfirmModal();
        rebuildActionBlocks();
    }

    private void openUnsavedChangesConfirmModal() {
        unsavedChangesConfirmModalOpen = true;
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
    }

    private void closeUnsavedChangesConfirmModal() {
        unsavedChangesConfirmModalOpen = false;
    }

    private void dismissUnsavedChanges() {
        closeUnsavedChangesConfirmModal();
        rebuildActionBlocks();
    }

    private void applyResetToDefaults() {
        closeResetConfirmModal();
        ConfigManager.resetToDefaults();
        hotkeyBindingEdit.clearCaptureTarget();
        selectedProviderIndex = 0;
        selectedProviderId = "";
        providerApiKeyVisible = ProviderManagerActionsSupport.isProviderApiKeyVisible(Translate_AllinOne.getConfig().providerManager);
        providerSearchQuery = "";
        routeDropdownSlot = null;
        addProviderTypeDropdownOpen = false;
        contentScroll.clearDragAndElastic();
        setStatus(t("status.config_reset_pending"), COLOR_STATUS_OK);
        rebuildActionBlocks();
    }

    @Override
    public void onClose() {
        hotkeyBindingEdit.clearCaptureTarget();
        contentScroll.stopScrollDrag();
        ConfigUiModalInteractionSupport.ModalCloseAction action =
                ConfigUiModalInteractionSupport.closeByPriority(modalState());

        switch (action) {
            case SKIP_SETUP_WIZARD -> {
                skipSetupWizard();
                return;
            }
            case CLOSE_UPDATE_NOTICE -> {
                dismissUpdateNotice();
                return;
            }
            case CLOSE_RESET_CONFIRM -> {
                dismissResetConfirm();
                return;
            }
            case CLOSE_UNSAVED_CHANGES -> {
                dismissUnsavedChanges();
                return;
            }
            case CLOSE_PROMPT_EDITOR_WARNING -> {
                dismissWarningConfirmation();
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
            case CLOSE_MODEL_TEMPERATURES -> {
                closeModelTemperatureModal(true);
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (hotkeyBindingEdit.isCapturing()) {
            hotkeyBindingEdit.captureMouse(click.button());
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        boolean modalOpen = isAnyModalOpen();

        if (click.button() != 0) {
            if (modalOpen) {
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (modalOpen) {
            draggingSlider = null;
            if (setupWizardOpen && setupWizardScroll.pressScrollbar(mouseX, mouseY)) {
                return true;
            }
            if (dictionaryFilesModalOpen && dictionaryFilesModal.pressScrollbar(mouseX, mouseY)) {
                return true;
            }
            if (!isInsideOpenModal(mouseX, mouseY)) {
                ConfigUiModalInteractionSupport.ModalCloseAction action =
                        ConfigUiModalInteractionSupport.outsideModalClickAction(modalState());
                switch (action) {
                    case CLOSE_UPDATE_NOTICE -> {
                        dismissUpdateNotice();
                        return true;
                    }
                    case CLOSE_RESET_CONFIRM -> {
                        dismissResetConfirm();
                        return true;
                    }
                    case CLOSE_UNSAVED_CHANGES -> {
                        dismissUnsavedChanges();
                        return true;
                    }
                    case CLOSE_PROMPT_EDITOR_WARNING -> {
                        dismissWarningConfirmation();
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
                    case CLOSE_MODEL_TEMPERATURES -> {
                        closeModelTemperatureModal(true);
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

            if (setupWizardOpen) {
                if (setupWizard.viewportRect().contains(mouseX, mouseY)) {
                    if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingContentActionBlocks, mouseX, mouseY)) {
                        return true;
                    }
                    if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(floatingCheckboxBlocks, mouseX, mouseY)) {
                        return true;
                    }
                }
            } else if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(floatingCheckboxBlocks, mouseX, mouseY)) {
                return true;
            }

            if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingChromeActionBlocks, mouseX, mouseY)) {
                return true;
            }

            if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingActionBlocks, mouseX, mouseY)) {
                return true;
            }

            if (setupWizardOpen && setupWizard.isTypeDropdownOpen()) {
                setupWizard.closeTypeDropdown();
                rebuildActionBlocks();
                return true;
            }

            if (!setupWizardOpen || setupWizard.viewportRect().contains(mouseX, mouseY)) {
                if (super.mouseClicked(click, doubled)) {
                    return true;
                }
            }
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(floatingActionBlocks, mouseX, mouseY)) {
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchCheckboxBlocks(floatingCheckboxBlocks, mouseX, mouseY)) {
            return true;
        }

        if (routeDropdownSlot != null) {
            routeDropdownSlot = null;
            rebuildActionBlocks();
            return true;
        }

        updateVersionLinkRect();
        if (versionLinkRect.width > 0 && versionLinkRect.height > 0 && versionLinkRect.contains(mouseX, mouseY)) {
            openRepositoryLink();
            return true;
        }

        if (click.button() == 0 && contentScroll.pressScrollbar(mouseX, mouseY)) {
            return true;
        }

        if (ConfigUiInteractionSupport.dispatchActionBlocks(actionBlocks, mouseX, mouseY)) {
            return true;
        }

        if (!contentScroll.containsMouse(mouseX, mouseY)) {
            if (addProviderTypeDropdownOpen || routeDropdownSlot != null) {
                addProviderTypeDropdownOpen = false;
                routeDropdownSlot = null;
                rebuildActionBlocks();
                return true;
            }
            return false;
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

        if (contentScroll.startContentDrag(mouseY)) {
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
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (isAnyModalOpen()) {
            draggingSlider = null;
            if (setupWizardOpen && click.button() == 0 && setupWizardScroll.dragScrollbarTo(click.y())) {
                return true;
            }
            if (dictionaryFilesModal.isDraggingScrollbar() && click.button() == 0) {
                dictionaryFilesModal.dragScrollbarTo(click.y());
                return true;
            }
            contentScroll.stopScrollDrag();
            return super.mouseDragged(click, deltaX, deltaY);
        }

        if (routeDropdownSlot != null) {
            return true;
        }

        if (draggingSlider != null && click.button() == 0) {
            draggingSlider.dragTo(click.x());
            return true;
        }

        if (click.button() == 0 && contentScroll.dragScrollbarTo(click.y())) {
            return true;
        }

        if (click.button() == 0 && contentScroll.dragContentTo(click.y())) {
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (isAnyModalOpen()) {
            draggingSlider = null;
            setupWizardScroll.stopScrollDrag();
            dictionaryFilesModal.stopScrollbarDrag();
            contentScroll.stopScrollDrag();
            return super.mouseReleased(click);
        }

        if (routeDropdownSlot != null) {
            return true;
        }

        if (draggingSlider != null && click.button() == 0) {
            draggingSlider.release();
            draggingSlider = null;
            return true;
        }

        if (click.button() == 0 && contentScroll.releaseDrags()) {
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (setupWizardOpen) {
            if (setupWizardScroll.scrollByWheel(mouseX, mouseY, verticalAmount)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (dictionaryFilesModalOpen) {
            if (verticalAmount != 0.0
                    && ConfigUiModalSupport.dictionaryFilesModalRect(this.width, this.height).contains(mouseX, mouseY)) {
                int delta = verticalAmount > 0 ? -1 : 1;
                if (dictionaryFilesModal.scrollBy(delta)) {
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (isAnyModalOpen()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (contentScroll.scrollByWheel(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (hotkeyBindingEdit.isCapturing()) {
            hotkeyBindingEdit.captureKey(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (hotkeyBindingEdit.isCapturing()) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateControlAnimations();

        long nowMillis = System.currentTimeMillis();
        boolean modalOpen = isAnyModalOpen();
        UiRect contentClip = contentScroll.clipRect(this.width, this.height);
        boolean contentMouseVisible = !modalOpen && contentScroll.containsMouse(mouseX, mouseY);
        int contentMouseX = contentMouseVisible ? mouseX : Integer.MIN_VALUE;
        int contentMouseY = contentMouseVisible ? mouseY : Integer.MIN_VALUE;
        boolean chromeMouseVisible = !modalOpen;
        int chromeMouseX = chromeMouseVisible ? mouseX : Integer.MIN_VALUE;
        int chromeMouseY = chromeMouseVisible ? mouseY : Integer.MIN_VALUE;
        ConfigUiScreenRenderSupport.renderChrome(
                context,
                this.font,
                this.width,
                this.height,
                TOP_BAR_HEIGHT,
                LEFT_PANEL_WIDTH,
                this.title,
                t("panel", t(selectedSection.translationKey())),
                selectedSection == ConfigSection.PROVIDERS,
                SCREEN_RENDER_STYLE
        );
        renderVersionLink(context, chromeMouseX, chromeMouseY, nowMillis);

        ConfigUiControlRenderer.drawActionBlocks(context, this.font, actionBlocks, chromeMouseX, chromeMouseY, COLOR_BORDER);

        ConfigUiDraw.withScissor(context, contentClip, () -> {
            ConfigUiControlRenderer.drawGroupBoxes(context, this.font, groupBoxes);
            ConfigUiControlRenderer.drawActionBlocks(context, this.font, contentActionBlocks, contentMouseX, contentMouseY, COLOR_BORDER);
            ConfigUiControlRenderer.drawStaticTextRows(context, this.font, staticTextRows);
            ConfigUiControlRenderer.drawSliderBlocks(context, sliderBlocks, contentMouseX, contentMouseY);
            ConfigUiControlRenderer.drawCheckboxBlocks(context, this.font, checkboxBlocks, contentMouseX, contentMouseY);
            if (!modalOpen) {
                super.extractRenderState(context, contentMouseX, contentMouseY, delta);
            }
        });

        if (!modalOpen && contentScroll.hasScrollableContent()) {
            contentScroll.drawScrollbar(context, mouseX, mouseY, CONTENT_SCROLL_STYLE);
        }

        ConfigUiScreenRenderSupport.renderModalOverlayAndShell(
                context,
                this.font,
                this.width,
                this.height,
                TOP_BAR_HEIGHT,
                modalState(),
                t("setup.title"),
                t("modal.add_provider.title"),
                t("modal.model.title"),
                t("custom_params.title"),
                t("modal.model.temperature.title"),
                dictionaryFilesModal.title(ModConfigScreen::t),
                t("modal.reset_confirm.title"),
                t("modal.update_notice.title"),
                t("modal.unsaved_changes.title"),
                t("modal.prompt_editor_warning_title"),
                SCREEN_RENDER_STYLE
        );

        if (setupWizardOpen) {
            setupWizard.renderContent(context, this.font, this.width, this.height);
        }

        if (updateNoticeModalOpen) {
            ConfirmModalSupport.renderUpdateNoticeMessage(context, this.font, this.width, this.height, ModConfigScreen::t, CONFIRM_MODAL_STYLE);
        } else if (resetConfirmModalOpen) {
            ConfirmModalSupport.renderResetMessage(context, this.font, this.width, this.height, ModConfigScreen::t, CONFIRM_MODAL_STYLE);
        } else if (unsavedChangesConfirmModalOpen) {
            ConfirmModalSupport.renderUnsavedChangesMessage(context, this.font, this.width, this.height, ModConfigScreen::t, CONFIRM_MODAL_STYLE);
        } else if (isWarningConfirmationOpen()) {
            ConfirmModalSupport.renderWarningMessage(context, this.font, this.width, this.height, ModConfigScreen::t, CONFIRM_MODAL_STYLE);
        }

        if (modalOpen) {
            if (setupWizardOpen) {
                ConfigUiDraw.withScissor(
                        context,
                        setupWizardScroll.clipRect(this.width, this.height),
                        () -> super.extractRenderState(context, mouseX, mouseY, delta)
                );
            } else {
                super.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
        if (setupWizardOpen) {
            ConfigUiDraw.withScissor(context, setupWizardScroll.clipRect(this.width, this.height), () -> {
                ConfigUiControlRenderer.drawCheckboxBlocks(context, this.font, floatingCheckboxBlocks, mouseX, mouseY);
                ConfigUiControlRenderer.drawActionBlocks(context, this.font, floatingContentActionBlocks, mouseX, mouseY, COLOR_BORDER);
            });
        } else {
            ConfigUiControlRenderer.drawCheckboxBlocks(context, this.font, floatingCheckboxBlocks, mouseX, mouseY);
        }
        ConfigUiControlRenderer.drawActionBlocks(context, this.font, floatingChromeActionBlocks, mouseX, mouseY, COLOR_BORDER);

        if (setupWizardOpen) {
            setupWizardScroll.drawScrollbar(context, mouseX, mouseY, CONTENT_SCROLL_STYLE);
        }

        if (routeDropdownSlot != null || (setupWizardOpen && setupWizard.isTypeDropdownOpen())) {
            context.fill(0, 0, this.width, this.height, COLOR_MODAL_OVERLAY);
        }
        ConfigUiControlRenderer.drawActionBlocks(context, this.font, floatingActionBlocks, mouseX, mouseY, COLOR_BORDER);

        if (dictionaryFilesModalOpen) {
            dictionaryFilesModal.drawScrollbar(context, mouseX, mouseY, DICTIONARY_FILES_MODAL_STYLE);
        }

        ConfigUiScreenRenderSupport.renderStatusToast(
                context,
                this.font,
                this.width,
                TOP_BAR_HEIGHT,
                statusMessage,
                statusColor,
                statusExpireAtMillis,
                nowMillis,
                0xE61A1A1A,
                0xCC3A3A3A
        );

        Component hoveredTooltip = resolveHoveredTooltip(mouseX, mouseY, modalOpen);
        ConfigUiDraw.drawTooltip(context, this.font, hoveredTooltip, mouseX, mouseY, this.width, this.height);
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

        if (contentScroll.animate(deltaSeconds)) {
            rebuildActionBlocks();
        }
        if (setupWizardOpen && setupWizardScroll.animate(deltaSeconds)) {
            rebuildActionBlocks();
        }
    }

    private final class ProviderManagerRuntime implements ProviderManagerActionsSupport.Runtime {
        @Override
        public void onSearchChanged(String value) {
            String next = ProviderProfileSupport.sanitizeText(value);
            if (!providerSearchQuery.equals(next)) {
                providerSearchQuery = next;
                rebuildActionBlocks(FocusTarget.PROVIDER_SEARCH);
            }
        }

        @Override
        public void onProviderSelected(String providerId) {
            selectedProviderId = providerId;
            modelSettingsModalOpen = false;
            rebuildActionBlocks();
        }

        @Override
        public void onOpenAddProvider() {
            openAddProviderModal();
            rebuildActionBlocks(FocusTarget.ADD_PROVIDER_NAME);
        }

        @Override
        public void onToggleProviderEnabled(ApiProviderProfile profile) {
            profile.enabled = !profile.enabled;
            rebuildActionBlocks();
        }

        @Override
        public void onDeleteProvider(ProviderManagerConfig providerManager, ApiProviderProfile profile) {
            deleteProvider(providerManager, profile);
        }

        @Override
        public void onToggleApiKeyVisible() {
            setProviderApiKeyVisible(!providerApiKeyVisible);
            rebuildActionBlocks();
        }

        @Override
        public void onTestProviderConnection(ApiProviderProfile profile) {
            ConfigUiRuntimeSupport.testProviderConnection(
                    profile,
                    ModConfigScreen::t,
                    ModConfigScreen.this::setStatus,
                    COLOR_STATUS_OK,
                    COLOR_STATUS_ERROR,
                    runnable -> {
                        if (minecraft != null) {
                            minecraft.execute(runnable);
                        } else {
                            runnable.run();
                        }
                    }
            );
        }

        @Override
        public void onSetDefaultModel(ApiProviderProfile profile, String modelId) {
            profile.model_id = modelId;
            profile.ensureModelSettings();
            setStatus(t("status.model_set_default", modelId), COLOR_STATUS_OK);
            rebuildActionBlocks();
        }

        @Override
        public void onOpenModelSettings(ApiProviderProfile profile, String modelId) {
            openModelSettingsModal(profile, modelId);
            rebuildActionBlocks(FocusTarget.MODEL_NAME);
        }

        @Override
        public void onRemoveModel(ApiProviderProfile profile, String modelId) {
            removeModel(profile, modelId);
        }

        @Override
        public void onAddModel(ApiProviderProfile profile) {
            openModelSettingsModal(profile, "");
            rebuildActionBlocks(FocusTarget.MODEL_NAME);
        }

        @Override
        public void onOpenPromptEditor(ApiProviderProfile profile) {
            openPromptEditorWarning(profile);
            rebuildActionBlocks();
        }

        @Override
        public void onToggleTranslationEnabled(boolean enabled) {
            ConfigManager.setGlobalTranslationEnabled(enabled);
            rebuildActionBlocks();
        }

        @Override
        public void onRerunSetupWizard() {
            openSetupWizard(true);
            rebuildActionBlocks();
        }

        @Override
        public void onEditorStateResolved(EditBox searchField, String providerId) {
            providerSearchField = searchField;
            selectedProviderId = providerId;
        }

        @Override
        public void onAddActiveProviderModals(ProviderManagerConfig providerManager) {
            if (addProviderModalOpen) {
                addAddProviderModal(providerManager);
            }
            if (modelSettingsModalOpen && !customParametersModalOpen && !modelTemperatureModalOpen) {
                addModelSettingsModal(providerManager);
            }
            if (customParametersModalOpen) {
                addCustomParametersModal();
            }
            if (modelTemperatureModalOpen) {
                addModelTemperatureModal();
            }
        }
    }

    private final class SetupWizardHotkeySupport implements SetupWizardModalSupport.HotkeySupport {
        @Override
        public Component modeLabel(HotkeyTarget target) {
            return hotkeyBindingEdit.modeRowLabel(target);
        }

        @Override
        public Component bindingLabel(HotkeyTarget target) {
            return hotkeyBindingEdit.bindingRowLabel(target);
        }

        @Override
        public void cycleMode(HotkeyTarget target) {
            hotkeyBindingEdit.cycleMode(target);
        }

        @Override
        public void startCapture(HotkeyTarget target) {
            hotkeyBindingEdit.startCapture(target);
        }
    }

    private final class SetupWizardRuntime implements SetupWizardModalSupport.Runtime {
        @Override
        public void onRebuild() {
            rebuildActionBlocks();
        }

        @Override
        public void onRequestFocus(EditBox field) {
            setFocused(field);
            field.setFocused(true);
        }

        @Override
        public void onProviderLanded(ApiProviderProfile profile) {
            selectedProviderId = profile.id;
            selectedProviderIndex = resolveSelectedProviderIndex(profile.id);
        }

        @Override
        public void onTestConnection(ApiProviderProfile probe, Consumer<Boolean> onResult) {
            ConfigUiRuntimeSupport.testProviderConnection(
                    probe,
                    ModConfigScreen::t,
                    ModConfigScreen.this::setStatus,
                    COLOR_STATUS_OK,
                    COLOR_STATUS_ERROR,
                    runnable -> {
                        if (minecraft != null) {
                            minecraft.execute(runnable);
                        } else {
                            runnable.run();
                        }
                    },
                    onResult
            );
        }

        @Override
        public void onSetupCompleted() {
            completeSetupWizard();
        }

        @Override
        public void onSetupSkipped() {
            skipSetupWizard();
        }
    }

}
