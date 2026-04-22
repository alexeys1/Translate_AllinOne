package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TooltipInternalLineSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String TRANSLATING_STATUS_PREFIX = "Translating...";
    private static final String KEY_MISMATCH_STATUS_PREFIX = "Item translation key mismatch, retrying...";

    private TooltipInternalLineSupport() {
    }

    public static Text createStatusLine(
            ItemTemplateCache.CacheStats stats,
            boolean hasMissingKeyIssue,
            String animationKey
    ) {
        float percentage = (stats.total() > 0) ? ((float) stats.translated() / stats.total()) * 100 : 100;
        String progressText = String.format(" (%d/%d) - %.0f%%", stats.translated(), stats.total(), percentage);

        Text statusMessage = hasMissingKeyIssue
                ? Text.literal(KEY_MISMATCH_STATUS_PREFIX).formatted(Formatting.RED)
                : Text.literal(TRANSLATING_STATUS_PREFIX).formatted(Formatting.GRAY);

        MutableText statusText = AnimationManager.getAnimatedStyledText(statusMessage, animationKey, hasMissingKeyIssue);
        return statusText.append(Text.literal(progressText).formatted(Formatting.YELLOW));
    }

    public static boolean shouldShowStatusLine(
            TooltipTranslationSupport.TooltipProcessingResult processedTooltip,
            ItemTemplateCache.CacheStats stats
    ) {
        if (processedTooltip == null || stats == null || processedTooltip.translatableLines() <= 0) {
            return false;
        }

        boolean isAnythingPending = stats.total() > stats.translated();
        return processedTooltip.pending() || processedTooltip.missingKeyIssue() || isAnythingPending;
    }

    public static List<Text> appendStatusLineIfNeeded(
            List<Text> tooltip,
            TooltipTranslationSupport.TooltipProcessingResult processedTooltip,
            String animationKey
    ) {
        if (tooltip == null) {
            return null;
        }

        ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
        if (!shouldShowStatusLine(processedTooltip, stats)) {
            return tooltip;
        }

        List<Text> tooltipWithStatus = new ArrayList<>(tooltip.size() + 1);
        tooltipWithStatus.addAll(tooltip);
        tooltipWithStatus.add(createStatusLine(stats, processedTooltip.missingKeyIssue(), animationKey));
        return tooltipWithStatus;
    }

    public static boolean isMissingKeyIssue(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains(MISSING_KEY_HINT) || lower.contains(KEY_MISMATCH_HINT);
    }

    public static boolean isInternalStatusLine(Text line) {
        if (line == null) {
            return false;
        }

        String content = line.getString();
        return content.startsWith(TRANSLATING_STATUS_PREFIX)
                || content.startsWith(KEY_MISMATCH_STATUS_PREFIX);
    }

    public static boolean isInternalGeneratedLine(Text line) {
        return isInternalStatusLine(line) || TooltipRefreshNoticeSupport.isRefreshNoticeLine(line);
    }

    public static List<Text> stripInternalGeneratedLines(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        List<Text> sanitized = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);
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
