package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipPlan;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteKind;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteSegment;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TooltipTranslationSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String TOOLTIP_REFRESH_NOTICE_KEY = "text.translate_allinone.item.tooltip_refresh_forced";
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final StyleSpriteSource.Font WYNNCRAFT_TOOLTIP_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "language/wynncraft"));
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");
    private static final Set<Integer> refreshedTooltipSignaturesThisHold = new HashSet<>();
    private static volatile int refreshNoticeTooltipSignature = 0;
    private static volatile long refreshNoticeExpiresAtMillis = 0L;

    private TooltipTranslationSupport() {
    }

    public record TooltipLineResult(Text translatedLine, boolean pending, boolean missingKeyIssue) {
    }

    public record TooltipProcessingResult(
            List<Text> translatedLines,
            int translatableLines,
            boolean pending,
            boolean missingKeyIssue
    ) {
    }

    public static boolean shouldShowOriginal(ItemTranslateConfig.KeybindingMode mode, boolean isKeyPressed) {
        return switch (mode) {
            case HOLD_TO_TRANSLATE -> !isKeyPressed;
            case HOLD_TO_SEE_ORIGINAL -> isKeyPressed;
            case DISABLED -> false;
        };
    }

    public static TooltipLineResult translateLine(Text line) {
        return translateLine(line, false);
    }

    public static TooltipLineResult translateLine(Text line, boolean useTagStylePreservation) {
        return TooltipTemplateRuntime.translateLine(line, useTagStylePreservation);
    }

    public static void maybeForceRefreshCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keysToRefresh = TooltipRoutePlanner.planTooltip(
                tooltip,
                config,
                config != null && config.wynn_item_compatibility
        ).translationTemplateKeys();
        maybeForceRefreshCurrentTooltip(keysToRefresh, config);
    }

    private static void maybeForceRefreshCurrentTooltip(Set<String> keysToRefresh, ItemTranslateConfig config) {
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

    public static List<Text> buildTranslatedTooltip(List<Text> originalTooltip, String animationKey) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return originalTooltip;
        }

        List<Text> tooltip = stripInternalGeneratedLines(originalTooltip);
        if (tooltip.isEmpty()) {
            return tooltip;
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return tooltip;
        }

        boolean wynnCompatibilityEnabled = config.wynn_item_compatibility;
        TooltipPlan tooltipPlan = TooltipRoutePlanner.planTooltip(tooltip, config, wynnCompatibilityEnabled);
        maybeForceRefreshCurrentTooltip(tooltipPlan.translationTemplateKeys(), config);
        boolean showRefreshNotice = shouldShowRefreshNotice(tooltipPlan.translationTemplateKeys());

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "screen-mirror", tooltip);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
        TooltipRoutePlanner.logLineDecisionsIfDev(tooltipPlan, config, emitDevLog, "screen-mirror");

        try {
            TooltipProcessingResult processedTooltip = processTooltipPlan(
                    tooltipPlan,
                    config,
                    wynnCompatibilityEnabled,
                    emitDevLog,
                    "screen-mirror"
            );
            List<Text> mirroredTooltip = new ArrayList<>(processedTooltip.translatedLines());

            if (processedTooltip.translatableLines() > 0) {
                ItemTemplateCache.CacheStats stats = ItemTemplateCache.getInstance().getCacheStats();
                boolean isAnythingPending = stats.total() > stats.translated();
                boolean shouldShowStatus = processedTooltip.pending() || processedTooltip.missingKeyIssue() || isAnythingPending;

                if (shouldShowStatus) {
                    mirroredTooltip.add(createStatusLine(
                            stats,
                            processedTooltip.missingKeyIssue(),
                            animationKey
                    ));
                }
            }

            TooltipTextMatcherSupport.logTooltipPassIfDev(
                    config,
                    emitDevLog,
                    "screen-mirror",
                    tooltip.size(),
                    processedTooltip.translatableLines(),
                    tooltipStartedAtNanos
            );
            return appendRefreshNoticeLine(mirroredTooltip, showRefreshNotice);
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip", e);
            return appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }
    }

    public static TooltipProcessingResult processTooltipLines(
            List<Text> tooltip,
            ItemTranslateConfig config,
            boolean useTagStylePreservation,
            boolean emitDevLog,
            String devSource
    ) {
        TooltipPlan tooltipPlan = TooltipRoutePlanner.planTooltip(tooltip, config, useTagStylePreservation);
        TooltipRoutePlanner.logLineDecisionsIfDev(tooltipPlan, config, emitDevLog, devSource);
        return processTooltipPlan(tooltipPlan, config, useTagStylePreservation, emitDevLog, devSource);
    }

    private static TooltipProcessingResult processTooltipPlan(
            TooltipPlan tooltipPlan,
            ItemTranslateConfig config,
            boolean useTagStylePreservation,
            boolean emitDevLog,
            String devSource
    ) {
        if (tooltipPlan == null || tooltipPlan.segments() == null || tooltipPlan.segments().isEmpty()) {
            return new TooltipProcessingResult(List.of(), 0, false, false);
        }

        List<Text> translatedLines = new ArrayList<>(tooltipPlan.segments().size());
        int translatableLines = 0;
        boolean hasPending = false;
        boolean hasMissingKeyIssue = false;

        for (TooltipRouteSegment segment : tooltipPlan.segments()) {
            if (segment.kind() == TooltipRouteKind.PASSTHROUGH) {
                translatedLines.add(segment.candidate() == null ? null : segment.candidate().line());
                continue;
            }

            translatableLines += segment.translatableLineCount();
            if (segment.kind() == TooltipRouteKind.PARAGRAPH_BLOCK) {
                TooltipParagraphBlock paragraphBlock = segment.paragraphBlock();
                long blockStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
                TooltipParagraphSupport.ParagraphTranslationAttempt blockAttempt = TooltipParagraphSupport.translateParagraphBlock(
                        paragraphBlock,
                        config,
                        emitDevLog,
                        devSource
                );
                for (TooltipLineResult lineResult : blockAttempt.lineResults()) {
                    if (lineResult.pending()) {
                        hasPending = true;
                    }
                    if (lineResult.missingKeyIssue()) {
                        hasMissingKeyIssue = true;
                    }
                    translatedLines.add(lineResult.translatedLine());
                }

                int loggableLineCount = Math.min(blockAttempt.lineResults().size(), paragraphBlock.preparedLines().size());
                for (int offset = 0; offset < loggableLineCount; offset++) {
                    TooltipLineResult lineResult = blockAttempt.lineResults().get(offset);
                    TooltipTextMatcherSupport.logLineTranslationIfDev(
                            config,
                            emitDevLog,
                            devSource,
                            paragraphBlock.startLineIndex() + offset,
                            lineResult,
                            "paragraph-block",
                            "paragraphKey=" + (paragraphBlock.paragraphTemplate().translationTemplateKey() == null
                                    ? ""
                                    : paragraphBlock.paragraphTemplate().translationTemplateKey()),
                            blockStartedAtNanos
                    );
                }
                if (blockAttempt.pending()) {
                    hasPending = true;
                }
                if (blockAttempt.missingKeyIssue()) {
                    hasMissingKeyIssue = true;
                }
                continue;
            }

            if (segment.candidate() == null) {
                continue;
            }

            long lineStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
            TooltipStructuredCaptureSupport.StructuredTooltipLineResult structuredLineResult = null;
            TooltipLineResult lineResult;
            String route;
            String detail;

            if (segment.kind() == TooltipRouteKind.STRUCTURED_LINE) {
                structuredLineResult = TooltipStructuredCaptureSupport.tryTranslateStructuredLine(
                        segment.candidate().line(),
                        useTagStylePreservation
                );
            }

            if (structuredLineResult != null) {
                lineResult = structuredLineResult.lineResult();
                route = "capture";
                detail = structuredLineResult.debugSummary();
            } else {
                PreparedTooltipTemplate preparedTemplate = segment.preparedTemplate();
                if (preparedTemplate == null) {
                    preparedTemplate = TooltipTemplateRuntime.prepareTemplate(
                            segment.candidate().line(),
                            useTagStylePreservation
                    );
                }
                lineResult = TooltipTemplateRuntime.translatePreparedTemplate(preparedTemplate);
                route = "line-template";
                detail = "templateKey=" + (preparedTemplate.translationTemplateKey() == null
                        ? ""
                        : preparedTemplate.translationTemplateKey());
            }

            if (lineResult.pending()) {
                hasPending = true;
            }
            if (lineResult.missingKeyIssue()) {
                hasMissingKeyIssue = true;
            }
            translatedLines.add(lineResult.translatedLine());
            TooltipTextMatcherSupport.logLineTranslationIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    segment.candidate().lineIndex(),
                    lineResult,
                    route,
                    detail,
                    lineStartedAtNanos
            );
        }

        return new TooltipProcessingResult(translatedLines, translatableLines, hasPending, hasMissingKeyIssue);
    }

    public static Text createStatusLine(
            ItemTemplateCache.CacheStats stats,
            boolean hasMissingKeyIssue,
            String animationKey
    ) {
        float percentage = (stats.total() > 0) ? ((float) stats.translated() / stats.total()) * 100 : 100;
        String progressText = String.format(" (%d/%d) - %.0f%%", stats.translated(), stats.total(), percentage);

        Text statusMessage = hasMissingKeyIssue
                ? Text.literal("Item translation key mismatch, retrying...").formatted(Formatting.RED)
                : Text.literal("Translating...").formatted(Formatting.GRAY);

        MutableText statusText = AnimationManager.getAnimatedStyledText(statusMessage, animationKey, hasMissingKeyIssue);
        return statusText.append(Text.literal(progressText).formatted(Formatting.YELLOW));
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
        return content.startsWith("Translating...")
                || content.startsWith("Item translation key mismatch, retrying...");
    }

    public static boolean isRefreshNoticeLine(Text line) {
        if (line == null) {
            return false;
        }
        return createRefreshNoticeLine().getString().equals(line.getString());
    }

    public static boolean isInternalGeneratedLine(Text line) {
        return isInternalStatusLine(line) || isRefreshNoticeLine(line);
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

    public static boolean looksLikeDedicatedWynnmodTooltip(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return false;
        }

        List<Text> sanitizedTooltip = stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return false;
        }

        boolean hasWynncraftFont = false;
        boolean hasMeaningfulLine = false;
        for (Text line : sanitizedTooltip) {
            if (line == null) {
                continue;
            }

            if (!hasMeaningfulLine && TooltipTextMatcherSupport.hasMeaningfulContent(line)) {
                hasMeaningfulLine = true;
            }

            if (hasWynncraftFont) {
                continue;
            }

            for (FlatNode node : FlatNode.compact(FlatNode.flatten(line))) {
                if (node.style() != null && WYNNCRAFT_TOOLTIP_FONT.equals(node.style().getFont())) {
                    hasWynncraftFont = true;
                    break;
                }
            }
        }
        return hasWynncraftFont && hasMeaningfulLine;
    }

    public static boolean shouldShowRefreshNotice(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keys = TooltipRoutePlanner.planTooltip(
                tooltip,
                config,
                config != null && config.wynn_item_compatibility
        ).translationTemplateKeys();
        return shouldShowRefreshNotice(keys);
    }

    private static boolean shouldShowRefreshNotice(Set<String> keys) {
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
