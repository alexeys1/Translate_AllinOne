package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.CacheStats;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentCacheModule;
import com.cedarxuesong.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TooltipInternalLineSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String TRANSLATING_STATUS_KEY = "text.translate_allinone.item.tooltip_translating";
    private static final String KEY_MISMATCH_STATUS_KEY = "text.translate_allinone.item.tooltip_key_mismatch_retrying";
    private static final String ERROR_STATUS_KEY = "text.translate_allinone.item.tooltip_translation_error";

    private TooltipInternalLineSupport() {
    }

    public static CacheStats getItemCacheStats() {
        CacheStats componentStats = ComponentTranslationStoreRegistry.getInstance()
                .forModule(ComponentCacheModule.ITEM)
                .getCacheStats();
        CacheStats legacyStats = ItemTemplateCache.getInstance().getCacheStats();
        return combineItemCacheStats(componentStats, legacyStats);
    }

    static CacheStats combineItemCacheStats(CacheStats componentStats, CacheStats legacyStats) {
        return new CacheStats(
                componentStats.translated() + legacyStats.translated(),
                componentStats.total() + legacyStats.total()
        );
    }

    public static Component createStatusLine(
            CacheStats stats,
            boolean hasMissingKeyIssue,
            String animationKey
    ) {
        float percentage = (stats.total() > 0) ? ((float) stats.translated() / stats.total()) * 100 : 100;
        String progressText = String.format(" (%d/%d) - %.0f%%", stats.translated(), stats.total(), percentage);

        Component statusMessage = hasMissingKeyIssue
                ? Component.translatable(KEY_MISMATCH_STATUS_KEY).withStyle(ChatFormatting.RED)
                : Component.translatable(TRANSLATING_STATUS_KEY).withStyle(ChatFormatting.GRAY);

        MutableComponent statusText = AnimationManager.getAnimatedStyledText(statusMessage, animationKey, hasMissingKeyIssue);
        return statusText.append(Component.literal(progressText).withStyle(ChatFormatting.YELLOW));
    }

    public static Component createErrorStatusLine(String errorMessage) {
        return Component.translatable(ERROR_STATUS_KEY, TranslationErrorTextSupport.localizeReason(errorMessage)).withStyle(ChatFormatting.RED);
    }

    public static Component createAnimatedPendingStatusLine(String animationKey) {
        return AnimationManager.getAnimatedStyledText(createTranslatingStatusText(), animationKey, false);
    }

    public static boolean shouldShowStatusLine(
            TooltipTranslationSupport.TooltipProcessingResult processedTooltip,
            CacheStats stats
    ) {
        if (processedTooltip == null || stats == null || processedTooltip.translatableLines() <= 0) {
            return false;
        }

        boolean isAnythingPending = stats.total() > stats.translated();
        return processedTooltip.pending() || processedTooltip.missingKeyIssue() || isAnythingPending;
    }

    public static boolean shouldShowErrorStatusLine(TooltipTranslationSupport.TooltipProcessingResult processedTooltip) {
        return false;
    }

    public static List<Component> appendStatusLineIfNeeded(
            List<Component> tooltip,
            TooltipTranslationSupport.TooltipProcessingResult processedTooltip,
            String animationKey
    ) {
        if (tooltip == null) {
            return null;
        }

        CacheStats stats = getItemCacheStats();
        boolean showStatusLine = shouldShowStatusLine(processedTooltip, stats);
        boolean showErrorStatusLine = shouldShowErrorStatusLine(processedTooltip);
        if (!showStatusLine && !showErrorStatusLine) {
            return tooltip;
        }

        List<Component> tooltipWithStatus = new ArrayList<>(tooltip.size() + 2);
        tooltipWithStatus.addAll(tooltip);
        if (showStatusLine) {
            tooltipWithStatus.add(createStatusLine(stats, processedTooltip.missingKeyIssue(), animationKey));
        }
        if (showErrorStatusLine) {
            tooltipWithStatus.add(createErrorStatusLine(processedTooltip.errorMessage()));
        }
        return tooltipWithStatus;
    }

    public static boolean isMissingKeyIssue(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains(MISSING_KEY_HINT) || lower.contains(KEY_MISMATCH_HINT);
    }

    public static boolean isInternalStatusLine(Component line) {
        if (line == null) {
            return false;
        }

        String content = line.getString();
        return content.startsWith(createTranslatingStatusText().getString())
                || content.startsWith(createKeyMismatchStatusText().getString())
                || content.equals(createErrorStatusLine("").getString())
                || content.startsWith(createErrorStatusLine("").getString());
    }

    private static Component createTranslatingStatusText() {
        return Component.translatable(TRANSLATING_STATUS_KEY).withStyle(ChatFormatting.GRAY);
    }

    private static Component createKeyMismatchStatusText() {
        return Component.translatable(KEY_MISMATCH_STATUS_KEY).withStyle(ChatFormatting.RED);
    }

    public static boolean isInternalGeneratedLine(Component line) {
        return isInternalStatusLine(line) || TooltipRefreshNoticeSupport.isRefreshNoticeLine(line);
    }

    public static List<Component> stripInternalGeneratedLines(List<Component> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        List<Component> sanitized = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            if (!isInternalGeneratedLine(line)) {
                if (sanitized != null) {
                    sanitized.add(line);
                }
                continue;
            }

            if (sanitized == null) {
                sanitized = new ArrayList<>(tooltip.size());
                sanitized.addAll(tooltip.subList(0, i));
            }
        }
        return sanitized == null ? tooltip : sanitized;
    }
}
