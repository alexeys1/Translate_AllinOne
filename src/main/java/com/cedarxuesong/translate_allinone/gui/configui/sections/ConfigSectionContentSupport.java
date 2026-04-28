package com.cedarxuesong.translate_allinone.gui.configui.sections;

import com.cedarxuesong.translate_allinone.gui.configui.model.ConfigSection;
import com.cedarxuesong.translate_allinone.gui.configui.model.RouteSlot;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.cache.ScoreboardTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.WynnDialogueTextCache;
import com.cedarxuesong.translate_allinone.utils.cache.WynntilsTaskTrackerTextCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.DebugConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.InputBindingConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ScoreboardConfig;
import com.cedarxuesong.translate_allinone.utils.config.pojos.WynnCraftConfig;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public final class ConfigSectionContentSupport {
    private static final int ROW_STEP = 24;
    private static final int MIN_CONTENT_HEIGHT = 20;
    private static final int SLIDER_STEP = 30;
    private static final int SECTION_TOP_MARGIN = 16;
    private static final int GROUP_GAP = 34;
    private static final int GROUP_PADDING_TOP = 18;
    private static final int GROUP_PADDING_BOTTOM = 8;
    private static final int GROUP_PADDING_SIDE = 6;

    private ConfigSectionContentSupport() {
    }

    public static int render(
            ConfigSection selectedSection,
            ModConfig config,
            int x,
            int y,
            int width,
            int viewportHeight,
            Translator translator,
            GroupBoxAdder groupBoxAdder,
            ToggleAdder toggleAdder,
            IntSliderAdder sliderAdder,
            ActionAdder actionAdder,
            TextFieldRowAdder textFieldRowAdder,
            BindingLabelProvider bindingLabelProvider,
            HotkeyAction hotkeyStartBinding,
            HotkeyAction hotkeyClearBinding,
            HotkeyAction hotkeyCycleMode,
            Runnable openCacheDirectoryAction,
            Runnable openWynnDialogueHudEditorAction,
            RouteSelectorAdder routeSelectorAdder,
            ProviderSectionAdder providerSectionAdder
    ) {
        if (selectedSection != ConfigSection.PROVIDERS) {
            y += SECTION_TOP_MARGIN;
        }

        switch (selectedSection) {
            case CHAT_OUTPUT -> {
                ChatTranslateConfig.ChatOutputTranslateConfig output = config.chatTranslate.output;
                int basicStart = y;
                toggleAdder.add(x, y, width, translator.t("label.enabled"), () -> output.enabled, value -> output.enabled = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.auto_translate"), () -> output.auto_translate, value -> output.auto_translate = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.streaming"), () -> output.streaming_response, value -> output.streaming_response = value);
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.target_language"),
                        48,
                        output.target_language,
                        translator.t("placeholder.target_language"),
                        value -> output.target_language = sanitizeLanguage(value),
                        value -> true,
                        true
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.basic"), x, width, basicStart, y);

                y += GROUP_GAP;
                int routeStart = y;
                routeSelectorAdder.add(config.providerManager, RouteSlot.CHAT_OUTPUT, x, y, width);
                addGroupBox(groupBoxAdder, translator.t("group.route"), x, width, routeStart, routeStart + ROW_STEP);
                return routeStart + ROW_STEP;
            }
            case CHAT_INPUT -> {
                ChatTranslateConfig.ChatInputTranslateConfig input = config.chatTranslate.input;
                if (input.keybinding == null) {
                    input.keybinding = new InputBindingConfig();
                }
                if (input.assistant_panel_enabled == null) {
                    input.assistant_panel_enabled = false;
                }

                int basicStart = y;
                toggleAdder.add(x, y, width, translator.t("label.enabled"), () -> input.enabled, value -> input.enabled = value);
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.chat_input_panel_enabled"),
                        () -> Boolean.TRUE.equals(input.assistant_panel_enabled),
                        value -> input.assistant_panel_enabled = value
                );
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.streaming"), () -> input.streaming_response, value -> input.streaming_response = value);
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.target_language"),
                        48,
                        input.target_language,
                        translator.t("placeholder.target_language"),
                        value -> input.target_language = sanitizeLanguage(value),
                        value -> true,
                        true
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.basic"), x, width, basicStart, y);

                y += GROUP_GAP;
                int hotkeyStart = y;
                actionAdder.add(x, y, width, bindingLabelProvider.label(HotkeyTarget.CHAT_INPUT, input.keybinding), () -> hotkeyStartBinding.handle(HotkeyTarget.CHAT_INPUT));
                y += ROW_STEP;
                actionAdder.add(x, y, width, translator.t("button.hotkey_clear"), () -> hotkeyClearBinding.handle(HotkeyTarget.CHAT_INPUT));
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.hotkey"), x, width, hotkeyStart, y);

                y += GROUP_GAP;
                int routeStart = y;
                routeSelectorAdder.add(config.providerManager, RouteSlot.CHAT_INPUT, x, y, width);
                addGroupBox(groupBoxAdder, translator.t("group.route"), x, width, routeStart, routeStart + ROW_STEP);
                return routeStart + ROW_STEP;
            }
            case ITEM -> {
                ItemTranslateConfig item = config.itemTranslate;
                if (item.keybinding == null) {
                    item.keybinding = new ItemTranslateConfig.KeybindingConfig();
                }
                if (item.keybinding.binding == null) {
                    item.keybinding.binding = new InputBindingConfig();
                }
                if (item.keybinding.refreshBinding == null) {
                    item.keybinding.refreshBinding = new InputBindingConfig();
                }
                if (item.debug == null) {
                    item.debug = new ItemTranslateConfig.DebugConfig();
                }

                int basicStart = y;
                toggleAdder.add(x, y, width, translator.t("label.enabled"), () -> item.enabled, value -> item.enabled = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.translate_item_name"), () -> item.enabled_translate_item_custom_name, value -> item.enabled_translate_item_custom_name = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.translate_item_lore"), () -> item.enabled_translate_item_lore, value -> item.enabled_translate_item_lore = value);
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.target_language"),
                        48,
                        item.target_language,
                        translator.t("placeholder.target_language"),
                        value -> item.target_language = sanitizeLanguage(value),
                        value -> true,
                        true
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.basic"), x, width, basicStart, y);

                y += GROUP_GAP;
                int hotkeyStart = y;

                actionAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.hotkey_mode", modeText(translator, item.keybinding.mode.name())),
                        () -> hotkeyCycleMode.handle(HotkeyTarget.ITEM)
                );
                y += ROW_STEP;
                actionAdder.add(x, y, width, bindingLabelProvider.label(HotkeyTarget.ITEM, item.keybinding.binding), () -> hotkeyStartBinding.handle(HotkeyTarget.ITEM));
                y += ROW_STEP;
                actionAdder.add(x, y, width, bindingLabelProvider.label(HotkeyTarget.ITEM_REFRESH, item.keybinding.refreshBinding), () -> hotkeyStartBinding.handle(HotkeyTarget.ITEM_REFRESH));
                y += ROW_STEP;
                actionAdder.add(x, y, width, translator.t("button.hotkey_clear"), () -> hotkeyClearBinding.handle(HotkeyTarget.ITEM));
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.hotkey"), x, width, hotkeyStart, y);

                y += GROUP_GAP;
                int performanceStart = y;

                sliderAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_threads"),
                        1,
                        16,
                        () -> item.max_concurrent_requests,
                        value -> item.max_concurrent_requests = value
                );
                y += SLIDER_STEP;

                sliderAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_batch_size"),
                        1,
                        64,
                        () -> item.max_batch_size,
                        value -> item.max_batch_size = value
                );
                y += SLIDER_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.performance"), x, width, performanceStart, y);

                y += GROUP_GAP;
                int routeStart = y;
                routeSelectorAdder.add(config.providerManager, RouteSlot.ITEM, x, y, width);
                addGroupBox(groupBoxAdder, translator.t("group.route"), x, width, routeStart, routeStart + ROW_STEP);
                return routeStart + ROW_STEP;
            }
            case DEBUG -> {
                if (config.debug == null) {
                    config.debug = new DebugConfig();
                }
                DebugConfig debug = config.debug;
                ChatTranslateConfig.ChatOutputTranslateConfig output = config.chatTranslate.output;
                if (output.debug == null) {
                    output.debug = new ChatTranslateConfig.ChatOutputTranslateConfig.DebugConfig();
                }
                ItemTranslateConfig item = config.itemTranslate;
                if (item.debug == null) {
                    item.debug = new ItemTranslateConfig.DebugConfig();
                }
                WynnCraftConfig wynnCraft = config.wynnCraft;
                if (wynnCraft == null) {
                    wynnCraft = new WynnCraftConfig();
                    config.wynnCraft = wynnCraft;
                }
                if (wynnCraft.npc_dialogue == null) {
                    wynnCraft.npc_dialogue = new WynnCraftConfig.NpcDialogueConfig();
                }
                if (wynnCraft.npc_dialogue.hud == null) {
                    wynnCraft.npc_dialogue.hud = new WynnCraftConfig.HudConfig();
                }
                if (wynnCraft.npc_dialogue.debug == null) {
                    wynnCraft.npc_dialogue.debug = new WynnCraftConfig.DebugConfig();
                }
                if (wynnCraft.wynntils_task_tracker == null) {
                    wynnCraft.wynntils_task_tracker = new WynnCraftConfig.WynntilsTaskTrackerConfig();
                }
                if (wynnCraft.wynntils_task_tracker.debug == null) {
                    wynnCraft.wynntils_task_tracker.debug = new WynnCraftConfig.DebugConfig();
                }
                WynnCraftConfig resolvedWynnCraft = wynnCraft;

                int llmDebugStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.debug_log_llm_request_text_stats"),
                        () -> debug.log_llm_request_text_stats,
                        value -> debug.log_llm_request_text_stats = value
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.llm_requests"), x, width, llmDebugStart, y);

                y += GROUP_GAP;
                int chatOutputDebugStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.chat_output_dev_enabled"),
                        () -> output.debug.enabled,
                        value -> output.debug.enabled = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.chat_output_dev_log_intercept"),
                        () -> output.debug.log_intercepted_message,
                        value -> output.debug.log_intercepted_message = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.chat_output_dev_log_llm_submission"),
                        () -> output.debug.log_llm_submission,
                        value -> output.debug.log_llm_submission = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.chat_output_dev_log_reflow_mapping"),
                        () -> output.debug.log_reflow_mapping,
                        value -> output.debug.log_reflow_mapping = value
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.chat_output_debug"), x, width, chatOutputDebugStart, y);

                y += GROUP_GAP;
                int itemDebugStart = y;
                toggleAdder.add(x, y, width, translator.t("label.item_dev_enabled"), () -> item.debug.enabled, value -> item.debug.enabled = value);
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_tooltip_filter"),
                        () -> item.debug.log_tooltip_filter_result,
                        value -> item.debug.log_tooltip_filter_result = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_tooltip_nodes"),
                        () -> item.debug.log_tooltip_node_summary,
                        value -> item.debug.log_tooltip_node_summary = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_tooltip_paragraph"),
                        () -> item.debug.log_tooltip_paragraph_result,
                        value -> item.debug.log_tooltip_paragraph_result = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_tooltip_style_map"),
                        () -> item.debug.log_tooltip_style_map,
                        value -> item.debug.log_tooltip_style_map = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_tooltip_timing"),
                        () -> item.debug.log_tooltip_timing,
                        value -> item.debug.log_tooltip_timing = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_batch_timing"),
                        () -> item.debug.log_item_batch_timing,
                        value -> item.debug.log_item_batch_timing = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_cache_migration"),
                        () -> item.debug.log_cache_migration,
                        value -> item.debug.log_cache_migration = value
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.item_debug"), x, width, itemDebugStart, y);

                y += GROUP_GAP;
                int dialogueDebugStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.wynn_npc_dialogue_dev_enabled"),
                        () -> resolvedWynnCraft.npc_dialogue.debug.enabled,
                        value -> resolvedWynnCraft.npc_dialogue.debug.enabled = value
                );
                y += ROW_STEP;
                addGroupBox(
                        groupBoxAdder,
                        translator.t("group.wynn_npc_dialogue_debug"),
                        x,
                        width,
                        dialogueDebugStart,
                        y);

                y += GROUP_GAP;
                int trackerDebugStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.wynntils_task_tracker_dev_enabled"),
                        () -> resolvedWynnCraft.wynntils_task_tracker.debug.enabled,
                        value -> resolvedWynnCraft.wynntils_task_tracker.debug.enabled = value
                );
                y += ROW_STEP;
                addGroupBox(
                        groupBoxAdder,
                        translator.t("group.wynntils_task_tracker_debug"),
                        x,
                        width,
                        trackerDebugStart,
                        y);

                y += GROUP_GAP;
                int localHitLogsStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_items_local_hits"),
                        () -> item.debug.log_items_local_hits,
                        value -> item.debug.log_items_local_hits = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.item_dev_log_skills_local_hits"),
                        () -> item.debug.log_skills_local_hits,
                        value -> item.debug.log_skills_local_hits = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.wynn_npc_dialogue_log_dialogues_local_hits"),
                        () -> resolvedWynnCraft.npc_dialogue.debug.log_dialogues_local_hits,
                        value -> resolvedWynnCraft.npc_dialogue.debug.log_dialogues_local_hits = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.wynntils_task_tracker_log_quests_local_hits"),
                        () -> resolvedWynnCraft.wynntils_task_tracker.debug.log_quests_local_hits,
                        value -> resolvedWynnCraft.wynntils_task_tracker.debug.log_quests_local_hits = value
                );
                y += ROW_STEP;
                addGroupBox(
                        groupBoxAdder,
                        translator.t("group.log_hits"),
                        x,
                        width,
                        localHitLogsStart,
                        y);
                return y;
            }
            case SCOREBOARD -> {
                ScoreboardConfig scoreboard = config.scoreboardTranslate;
                if (scoreboard.keybinding == null) {
                    scoreboard.keybinding = new ScoreboardConfig.KeybindingConfig();
                }
                if (scoreboard.keybinding.binding == null) {
                    scoreboard.keybinding.binding = new InputBindingConfig();
                }

                int basicStart = y;
                toggleAdder.add(x, y, width, translator.t("label.enabled"), () -> scoreboard.enabled, value -> scoreboard.enabled = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.translate_prefix_suffix"), () -> scoreboard.enabled_translate_prefix_and_suffix_name, value -> scoreboard.enabled_translate_prefix_and_suffix_name = value);
                y += ROW_STEP;
                toggleAdder.add(x, y, width, translator.t("label.translate_player_name"), () -> scoreboard.enabled_translate_player_name, value -> scoreboard.enabled_translate_player_name = value);
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.target_language"),
                        48,
                        scoreboard.target_language,
                        translator.t("placeholder.target_language"),
                        value -> scoreboard.target_language = sanitizeLanguage(value),
                        value -> true,
                        true
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.basic"), x, width, basicStart, y);

                y += GROUP_GAP;
                int hotkeyStart = y;
                actionAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.hotkey_mode", modeText(translator, scoreboard.keybinding.mode.name())),
                        () -> hotkeyCycleMode.handle(HotkeyTarget.SCOREBOARD)
                );
                y += ROW_STEP;
                actionAdder.add(x, y, width, bindingLabelProvider.label(HotkeyTarget.SCOREBOARD, scoreboard.keybinding.binding), () -> hotkeyStartBinding.handle(HotkeyTarget.SCOREBOARD));
                y += ROW_STEP;
                actionAdder.add(x, y, width, translator.t("button.hotkey_clear"), () -> hotkeyClearBinding.handle(HotkeyTarget.SCOREBOARD));
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.hotkey"), x, width, hotkeyStart, y);

                y += GROUP_GAP;
                int routeStart = y;
                routeSelectorAdder.add(config.providerManager, RouteSlot.SCOREBOARD, x, y, width);
                addGroupBox(groupBoxAdder, translator.t("group.route"), x, width, routeStart, routeStart + ROW_STEP);
                return routeStart + ROW_STEP;
            }
            case WYNNCRAFT -> {
                WynnCraftConfig resolvedWynnCraft = config.wynnCraft;
                if (resolvedWynnCraft == null) {
                    resolvedWynnCraft = new WynnCraftConfig();
                    config.wynnCraft = resolvedWynnCraft;
                }
                WynnCraftConfig wynnCraft = resolvedWynnCraft;
                if (wynnCraft.npc_dialogue == null) {
                    wynnCraft.npc_dialogue = new WynnCraftConfig.NpcDialogueConfig();
                }
                if (wynnCraft.npc_dialogue.debug == null) {
                    wynnCraft.npc_dialogue.debug = new WynnCraftConfig.DebugConfig();
                }
                if (wynnCraft.wynntils_task_tracker == null) {
                    wynnCraft.wynntils_task_tracker = new WynnCraftConfig.WynntilsTaskTrackerConfig();
                }
                if (wynnCraft.wynntils_task_tracker.debug == null) {
                    wynnCraft.wynntils_task_tracker.debug = new WynnCraftConfig.DebugConfig();
                }
                if (wynnCraft.wynntils_task_tracker.keybinding == null) {
                    wynnCraft.wynntils_task_tracker.keybinding = new WynnCraftConfig.KeybindingConfig();
                }
                if (wynnCraft.wynntils_task_tracker.keybinding.binding == null) {
                    wynnCraft.wynntils_task_tracker.keybinding.binding = new InputBindingConfig();
                }
                if (wynnCraft.wynntils_task_tracker.keybinding.refreshBinding == null) {
                    wynnCraft.wynntils_task_tracker.keybinding.refreshBinding = new InputBindingConfig();
                }
                if (wynnCraft.target_language == null || wynnCraft.target_language.isBlank()) {
                    wynnCraft.target_language = WynnCraftConfig.DEFAULT_TARGET_LANGUAGE;
                }

                int dialogueStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.enabled"),
                        () -> wynnCraft.npc_dialogue.enabled,
                        value -> wynnCraft.npc_dialogue.enabled = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.translate_wynn_npc_dialogue_npc_name"),
                        () -> wynnCraft.npc_dialogue.translate_npc_name,
                        value -> wynnCraft.npc_dialogue.translate_npc_name = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.translate_wynn_npc_dialogue_options"),
                        () -> wynnCraft.npc_dialogue.translate_options,
                        value -> wynnCraft.npc_dialogue.translate_options = value
                );
                y += ROW_STEP;
                actionAdder.add(
                        x,
                        y,
                        width,
                        translator.t("button.edit_hud"),
                        openWynnDialogueHudEditorAction
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.wynn_npc_dialogue"), x, width, dialogueStart, y);

                y += GROUP_GAP;
                int trackerStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.enabled"),
                        () -> wynnCraft.wynntils_task_tracker.enabled,
                        value -> wynnCraft.wynntils_task_tracker.enabled = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.translate_wynntils_task_tracker_title"),
                        () -> wynnCraft.wynntils_task_tracker.translate_title,
                        value -> wynnCraft.wynntils_task_tracker.translate_title = value
                );
                y += ROW_STEP;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.translate_wynntils_task_tracker_description"),
                        () -> wynnCraft.wynntils_task_tracker.translate_description,
                        value -> wynnCraft.wynntils_task_tracker.translate_description = value
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.wynntils_task_tracker"), x, width, trackerStart, y);

                y += GROUP_GAP;
                int hotkeyStart = y;
                actionAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.hotkey_mode", modeText(translator, wynnCraft.wynntils_task_tracker.keybinding.mode.name())),
                        () -> hotkeyCycleMode.handle(HotkeyTarget.WYNNTILS_TASK_TRACKER)
                );
                y += ROW_STEP;
                actionAdder.add(
                        x,
                        y,
                        width,
                        bindingLabelProvider.label(
                                HotkeyTarget.WYNNTILS_TASK_TRACKER,
                                wynnCraft.wynntils_task_tracker.keybinding.binding),
                        () -> hotkeyStartBinding.handle(HotkeyTarget.WYNNTILS_TASK_TRACKER)
                );
                y += ROW_STEP;
                actionAdder.add(
                        x,
                        y,
                        width,
                        bindingLabelProvider.label(
                                HotkeyTarget.WYNNTILS_TASK_TRACKER_REFRESH,
                                wynnCraft.wynntils_task_tracker.keybinding.refreshBinding),
                        () -> hotkeyStartBinding.handle(HotkeyTarget.WYNNTILS_TASK_TRACKER_REFRESH)
                );
                y += ROW_STEP;
                actionAdder.add(
                        x,
                        y,
                        width,
                        translator.t("button.hotkey_clear"),
                        () -> hotkeyClearBinding.handle(HotkeyTarget.WYNNTILS_TASK_TRACKER)
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.hotkey"), x, width, hotkeyStart, y);

                y += GROUP_GAP;
                int routeStart = y;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.target_language"),
                        48,
                        wynnCraft.target_language,
                        translator.t("placeholder.target_language"),
                        value -> wynnCraft.target_language = sanitizeLanguage(value),
                        value -> true,
                        true
                );
                y += ROW_STEP;
                routeSelectorAdder.add(config.providerManager, RouteSlot.WYNNCRAFT, x, y, width);
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.route"), x, width, routeStart, y);
                return y;
            }
            case CACHE -> {
                CacheBackupConfig resolvedCacheBackup = config.cacheBackup;
                if (resolvedCacheBackup == null) {
                    resolvedCacheBackup = new CacheBackupConfig();
                    config.cacheBackup = resolvedCacheBackup;
                }
                CacheBackupConfig cacheBackup = resolvedCacheBackup;

                int policyStart = y;
                toggleAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_backup_enabled"),
                        cacheBackup::isEnabled,
                        value -> cacheBackup.enabled = value
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.backup_interval_minutes"),
                        5,
                        Integer.toString(cacheBackup.backup_interval_minutes),
                        translator.t(
                                "placeholder.backup_interval_minutes",
                                CacheBackupConfig.MIN_BACKUP_INTERVAL_MINUTES,
                                CacheBackupConfig.MAX_BACKUP_INTERVAL_MINUTES
                        ),
                        value -> {
                            Integer parsed = tryParseNonNegativeInt(value);
                            if (parsed != null && parsed >= CacheBackupConfig.MIN_BACKUP_INTERVAL_MINUTES) {
                                cacheBackup.backup_interval_minutes = parsed;
                            }
                        },
                        value -> isAllowedNumericInput(value, CacheBackupConfig.MAX_BACKUP_INTERVAL_MINUTES),
                        true
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.max_backup_count"),
                        5,
                        Integer.toString(cacheBackup.max_backup_count),
                        translator.t(
                                "placeholder.max_backup_count",
                                CacheBackupConfig.MIN_MAX_BACKUP_COUNT,
                                CacheBackupConfig.MAX_MAX_BACKUP_COUNT
                        ),
                        value -> {
                            Integer parsed = tryParseNonNegativeInt(value);
                            if (parsed != null && parsed >= CacheBackupConfig.MIN_MAX_BACKUP_COUNT) {
                                cacheBackup.max_backup_count = parsed;
                            }
                        },
                        value -> isAllowedNumericInput(value, CacheBackupConfig.MAX_MAX_BACKUP_COUNT),
                        true
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.backup_policy"), x, width, policyStart, y);

                y += GROUP_GAP;
                ItemTemplateCache.CacheStats itemStats = ItemTemplateCache.getInstance().getCacheStats();
                ScoreboardTextCache.CacheStats scoreboardStats = ScoreboardTextCache.getInstance().getCacheStats();
                WynnDialogueTextCache.CacheStats dialogueStats = WynnDialogueTextCache.getInstance().getCacheStats();
                WynntilsTaskTrackerTextCache.CacheStats taskTrackerStats = WynntilsTaskTrackerTextCache.getInstance().getCacheStats();
                int totalTranslated =
                        itemStats.translated()
                                + scoreboardStats.translated()
                                + dialogueStats.translated()
                                + taskTrackerStats.translated();
                int totalTracked =
                        itemStats.total()
                                + scoreboardStats.total()
                                + dialogueStats.total()
                                + taskTrackerStats.total();

                int statsStart = y;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_entries_item"),
                        64,
                        translator.t("value.cache_entries", itemStats.translated(), itemStats.total()).getString(),
                        Text.empty(),
                        value -> {
                        },
                        value -> true,
                        false
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_entries_scoreboard"),
                        64,
                        translator.t("value.cache_entries", scoreboardStats.translated(), scoreboardStats.total()).getString(),
                        Text.empty(),
                        value -> {
                        },
                        value -> true,
                        false
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_entries_wynn_dialogue"),
                        64,
                        translator.t("value.cache_entries", dialogueStats.translated(), dialogueStats.total()).getString(),
                        Text.empty(),
                        value -> {
                        },
                        value -> true,
                        false
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_entries_wynntils_task_tracker"),
                        64,
                        translator.t("value.cache_entries", taskTrackerStats.translated(), taskTrackerStats.total()).getString(),
                        Text.empty(),
                        value -> {
                        },
                        value -> true,
                        false
                );
                y += ROW_STEP;
                textFieldRowAdder.add(
                        x,
                        y,
                        width,
                        translator.t("label.cache_entries_total"),
                        64,
                        translator.t("value.cache_entries", totalTranslated, totalTracked).getString(),
                        Text.empty(),
                        value -> {
                        },
                        value -> true,
                        false
                );
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.cache_entries"), x, width, statsStart, y);

                y += GROUP_GAP;
                int toolsStart = y;
                actionAdder.add(x, y, width, translator.t("button.open_cache_directory"), openCacheDirectoryAction);
                y += ROW_STEP;
                addGroupBox(groupBoxAdder, translator.t("group.backup_tools"), x, width, toolsStart, y);
                return y;
            }
            case PROVIDERS -> {
                return providerSectionAdder.add(config.providerManager, x, y, width, viewportHeight);
            }
        }

        return y;
    }

    private static void addGroupBox(
            GroupBoxAdder groupBoxAdder,
            Text title,
            int x,
            int width,
            int contentStartY,
            int contentEndY
    ) {
        int contentBottom = Math.max(contentStartY + MIN_CONTENT_HEIGHT, contentEndY);
        int groupX = x - GROUP_PADDING_SIDE;
        int groupY = contentStartY - GROUP_PADDING_TOP;
        int groupWidth = width + GROUP_PADDING_SIDE * 2;
        int groupHeight = (contentBottom - contentStartY) + GROUP_PADDING_TOP + GROUP_PADDING_BOTTOM;
        groupBoxAdder.add(groupX, groupY, groupWidth, groupHeight, title);
    }

    private static Text modeText(Translator translator, String modeName) {
        return switch (modeName) {
            case "HOLD_TO_TRANSLATE" -> translator.t("state.hold_to_translate");
            case "HOLD_TO_SEE_ORIGINAL" -> translator.t("state.hold_to_see_original");
            default -> translator.t("state.disabled");
        };
    }

    private static String sanitizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        return language.trim();
    }

    private static Integer tryParseNonNegativeInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isAllowedNumericInput(String value, int maxValue) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }

        try {
            return Integer.parseInt(value) <= maxValue;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    public interface Translator {
        Text t(String key, Object... args);
    }

    @FunctionalInterface
    public interface GroupBoxAdder {
        void add(int x, int y, int width, int height, Text title);
    }

    @FunctionalInterface
    public interface ToggleAdder {
        void add(int x, int y, int width, Text label, BooleanSupplier getter, Consumer<Boolean> setter);
    }

    @FunctionalInterface
    public interface IntSliderAdder {
        void add(int x, int y, int width, Text label, int min, int max, IntSupplier getter, IntConsumer setter);
    }

    @FunctionalInterface
    public interface ActionAdder {
        void add(int x, int y, int width, Text label, Runnable action);
    }

    @FunctionalInterface
    public interface TextFieldRowAdder {
        void add(
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
        );
    }

    @FunctionalInterface
    public interface BindingLabelProvider {
        Text label(HotkeyTarget target, InputBindingConfig binding);
    }

    @FunctionalInterface
    public interface HotkeyAction {
        void handle(HotkeyTarget target);
    }

    @FunctionalInterface
    public interface RouteSelectorAdder {
        void add(ProviderManagerConfig manager, RouteSlot routeSlot, int x, int y, int width);
    }

    @FunctionalInterface
    public interface ProviderSectionAdder {
        int add(ProviderManagerConfig providerManager, int x, int y, int width, int viewportHeight);
    }

    public enum HotkeyTarget {
        CHAT_INPUT,
        ITEM,
        ITEM_REFRESH,
        SCOREBOARD,
        WYNNTILS_TASK_TRACKER,
        WYNNTILS_TASK_TRACKER_REFRESH
    }
}
