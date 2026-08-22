package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.cache.CacheStats;
import com.alexeys.translate_allinone.utils.cache.ItemTemplateCache;
import com.alexeys.translate_allinone.utils.cache.component.ComponentCacheModule;
import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import com.alexeys.translate_allinone.utils.componentjson.NoRoutedModelErrorSupport;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static Text createStatusLine(
            CacheStats stats,
            boolean hasMissingKeyIssue,
            String animationKey
    ) {
        float percentage = (stats.total() > 0) ? ((float) stats.translated() / stats.total()) * 100 : 100;
        String progressText = String.format(" (%d/%d) - %.0f%%", stats.translated(), stats.total(), percentage);

        Text statusMessage = hasMissingKeyIssue
                ? Text.translatable(KEY_MISMATCH_STATUS_KEY).formatted(Formatting.RED)
                : Text.translatable(TRANSLATING_STATUS_KEY).formatted(Formatting.GRAY);

        MutableText statusText = AnimationManager.getAnimatedStyledText(statusMessage, animationKey, hasMissingKeyIssue);
        return statusText.append(Text.literal(progressText).formatted(Formatting.YELLOW));
    }

    public static Text createErrorStatusLine(String errorMessage) {
        return Text.translatable(ERROR_STATUS_KEY, TranslationErrorTextSupport.localizeReason(errorMessage)).formatted(Formatting.RED);
    }

    public static Text createAnimatedPendingStatusLine(String animationKey) {
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
        if (processedTooltip == null || processedTooltip.translatableLines() <= 0) {
            return false;
        }
        if (!NoRoutedModelErrorSupport.isTooltipNoRoutedError(processedTooltip.errorMessage())) {
            return false;
        }
        return NoRoutedModelErrorSupport.shouldShowTooltipError(tooltipErrorFingerprint(processedTooltip));
    }

    private static String tooltipErrorFingerprint(TooltipTranslationSupport.TooltipProcessingResult processedTooltip) {
        List<Text> lines = processedTooltip.translatedLines();
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Text line : lines) {
            if (line != null) {
                builder.append(line.getString()).append('\n');
            }
        }
        return builder.toString();
    }

    public static List<Text> appendStatusLineIfNeeded(
            List<Text> tooltip,
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

        List<Text> tooltipWithStatus = new ArrayList<>(tooltip.size() + 2);
        tooltipWithStatus.addAll(tooltip);
        if (showErrorStatusLine) {
            tooltipWithStatus.add(createErrorStatusLine(NoRoutedModelErrorSupport.tooltipErrorMessage()));
        } else if (showStatusLine) {
            tooltipWithStatus.add(createStatusLine(stats, processedTooltip.missingKeyIssue(), animationKey));
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

    public static boolean isInternalStatusLine(Text line) {
        if (line == null) {
            return false;
        }

        String content = line.getString();
        return content.startsWith(createTranslatingStatusText().getString())
                || content.startsWith(createKeyMismatchStatusText().getString())
                || content.equals(createErrorStatusLine("").getString())
                || content.startsWith(createErrorStatusLine("").getString());
    }

    private static Text createTranslatingStatusText() {
        return Text.translatable(TRANSLATING_STATUS_KEY).formatted(Formatting.GRAY);
    }

    private static Text createKeyMismatchStatusText() {
        return Text.translatable(KEY_MISMATCH_STATUS_KEY).formatted(Formatting.RED);
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
