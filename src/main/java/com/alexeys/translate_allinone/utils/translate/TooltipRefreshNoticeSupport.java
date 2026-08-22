package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.ItemTemplateCache;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import com.alexeys.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipPlan;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TooltipRefreshNoticeSupport {
    private static final String TOOLTIP_REFRESH_NOTICE_KEY = "text.translate_allinone.item.tooltip_refresh_forced";
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final long REFRESH_HOLD_RELEASE_GRACE_MILLIS = 250L;
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipRefreshNoticeSupport");
    private static final Set<Integer> refreshedTooltipSignaturesThisHold = new HashSet<>();
    private static final Set<String> refreshedComponentKeysForNotice = new HashSet<>();
    private static volatile boolean refreshHoldActive = false;
    private static volatile long refreshHoldGraceExpiresAtMillis = 0L;
    private static volatile int refreshNoticeTooltipSignature = 0;
    private static volatile long refreshNoticeExpiresAtMillis = 0L;

    private TooltipRefreshNoticeSupport() {
    }

    public static void maybeForceRefreshCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        if (!TranslationFeatureGate.isEnabled() || tooltip == null || tooltip.isEmpty() || config == null || !config.enabled) {
            return;
        }
        List<Text> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return;
        }
        boolean preserveStyles = TooltipDecorativeContextSupport.isDecorativeTooltipContext(sanitizedTooltip);
        TooltipRoutePlanner.TooltipPlan plan = TooltipRoutePlanner.planTooltip(
                sanitizedTooltip,
                config,
                preserveStyles
        );
        maybeForceRefreshCurrentTooltip(plan, plan.translationTemplateKeys(), config);
    }

    public static void maybeForceRefreshCurrentTooltip(Set<String> keysToRefresh, ItemTranslateConfig config) {
        maybeForceRefreshCurrentTooltip(null, keysToRefresh, config);
    }

    static void maybeForceRefreshCurrentTooltip(
            TooltipRoutePlanner.TooltipPlan tooltipPlan,
            Set<String> keysToRefresh,
            ItemTranslateConfig config
    ) {
        if (!TranslationFeatureGate.isEnabled()) {
            return;
        }
        boolean hasKeysToRefresh = keysToRefresh != null && !keysToRefresh.isEmpty();
        Set<String> refreshTemplateKeys = collectRefreshTemplateKeys(tooltipPlan, keysToRefresh);

        boolean isRefreshPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
        long now = System.currentTimeMillis();
        clearExpiredComponentRefreshKeys(now);
        if (!updateRefreshHoldState(isRefreshPressed, now)) {
            return;
        }

        if (!hasKeysToRefresh) {
            return;
        }

        int tooltipSignature = computeTooltipSignature(keysToRefresh);
        synchronized (refreshedTooltipSignaturesThisHold) {
            if (!refreshedTooltipSignaturesThisHold.add(tooltipSignature)) {
                return;
            }
        }

        TooltipTemplateRuntime.registerForceRefreshCompatBypass(refreshTemplateKeys);
        int legacyRefreshedCount = ItemTemplateCache.getInstance().forceRefresh(refreshTemplateKeys);
        int componentRefreshedCount = TooltipTranslationSupport.forceRefreshComponentCaches(tooltipPlan, config);
        if (legacyRefreshedCount > 0 || componentRefreshedCount > 0) {
            refreshNoticeTooltipSignature = tooltipSignature;
            refreshNoticeExpiresAtMillis = now + REFRESH_NOTICE_DURATION_MILLIS;
            LOGGER.info(
                    "Forced refresh of current item tooltip translation keys: legacy={}, component={}.",
                    legacyRefreshedCount,
                    componentRefreshedCount
            );
        }
    }

    public static void queueRemoteTranslationForCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        queueRemoteTranslationForCurrentTooltip(tooltip, config, null);
    }

    public static void queueRemoteTranslationForCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config, String source) {
        if (!TranslationFeatureGate.isEnabled()) {
            return;
        }
        Set<String> keysToQueue = TooltipTranslationSupport.collectRemoteTranslationTemplateKeys(tooltip, config);
        if (keysToQueue != null && !keysToQueue.isEmpty()) {
            if (source != null && config != null && config.debug.enabled) {
                LOGGER.info(
                        "[ItemDev:llm-enqueue] source=\"{}\" keyCount={}",
                        source,
                        keysToQueue.size()
                );
            }
            TooltipTranslationSupport.queueRemoteTranslationTemplateKeys(keysToQueue);
        }
    }

    public static boolean shouldShowRefreshNotice(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keys = TooltipTranslationSupport.collectRemoteTranslationTemplateKeys(tooltip, config);
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

    public static boolean containsRefreshNoticeLine(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        for (Text line : tooltip) {
            if (isRefreshNoticeLine(line)) {
                return true;
            }
        }
        return false;
    }

    public static List<Text> appendRefreshNoticeLine(List<Text> tooltip, boolean showRefreshNotice) {
        if (!showRefreshNotice || tooltip == null) {
            return tooltip;
        }

        if (containsRefreshNoticeLine(tooltip)) {
            return tooltip;
        }

        List<Text> tooltipWithNotice = new ArrayList<>(tooltip.size() + 1);
        tooltipWithNotice.addAll(tooltip);
        tooltipWithNotice.add(createRefreshNoticeLine());
        return tooltipWithNotice;
    }

    static int computeTooltipSignature(Set<String> keys) {
        int hash = 1;
        List<String> orderedKeys = new ArrayList<>(keys.size());
        for (String key : keys) {
            orderedKeys.add(key == null ? "" : key);
        }
        Collections.sort(orderedKeys);
        for (String key : orderedKeys) {
            hash = 31 * hash + key.hashCode();
        }
        return 31 * hash + keys.size();
    }

    static boolean updateRefreshHoldState(boolean isRefreshPressed, long now) {
        if (isRefreshPressed) {
            refreshHoldActive = true;
            refreshHoldGraceExpiresAtMillis = now + REFRESH_HOLD_RELEASE_GRACE_MILLIS;
            return true;
        }

        if (refreshHoldActive && now <= refreshHoldGraceExpiresAtMillis) {
            return true;
        }

        clearRefreshHoldState();
        return false;
    }

    static boolean consumeComponentRefresh(String cacheKey, ItemTranslateConfig config) {
        if (cacheKey == null || cacheKey.isBlank() || config == null || config.keybinding == null) {
            return false;
        }
        boolean pressed = KeybindingManager.isPressed(config.keybinding.refreshBinding);
        if (!updateRefreshHoldState(pressed, System.currentTimeMillis())) {
            return false;
        }
        synchronized (refreshedComponentKeysForNotice) {
            return refreshedComponentKeysForNotice.add(cacheKey);
        }
    }

    static boolean shouldSuppressComponentQueue(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        clearExpiredComponentRefreshKeys(now);
        if (now > refreshNoticeExpiresAtMillis) {
            return false;
        }
        synchronized (refreshedComponentKeysForNotice) {
            return refreshedComponentKeysForNotice.contains(cacheKey);
        }
    }

    static Set<String> collectRefreshTemplateKeys(TooltipPlan tooltipPlan, Set<String> keysToRefresh) {
        LinkedHashSet<String> refreshTemplateKeys = new LinkedHashSet<>();
        if (keysToRefresh != null) {
            for (String key : keysToRefresh) {
                if (key != null && !key.isBlank()) {
                    refreshTemplateKeys.add(key);
                }
            }
        }
        if (tooltipPlan == null || tooltipPlan.segments() == null) {
            return refreshTemplateKeys;
        }

        for (TooltipRoutePlanner.TooltipRouteSegment segment : tooltipPlan.segments()) {
            if (segment == null || segment.kind() != TooltipRoutePlanner.TooltipRouteKind.PARAGRAPH_BLOCK) {
                continue;
            }
            refreshTemplateKeys.addAll(
                    TooltipComponentTranslationSupport.collectParagraphForceRefreshTemplateKeys(segment.paragraphBlock())
            );
        }
        return refreshTemplateKeys;
    }

    static void markComponentRefreshHandled(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return;
        }
        synchronized (refreshedComponentKeysForNotice) {
            refreshedComponentKeysForNotice.add(cacheKey);
        }
    }

    static void clearRefreshHoldState() {
        refreshHoldActive = false;
        refreshHoldGraceExpiresAtMillis = 0L;
        synchronized (refreshedTooltipSignaturesThisHold) {
            refreshedTooltipSignaturesThisHold.clear();
        }
    }

    private static void clearExpiredComponentRefreshKeys(long now) {
        if (now <= refreshNoticeExpiresAtMillis) {
            return;
        }
        synchronized (refreshedComponentKeysForNotice) {
            refreshedComponentKeysForNotice.clear();
        }
    }
}
