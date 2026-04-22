package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TooltipRefreshNoticeSupport {
    private static final String TOOLTIP_REFRESH_NOTICE_KEY = "text.translate_allinone.item.tooltip_refresh_forced";
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipRefreshNoticeSupport");
    private static final Set<Integer> refreshedTooltipSignaturesThisHold = new HashSet<>();
    private static volatile int refreshNoticeTooltipSignature = 0;
    private static volatile long refreshNoticeExpiresAtMillis = 0L;

    private TooltipRefreshNoticeSupport() {
    }

    public static void maybeForceRefreshCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keysToRefresh = TooltipRoutePlanner.planTooltip(
                tooltip,
                config,
                config != null && config.wynn_item_compatibility
        ).translationTemplateKeys();
        maybeForceRefreshCurrentTooltip(keysToRefresh, config);
    }

    static void maybeForceRefreshCurrentTooltip(Set<String> keysToRefresh, ItemTranslateConfig config) {
        boolean isRefreshPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
        if (!isRefreshPressed) {
            synchronized (refreshedTooltipSignaturesThisHold) {
                refreshedTooltipSignaturesThisHold.clear();
            }
            return;
        }

        if (keysToRefresh.isEmpty()) {
            return;
        }

        int tooltipSignature = computeTooltipSignature(keysToRefresh);
        synchronized (refreshedTooltipSignaturesThisHold) {
            if (!refreshedTooltipSignaturesThisHold.add(tooltipSignature)) {
                return;
            }
        }

        int refreshedCount = ItemTemplateCache.getInstance().forceRefresh(keysToRefresh);
        if (refreshedCount > 0) {
            TooltipTemplateRuntime.registerForceRefreshCompatBypass(keysToRefresh);
            refreshNoticeTooltipSignature = tooltipSignature;
            refreshNoticeExpiresAtMillis = System.currentTimeMillis() + REFRESH_NOTICE_DURATION_MILLIS;
            LOGGER.info("Forced refresh of {} current item tooltip translation key(s).", refreshedCount);
        }
    }

    public static boolean shouldShowRefreshNotice(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keys = TooltipRoutePlanner.planTooltip(
                tooltip,
                config,
                config != null && config.wynn_item_compatibility
        ).translationTemplateKeys();
        return shouldShowRefreshNotice(keys);
    }

    static boolean shouldShowRefreshNotice(Set<String> keys) {
        long expiresAt = refreshNoticeExpiresAtMillis;
        if (expiresAt <= 0L || System.currentTimeMillis() > expiresAt) {
            return false;
        }

        if (keys.isEmpty()) {
            return false;
        }
        return computeTooltipSignature(keys) == refreshNoticeTooltipSignature;
    }

    public static Text createRefreshNoticeLine() {
        return Text.translatable(TOOLTIP_REFRESH_NOTICE_KEY).formatted(Formatting.GREEN);
    }

    public static boolean isRefreshNoticeLine(Text line) {
        if (line == null) {
            return false;
        }
        return createRefreshNoticeLine().getString().equals(line.getString());
    }

    public static List<Text> appendRefreshNoticeLine(List<Text> tooltip, boolean showRefreshNotice) {
        if (!showRefreshNotice || tooltip == null) {
            return tooltip;
        }

        for (Text line : tooltip) {
            if (isRefreshNoticeLine(line)) {
                return tooltip;
            }
        }

        List<Text> tooltipWithNotice = new ArrayList<>(tooltip.size() + 1);
        tooltipWithNotice.addAll(tooltip);
        tooltipWithNotice.add(createRefreshNoticeLine());
        return tooltipWithNotice;
    }

    private static int computeTooltipSignature(Set<String> keys) {
        int hash = 1;
        for (String key : keys) {
            hash = 31 * hash + key.hashCode();
        }
        return 31 * hash + keys.size();
    }
}
