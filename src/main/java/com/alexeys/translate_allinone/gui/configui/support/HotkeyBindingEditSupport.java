package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.gui.configui.model.HotkeyTarget;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.StatusSetter;
import com.alexeys.translate_allinone.gui.configui.support.ConfigUiRuntimeSupport.Translator;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.alexeys.translate_allinone.utils.config.pojos.WynnCraftConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class HotkeyBindingEditSupport {
    private final Translator translator;
    private final StatusSetter statusSetter;
    private final int statusOkColor;
    private final int statusMutedColor;
    private final Runnable onStateChanged;

    private HotkeyTarget captureTarget;

    public HotkeyBindingEditSupport(
            Translator translator,
            StatusSetter statusSetter,
            int statusOkColor,
            int statusMutedColor,
            Runnable onStateChanged
    ) {
        this.translator = translator;
        this.statusSetter = statusSetter;
        this.statusOkColor = statusOkColor;
        this.statusMutedColor = statusMutedColor;
        this.onStateChanged = onStateChanged;
    }

    public boolean isCapturing() {
        return captureTarget != null;
    }

    public void clearCaptureTarget() {
        captureTarget = null;
    }

    public void captureMouse(int button) {
        if (captureTarget == null || captureTarget == HotkeyTarget.CHAT_INPUT) {
            return;
        }
        InputBindingConfig captured = KeybindingManager.captureMouseBinding(button);
        if (captured != null) {
            applyBinding(captured);
        }
    }

    public void captureKey(KeyEvent input) {
        if (captureTarget == null) {
            return;
        }
        if (KeybindingManager.isEscape(input)) {
            cancelCapture();
            return;
        }
        InputBindingConfig captured = KeybindingManager.captureKeyboardBinding(input);
        if (captured != null) {
            applyBinding(captured);
        }
    }

    public Component bindingLabel(HotkeyTarget target, InputBindingConfig binding) {
        if (captureTarget == target) {
            return bindingLabelText(target, translator.t("state.hotkey_listening"));
        }

        if (!KeybindingManager.isBound(binding)) {
            return bindingLabelText(target, translator.t("state.hotkey_unbound"));
        }

        return bindingLabelText(target, Component.literal(KeybindingManager.displayName(binding)));
    }

    public void startCapture(HotkeyTarget target) {
        captureTarget = target;
        statusSetter.set(translator.t("status.hotkey_listening", sectionLabel(target)), statusOkColor);
        onStateChanged.run();
    }

    public void clearBinding(HotkeyTarget target) {
        if (target == HotkeyTarget.ITEM) {
            ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(Translate_AllinOne.getConfig());
            if (keybinding.binding == null) {
                keybinding.binding = new InputBindingConfig();
            }
            if (keybinding.refreshBinding == null) {
                keybinding.refreshBinding = new InputBindingConfig();
            }

            if (captureTarget == HotkeyTarget.ITEM
                    || captureTarget == HotkeyTarget.ITEM_REFRESH) {
                captureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            statusSetter.set(translator.t("status.hotkey_cleared", sectionLabel(target)), statusOkColor);
            onStateChanged.run();
            return;
        }

        if (target == HotkeyTarget.OTHER_TRANSLATIONS) {
            OtherTranslationsConfig.KeybindingConfig keybinding = ensureOtherTranslationsKeybinding(Translate_AllinOne.getConfig());
            if (captureTarget == HotkeyTarget.OTHER_TRANSLATIONS
                    || captureTarget == HotkeyTarget.OTHER_TRANSLATIONS_REFRESH) {
                captureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            statusSetter.set(translator.t("status.hotkey_cleared", sectionLabel(target)), statusOkColor);
            onStateChanged.run();
            return;
        }

        if (target == HotkeyTarget.WYNNTILS_TASK_TRACKER) {
            WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(Translate_AllinOne.getConfig());
            if (keybinding.binding == null) {
                keybinding.binding = new InputBindingConfig();
            }
            if (keybinding.refreshBinding == null) {
                keybinding.refreshBinding = new InputBindingConfig();
            }

            if (captureTarget == HotkeyTarget.WYNNTILS_TASK_TRACKER
                    || captureTarget == HotkeyTarget.WYNNTILS_TASK_TRACKER_REFRESH) {
                captureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            statusSetter.set(translator.t("status.hotkey_cleared", sectionLabel(target)), statusOkColor);
            onStateChanged.run();
            return;
        }

        if (target == HotkeyTarget.SCOREBOARD) {
            ScoreboardConfig.KeybindingConfig keybinding = ensureScoreboardKeybinding(Translate_AllinOne.getConfig());
            if (keybinding.binding == null) {
                keybinding.binding = new InputBindingConfig();
            }
            if (keybinding.refreshBinding == null) {
                keybinding.refreshBinding = new InputBindingConfig();
            }

            if (captureTarget == HotkeyTarget.SCOREBOARD
                    || captureTarget == HotkeyTarget.SCOREBOARD_REFRESH) {
                captureTarget = null;
            }

            KeybindingManager.clear(keybinding.binding);
            KeybindingManager.clear(keybinding.refreshBinding);
            statusSetter.set(translator.t("status.hotkey_cleared", sectionLabel(target)), statusOkColor);
            onStateChanged.run();
            return;
        }

        InputBindingConfig binding = ensureBinding(target);
        if (binding == null) {
            return;
        }

        captureTarget = captureTarget == target ? null : captureTarget;
        KeybindingManager.clear(binding);
        statusSetter.set(translator.t("status.hotkey_cleared", sectionLabel(target)), statusOkColor);
        onStateChanged.run();
    }

    public void cycleMode(HotkeyTarget target) {
        ModConfig config = Translate_AllinOne.getConfig();
        switch (target) {
            case ITEM -> {
                ItemTranslateConfig.KeybindingConfig keybinding = ensureItemKeybinding(config);
                ItemTranslateConfig.KeybindingMode[] modes = ItemTranslateConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                statusSetter.set(translator.t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), statusOkColor);
            }
            case SCOREBOARD -> {
                ScoreboardConfig.KeybindingConfig keybinding = ensureScoreboardKeybinding(config);
                ScoreboardConfig.KeybindingMode[] modes = ScoreboardConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                statusSetter.set(translator.t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), statusOkColor);
            }
            case OTHER_TRANSLATIONS -> {
                OtherTranslationsConfig.KeybindingConfig keybinding = ensureOtherTranslationsKeybinding(config);
                OtherTranslationsConfig.KeybindingMode[] modes = OtherTranslationsConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                statusSetter.set(translator.t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), statusOkColor);
            }
            case WYNNTILS_TASK_TRACKER -> {
                WynnCraftConfig.KeybindingConfig keybinding = ensureWynntilsTaskTrackerKeybinding(config);
                WynnCraftConfig.KeybindingMode[] modes = WynnCraftConfig.KeybindingMode.values();
                keybinding.mode = modes[(keybinding.mode.ordinal() + 1) % modes.length];
                statusSetter.set(translator.t("status.hotkey_mode_changed", sectionLabel(target), modeLabel(keybinding.mode.name())), statusOkColor);
            }
            default -> {
                return;
            }
        }
        onStateChanged.run();
    }

    private void applyBinding(InputBindingConfig captured) {
        if (captureTarget == null || captured == null) {
            return;
        }

        HotkeyTarget target = captureTarget;
        InputBindingConfig binding = ensureBinding(target);
        KeybindingManager.apply(binding, captured);
        captureTarget = null;
        statusSetter.set(translator.t("status.hotkey_bound", sectionLabel(target), Component.literal(KeybindingManager.displayName(binding))), statusOkColor);
        onStateChanged.run();
    }

    private void cancelCapture() {
        if (captureTarget == null) {
            return;
        }
        captureTarget = null;
        statusSetter.set(translator.t("status.hotkey_capture_cancelled"), statusMutedColor);
        onStateChanged.run();
    }

    private InputBindingConfig ensureBinding(HotkeyTarget target) {
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
            case SCOREBOARD_REFRESH -> {
                ScoreboardConfig.KeybindingConfig keybinding = ensureScoreboardKeybinding(config);
                if (keybinding.refreshBinding == null) {
                    keybinding.refreshBinding = new InputBindingConfig();
                }
                yield keybinding.refreshBinding;
            }
            case OTHER_TRANSLATIONS -> {
                OtherTranslationsConfig.KeybindingConfig keybinding = ensureOtherTranslationsKeybinding(config);
                yield keybinding.binding;
            }
            case OTHER_TRANSLATIONS_REFRESH -> {
                OtherTranslationsConfig.KeybindingConfig keybinding = ensureOtherTranslationsKeybinding(config);
                yield keybinding.refreshBinding;
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
        if (config.scoreboardTranslate.keybinding.refreshBinding == null) {
            config.scoreboardTranslate.keybinding.refreshBinding = new InputBindingConfig();
        }
        return config.scoreboardTranslate.keybinding;
    }

    private OtherTranslationsConfig.KeybindingConfig ensureOtherTranslationsKeybinding(ModConfig config) {
        if (config.otherTranslations == null) {
            config.otherTranslations = new OtherTranslationsConfig();
        }
        if (config.otherTranslations.keybinding == null) {
            config.otherTranslations.keybinding = new OtherTranslationsConfig.KeybindingConfig();
        }
        if (config.otherTranslations.keybinding.binding == null) {
            config.otherTranslations.keybinding.binding = new InputBindingConfig();
        }
        if (config.otherTranslations.keybinding.refreshBinding == null) {
            config.otherTranslations.keybinding.refreshBinding = new InputBindingConfig();
        }
        return config.otherTranslations.keybinding;
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

    private Component sectionLabel(HotkeyTarget target) {
        return switch (target) {
            case CHAT_INPUT -> translator.t("section.chat_input");
            case ITEM -> translator.t("section.item");
            case ITEM_REFRESH -> translator.t("section.item");
            case SCOREBOARD -> translator.t("section.scoreboard");
            case SCOREBOARD_REFRESH -> translator.t("section.scoreboard");
            case OTHER_TRANSLATIONS -> translator.t("section.other_translations");
            case OTHER_TRANSLATIONS_REFRESH -> translator.t("section.other_translations");
            case WYNNTILS_TASK_TRACKER -> translator.t("section.wynncraft");
            case WYNNTILS_TASK_TRACKER_REFRESH -> translator.t("section.wynncraft");
        };
    }

    private Component bindingLabelText(HotkeyTarget target, Component bindingLabel) {
        return switch (target) {
            case ITEM_REFRESH -> translator.t("label.item_refresh_hotkey_binding", bindingLabel);
            case SCOREBOARD_REFRESH -> translator.t("label.scoreboard_refresh_hotkey_binding", bindingLabel);
            case OTHER_TRANSLATIONS_REFRESH -> translator.t("label.other_translations_refresh_hotkey_binding", bindingLabel);
            case WYNNTILS_TASK_TRACKER_REFRESH -> translator.t("label.item_refresh_hotkey_binding", bindingLabel);
            default -> translator.t("label.hotkey_binding", bindingLabel);
        };
    }

    private Component modeLabel(String modeName) {
        return switch (modeName) {
            case "HOLD_TO_TRANSLATE" -> translator.t("state.hold_to_translate");
            case "HOLD_TO_SEE_ORIGINAL" -> translator.t("state.hold_to_see_original");
            default -> translator.t("state.disabled");
        };
    }

}
