package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipPlan;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteKind;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteSegment;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TooltipTranslationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");

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

    public record TranslatedTooltipBuildResult(
            List<Text> translatedTooltip,
            boolean locallyStableForRecentGuard
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

    public static List<Text> buildTranslatedTooltip(List<Text> originalTooltip, String animationKey) {
        return buildTranslatedTooltipResult(originalTooltip, animationKey).translatedTooltip();
    }

    public static TranslatedTooltipBuildResult buildTranslatedTooltipResult(List<Text> originalTooltip, String animationKey) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return new TranslatedTooltipBuildResult(originalTooltip, false);
        }

        List<Text> tooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(originalTooltip);
        if (tooltip.isEmpty()) {
            return new TranslatedTooltipBuildResult(tooltip, false);
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return new TranslatedTooltipBuildResult(tooltip, false);
        }

        boolean wynnCompatibilityEnabled = config.wynn_item_compatibility;
        TooltipPlan tooltipPlan = TooltipRoutePlanner.planTooltip(tooltip, config, wynnCompatibilityEnabled);
        TooltipRefreshNoticeSupport.maybeForceRefreshCurrentTooltip(tooltipPlan.translationTemplateKeys(), config);
        boolean showRefreshNotice = TooltipRefreshNoticeSupport.shouldShowRefreshNotice(tooltipPlan.translationTemplateKeys());

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return new TranslatedTooltipBuildResult(
                    TooltipRefreshNoticeSupport.appendRefreshNoticeLine(tooltip, showRefreshNotice),
                    false
            );
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
            List<Text> mirroredTooltip = TooltipInternalLineSupport.appendStatusLineIfNeeded(
                    new ArrayList<>(processedTooltip.translatedLines()),
                    processedTooltip,
                    animationKey
            );
            boolean locallyStableForRecentGuard = !processedTooltip.pending() && !processedTooltip.missingKeyIssue();

            TooltipTextMatcherSupport.logTooltipPassIfDev(
                    config,
                    emitDevLog,
                    "screen-mirror",
                    tooltip.size(),
                    processedTooltip.translatableLines(),
                    tooltipStartedAtNanos
            );
            return new TranslatedTooltipBuildResult(
                    TooltipRefreshNoticeSupport.appendRefreshNoticeLine(mirroredTooltip, showRefreshNotice),
                    locallyStableForRecentGuard
            );
        } catch (Exception e) {
            LOGGER.error("Failed to build translated tooltip", e);
            return new TranslatedTooltipBuildResult(
                    TooltipRefreshNoticeSupport.appendRefreshNoticeLine(tooltip, showRefreshNotice),
                    false
            );
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
}
