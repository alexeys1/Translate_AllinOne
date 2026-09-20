package com.alexeys.translate_allinone.gui.configui.sections;

import com.alexeys.translate_allinone.gui.configui.model.RouteModelOption;
import com.alexeys.translate_allinone.utils.config.ui.RouteSlot;
import com.alexeys.translate_allinone.utils.config.ui.ProviderProfileSupport;
import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

public final class RouteModelSectionSupport {
    private RouteModelSectionSupport() {
    }

    public static List<RouteModelOption> buildRouteModelOptions(ProviderManagerConfig manager, Component noneOptionLabel) {
        List<RouteModelOption> options = new ArrayList<>();
        options.add(new RouteModelOption("", noneOptionLabel));

        if (manager.providers == null) {
            return options;
        }

        for (ApiProviderProfile profile : manager.providers) {
            if (profile == null || profile.id == null || profile.id.isBlank()) {
                continue;
            }
            profile.ensureModelSettings();
            for (String modelId : ProviderProfileSupport.normalizeModelIds(profile)) {
                if (modelId == null || modelId.isBlank()) {
                    continue;
                }
                String routeKey = ProviderManagerConfig.composeRouteKey(profile.id, modelId);
                Component display = Component.literal(ProviderProfileSupport.safeProviderName(profile) + "/" + modelId);
                options.add(new RouteModelOption(routeKey, display));
            }
        }
        return options;
    }

    public static String getRouteKey(ProviderManagerConfig manager, RouteSlot routeSlot) {
        return switch (routeSlot) {
            case ITEM -> manager.routes.item;
            case SCOREBOARD -> manager.routes.scoreboard;
            case OTHER_TRANSLATIONS -> manager.routes.other_translations;
            case WYNNCRAFT -> manager.routes.wynncraft;
            case WYNN_NPC_DIALOGUE -> manager.routes.wynn_npc_dialogue;
            case WYNNTILS_TASK_TRACKER -> manager.routes.wynntils_task_tracker;
            case CHAT_INPUT -> manager.routes.chat_input;
            case CHAT_OUTPUT -> manager.routes.chat_output;
        };
    }

    public static void setRouteKey(ProviderManagerConfig manager, RouteSlot routeSlot, String routeKey) {
        switch (routeSlot) {
            case ITEM -> manager.routes.item = routeKey;
            case SCOREBOARD -> manager.routes.scoreboard = routeKey;
            case OTHER_TRANSLATIONS -> manager.routes.other_translations = routeKey;
            case WYNNCRAFT -> manager.routes.wynncraft = routeKey;
            case WYNN_NPC_DIALOGUE -> manager.routes.wynn_npc_dialogue = routeKey;
            case WYNNTILS_TASK_TRACKER -> manager.routes.wynntils_task_tracker = routeKey;
            case CHAT_INPUT -> manager.routes.chat_input = routeKey;
            case CHAT_OUTPUT -> manager.routes.chat_output = routeKey;
        }
    }

    public static Component describeRouteModel(
            String routeKey,
            ProviderManagerConfig manager,
            Component noneLabel,
            Function<String, Component> missingLabelFactory
    ) {
        if (routeKey == null || routeKey.isBlank()) {
            return noneLabel;
        }

        String providerId = ProviderManagerConfig.extractProviderId(routeKey);
        String modelId = ProviderManagerConfig.extractModelId(routeKey);
        if (providerId.isBlank() || modelId.isBlank()) {
            return missingLabelFactory.apply(routeKey);
        }

        ApiProviderProfile profile = manager.findById(providerId);
        if (profile == null) {
            return missingLabelFactory.apply(routeKey);
        }
        profile.ensureModelSettings();
        if (profile.getModelSettings(modelId) == null) {
            return missingLabelFactory.apply(routeKey);
        }
        return Component.literal(ProviderProfileSupport.safeProviderName(profile) + "/" + modelId);
    }
}
