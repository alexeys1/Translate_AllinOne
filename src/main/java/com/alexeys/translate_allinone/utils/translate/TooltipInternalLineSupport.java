package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.cache.CacheStats;
import com.alexeys.translate_allinone.utils.cache.ItemTemplateCache;
import com.alexeys.translate_allinone.utils.cache.component.ComponentCacheModule;
import com.alexeys.translate_allinone.utils.cache.component.ComponentTranslationStoreRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TooltipInternalLineSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String TRANSLATING_STATUS_KEY = "text.translate_allinone.item.tooltip_translating";
    private static final String KEY_MISMATCH_STATUS_KEY = "text.translate_allinone.item.tooltip_key_mismatch_retrying";
    private static final String ERROR_STATUS_KEY = "text.translate_allinone.item.tooltip_translation_error";
    private static final long ERROR_DISPLAY_MS = 3_000L;
    private static final long ERROR_QUIET_MS = 5_000L;
    private static final int ERROR_FINGERPRINT_LIMIT = 128;
    private static final int GENERATED_LINE_LIMIT = 256;
    private static final Map<String, Long> ERROR_SINCE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > ERROR_FINGERPRINT_LIMIT;
        }
    };
    private static final Map<String, Boolean> GENERATED_LINES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > GENERATED_LINE_LIMIT;
        }
    };

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
        return registerGeneratedLine(statusText.append(Component.literal(progressText).withStyle(ChatFormatting.YELLOW)));
    }

    public static Component createErrorStatusLine(String errorMessage) {
        return registerGeneratedLine(Component.translatable(
                ERROR_STATUS_KEY,
                TranslationErrorTextSupport.localizeReason(errorMessage)
        ).withStyle(ChatFormatting.RED));
    }

    public static Component createAnimatedPendingStatusLine(String animationKey) {
        return registerGeneratedLine(AnimationManager.getAnimatedStyledText(createTranslatingStatusText(), animationKey, false));
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
        if (processedTooltip.errorMessage().isBlank()) {
            return false;
        }
        return shouldShowTooltipError(tooltipErrorFingerprint(processedTooltip));
    }

    private static boolean shouldShowTooltipError(String tooltipFingerprint) {
        long now = System.currentTimeMillis();
        synchronized (ERROR_SINCE) {
            Long since = ERROR_SINCE.get(tooltipFingerprint);
            if (since == null || now - since >= ERROR_DISPLAY_MS + ERROR_QUIET_MS) {
                since = now;
                ERROR_SINCE.put(tooltipFingerprint, now);
            }
            return now - since < ERROR_DISPLAY_MS;
        }
    }

    private static String tooltipErrorFingerprint(TooltipTranslationSupport.TooltipProcessingResult processedTooltip) {
        List<Component> lines = processedTooltip.translatedLines();
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Component line : lines) {
            if (line != null) {
                builder.append(line.getString()).append('\n');
            }
        }
        return builder.toString();
    }

    public static List<Component> appendStatusLineIfNeeded(
            List<Component> tooltip,
            TooltipTranslationSupport.TooltipProcessingResult processedTooltip,
            String animationKey
    ) {
        if (tooltip == null) {
            return null;
        }

        List<Component> base = withoutInternalStatusLines(tooltip);
        CacheStats stats = getItemCacheStats();
        boolean showStatusLine = shouldShowStatusLine(processedTooltip, stats);
        boolean showErrorStatusLine = shouldShowErrorStatusLine(processedTooltip);
        if (!showStatusLine && !showErrorStatusLine) {
            return base;
        }

        List<Component> tooltipWithStatus = new ArrayList<>(base.size() + 1);
        tooltipWithStatus.addAll(base);
        if (showErrorStatusLine) {
            tooltipWithStatus.add(createErrorStatusLine(processedTooltip.errorMessage()));
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

    public static boolean isInternalStatusLine(Component line) {
        return line != null && isInternalStatusLineText(line.getString());
    }

    private static boolean isInternalStatusLineText(String plainText) {
        synchronized (GENERATED_LINES) {
            return GENERATED_LINES.containsKey(plainText);
        }
    }

    private static <T extends Component> T registerGeneratedLine(T line) {
        String content = line.getString();
        synchronized (GENERATED_LINES) {
            GENERATED_LINES.put(content, Boolean.TRUE);
        }
        return line;
    }

    private static Component createTranslatingStatusText() {
        return Component.translatable(TRANSLATING_STATUS_KEY).withStyle(ChatFormatting.GRAY);
    }

    public static boolean isInternalGeneratedLine(Component line) {
        return isInternalStatusLine(line) || TooltipRefreshNoticeSupport.isRefreshNoticeLine(line);
    }

    public static List<Component> withoutInternalStatusLines(List<Component> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        List<Component> sanitized = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            if (!isInternalStatusLine(line)) {
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
