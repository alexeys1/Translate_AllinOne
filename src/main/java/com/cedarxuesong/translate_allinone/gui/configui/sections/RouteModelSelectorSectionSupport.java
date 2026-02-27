package com.cedarxuesong.translate_allinone.gui.configui.sections;

import com.cedarxuesong.translate_allinone.gui.configui.model.RouteModelOption;
import com.cedarxuesong.translate_allinone.gui.configui.model.RouteSlot;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ProviderManagerConfig;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Supplier;

public final class RouteModelSelectorSectionSupport {
    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_STEP = 22;
    private static final int OPTION_GAP = OPTION_STEP - OPTION_HEIGHT;

    private RouteModelSelectorSectionSupport() {
    }

    public static void render(
            ProviderManagerConfig manager,
            RouteSlot routeSlot,
            int x,
            int y,
            int width,
            int dropdownMinY,
            int dropdownMaxY,
            boolean dropdownOpen,
            Translator translator,
            ActionBlockAdder actionBlockAdder,
            FloatingActionBlockAdder floatingActionBlockAdder,
            Runnable onToggleDropdown,
            RouteSelectionHandler onSelected,
            Style style
    ) {
        String selectedRouteKey = RouteModelSectionSupport.getRouteKey(manager, routeSlot);

        actionBlockAdder.add(
                x,
                y,
                width,
                OPTION_HEIGHT,
                () -> translator.t(
                        "label.route_model",
                        RouteModelSectionSupport.describeRouteModel(
                                selectedRouteKey,
                                manager,
                                translator.t("route_model.none"),
                                key -> translator.t("provider.missing", key)
                        )
                ),
                onToggleDropdown,
                style.colorBlock(),
                style.colorBlockHover(),
                style.colorText(),
                false
        );

        if (!dropdownOpen) {
            return;
        }

        List<RouteModelOption> options = RouteModelSectionSupport.buildRouteModelOptions(manager, translator.t("route_model.none"));
        int dropdownX = x;
        int dropdownWidth = width;
        int dropdownHeight = dropdownHeight(options.size());
        int belowTop = y + OPTION_STEP;
        int aboveTop = y - dropdownHeight - OPTION_GAP;
        int availableBelow = Math.max(0, dropdownMaxY - belowTop);
        int availableAbove = Math.max(0, (y - OPTION_GAP) - dropdownMinY);
        boolean openUpward = dropdownHeight > availableBelow && availableAbove > availableBelow;
        int optionY = clampDropdownTop(openUpward ? aboveTop : belowTop, dropdownHeight, dropdownMinY, dropdownMaxY);
        for (RouteModelOption option : options) {
            boolean selected = option.routeKey().equals(selectedRouteKey == null ? "" : selectedRouteKey);
            int color = selected ? style.colorBlockAccent() : style.colorBlock();
            int hoverColor = selected ? style.colorBlockAccentHover() : style.colorBlockHover();
            int textColor = selected ? style.colorTextAccent() : style.colorText();
            floatingActionBlockAdder.add(
                    dropdownX,
                    optionY,
                    dropdownWidth,
                    OPTION_HEIGHT,
                    () -> option.displayLabel(),
                    () -> onSelected.onSelected(option.routeKey(), option.displayLabel()),
                    color,
                    hoverColor,
                    textColor,
                    false
            );
            optionY += OPTION_STEP;
        }
    }

    private static int dropdownHeight(int optionCount) {
        if (optionCount <= 0) {
            return 0;
        }
        return OPTION_HEIGHT + (optionCount - 1) * OPTION_STEP;
    }

    private static int clampDropdownTop(int preferredTop, int dropdownHeight, int minY, int maxY) {
        if (dropdownHeight <= 0) {
            return preferredTop;
        }
        int minTop = minY;
        int maxTop = maxY - dropdownHeight;
        if (maxTop < minTop) {
            return minTop;
        }
        return Math.max(minTop, Math.min(maxTop, preferredTop));
    }

    @FunctionalInterface
    public interface Translator {
        Text t(String key, Object... args);
    }

    @FunctionalInterface
    public interface RouteSelectionHandler {
        void onSelected(String routeKey, Text displayLabel);
    }

    @FunctionalInterface
    public interface ActionBlockAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Text> labelSupplier,
                Runnable action,
                int color,
                int hoverColor,
                int textColor,
                boolean centered
        );
    }

    @FunctionalInterface
    public interface FloatingActionBlockAdder {
        void add(
                int x,
                int y,
                int width,
                int height,
                Supplier<Text> labelSupplier,
                Runnable action,
                int color,
                int hoverColor,
                int textColor,
                boolean centered
        );
    }

    public record Style(
            int colorBlock,
            int colorBlockHover,
            int colorBlockAccent,
            int colorBlockAccentHover,
            int colorText,
            int colorTextAccent
    ) {
    }
}
