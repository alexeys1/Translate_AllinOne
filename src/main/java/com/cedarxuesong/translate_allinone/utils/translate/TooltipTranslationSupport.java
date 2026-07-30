package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipPlan;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteKind;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipRouteSegment;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public final class TooltipTranslationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");

    private static final class TooltipFrameCache {
        int fingerprint;
        boolean useTagStylePreservation;
        TooltipPlan plan;
        Set<String> remoteTranslationTemplateKeys;
        boolean hasRemoteTranslationTemplateKeys;
        boolean decorativeTooltipContext;

        boolean match(int fp, boolean preserveStyle) {
            return fp != 0 && fingerprint == fp && useTagStylePreservation == preserveStyle && plan != null;
        }

        void setPlan(int fp, boolean preserveStyle, TooltipPlan p, boolean decorative) {
            this.fingerprint = fp;
            this.useTagStylePreservation = preserveStyle;
            this.plan = p;
            this.remoteTranslationTemplateKeys = null;
            this.hasRemoteTranslationTemplateKeys = false;
            this.decorativeTooltipContext = decorative;
        }

        void setPlanWithRemoteKeys(
                int fp,
                boolean preserveStyle,
                TooltipPlan p,
                Set<String> keys,
                boolean decorative
        ) {
            this.fingerprint = fp;
            this.useTagStylePreservation = preserveStyle;
            this.plan = p;
            this.remoteTranslationTemplateKeys = keys == null ? Set.of() : keys;
            this.hasRemoteTranslationTemplateKeys = true;
            this.decorativeTooltipContext = decorative;
        }
    }

    private static final TooltipFrameCache FRAME_CACHE = new TooltipFrameCache();
    private static final AtomicLong TOOLTIP_PLAN_CACHE_HITS = new AtomicLong();
    private static final AtomicLong TOOLTIP_PLAN_CACHE_MISSES = new AtomicLong();
    private static final AtomicLong REMOTE_KEYS_CACHE_HITS = new AtomicLong();
    private static final AtomicLong REMOTE_KEYS_CACHE_MISSES = new AtomicLong();

    private TooltipTranslationSupport() {
    }

    public record TooltipLineResult(Component translatedLine, boolean pending, boolean missingKeyIssue, String errorMessage) {
        public TooltipLineResult(Component translatedLine, boolean pending, boolean missingKeyIssue) {
            this(translatedLine, pending, missingKeyIssue, "");
        }

        public TooltipLineResult {
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    public record TooltipProcessingResult(
            List<Component> translatedLines,
            int translatableLines,
            boolean pending,
            boolean missingKeyIssue,
            String errorMessage
    ) {
        public TooltipProcessingResult(
                List<Component> translatedLines,
                int translatableLines,
                boolean pending,
                boolean missingKeyIssue
        ) {
            this(translatedLines, translatableLines, pending, missingKeyIssue, "");
        }

        public TooltipProcessingResult {
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    public record TranslatedTooltipBuildResult(
            List<Component> translatedTooltip,
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

    public static TooltipLineResult translateLine(Component line) {
        return translateLine(line, false);
    }

    public static TooltipLineResult translateLine(Component line, boolean useTagStylePreservation) {
        return TooltipTemplateRuntime.translateLine(line, useTagStylePreservation);
    }

    public static List<Component> buildTranslatedTooltip(List<Component> originalTooltip, String animationKey) {
        return buildTranslatedTooltipResult(originalTooltip, animationKey).translatedTooltip();
    }

    public static Set<String> collectTranslationTemplateKeys(List<Component> tooltip, ItemTranslateConfig config) {
        if (tooltip == null || tooltip.isEmpty() || config == null || !config.enabled) {
            return Set.of();
        }

        List<Component> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return Set.of();
        }

        boolean decorativeTooltipContext = TooltipDecorativeContextSupport.isDecorativeTooltipContext(sanitizedTooltip);
        return TooltipRoutePlanner.planTooltip(sanitizedTooltip, config, decorativeTooltipContext).translationTemplateKeys();
    }

    static Set<String> collectRemoteTranslationTemplateKeys(List<Component> tooltip, ItemTranslateConfig config) {
        if (tooltip == null || tooltip.isEmpty() || config == null || !config.enabled) {
            return Set.of();
        }

        List<Component> sanitizedTooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(tooltip);
        if (sanitizedTooltip == null || sanitizedTooltip.isEmpty()) {
            return Set.of();
        }

        boolean decorativeTooltipContext = TooltipDecorativeContextSupport.isDecorativeTooltipContext(sanitizedTooltip);
        TooltipPlan tooltipPlan = TooltipRoutePlanner.planTooltip(sanitizedTooltip, config, decorativeTooltipContext);
        return collectRemoteTranslationTemplateKeys(tooltipPlan, decorativeTooltipContext, config);
    }

    static void queueRemoteTranslationTemplateKeys(Set<String> remoteTranslationTemplateKeys) {
        if (remoteTranslationTemplateKeys == null || remoteTranslationTemplateKeys.isEmpty()) {
            return;
        }
    }

    static void queueRemoteTranslationTemplateKeys(
            Set<String> remoteTranslationTemplateKeys,
            Consumer<String> queueKey
    ) {
        if (remoteTranslationTemplateKeys == null || remoteTranslationTemplateKeys.isEmpty()) {
            return;
        }
        if (queueKey == null) {
            return;
        }

        for (String key : remoteTranslationTemplateKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            queueKey.accept(key);
        }
    }

    static int forceRefreshComponentCaches(TooltipPlan tooltipPlan, ItemTranslateConfig config) {
        if (tooltipPlan == null
                || tooltipPlan.segments() == null
                || tooltipPlan.segments().isEmpty()
                || config == null) {
            return 0;
        }

        int refreshed = 0;
        for (TooltipRouteSegment segment : tooltipPlan.segments()) {
            if (segment == null || segment.kind() == null) {
                continue;
            }
            switch (segment.kind()) {
                case PASSTHROUGH -> {
                }
                case PARAGRAPH_BLOCK -> {
                    if (!TooltipParagraphSupport.hasAcceptedLocalDictionaryTranslation(segment.paragraphBlock())) {
                        refreshed += TooltipComponentTranslationSupport.forceRefreshParagraphBlock(
                                segment.paragraphBlock(),
                                config
                        );
                    }
                }
                case STRUCTURED_LINE -> {
                    if (segment.candidate() == null) {
                        continue;
                    }
                    boolean preserveStyles = segment.preparedTemplate() != null
                            && segment.preparedTemplate().useTagStylePreservation();
                    refreshed += TooltipStructuredCaptureSupport.forceRefreshStructuredLine(
                            segment.candidate().line(),
                            preserveStyles,
                            config
                    );
                    if (!TooltipTemplateRuntime.hasLocalDictionaryTranslation(segment.candidate().line())) {
                        PreparedTooltipTemplate prepared = segment.preparedTemplate() == null
                                ? TooltipTemplateRuntime.prepareTemplate(
                                segment.candidate().line(),
                                preserveStyles
                        )
                                : segment.preparedTemplate();
                        refreshed += TooltipComponentTranslationSupport.forceRefreshPreparedLine(
                                prepared,
                                com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute.TOOLTIP_LINE,
                                "tooltip:line",
                                "line-v2",
                                config
                        );
                    }
                }
                case LINE_TEMPLATE -> {
                    if (segment.candidate() == null
                            || TooltipTemplateRuntime.hasLocalDictionaryTranslation(segment.candidate().line())) {
                        continue;
                    }
                    PreparedTooltipTemplate prepared = segment.preparedTemplate() == null
                            ? TooltipTemplateRuntime.prepareTemplate(
                            segment.candidate().line(),
                            false
                    )
                            : segment.preparedTemplate();
                    refreshed += TooltipComponentTranslationSupport.forceRefreshPreparedLine(
                            prepared,
                            com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute.TOOLTIP_LINE,
                            "tooltip:line",
                            "line-v2",
                            config
                    );
                }
            }
        }
        return refreshed;
    }

    public static TranslatedTooltipBuildResult buildTranslatedTooltipResult(List<Component> originalTooltip, String animationKey) {
        if (originalTooltip == null || originalTooltip.isEmpty()) {
            return new TranslatedTooltipBuildResult(originalTooltip, false);
        }

        List<Component> tooltip = TooltipInternalLineSupport.stripInternalGeneratedLines(originalTooltip);
        if (tooltip.isEmpty()) {
            return new TranslatedTooltipBuildResult(tooltip, false);
        }

        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (!config.enabled) {
            return new TranslatedTooltipBuildResult(tooltip, false);
        }

        boolean decorativeTooltipContext = TooltipDecorativeContextSupport.isDecorativeTooltipContext(tooltip);
        int fingerprint = computeTooltipFingerprint(tooltip, config, decorativeTooltipContext);
        TooltipPlan tooltipPlan;
        Set<String> remoteTranslationTemplateKeys;
        String planCacheResult;
        String remoteKeysCacheResult;

        if (FRAME_CACHE.match(fingerprint, decorativeTooltipContext)) {
            TOOLTIP_PLAN_CACHE_HITS.incrementAndGet();
            planCacheResult = "hit";
            tooltipPlan = FRAME_CACHE.plan;
            decorativeTooltipContext = FRAME_CACHE.decorativeTooltipContext;
            if (FRAME_CACHE.hasRemoteTranslationTemplateKeys) {
                REMOTE_KEYS_CACHE_HITS.incrementAndGet();
                remoteKeysCacheResult = "hit";
                remoteTranslationTemplateKeys = FRAME_CACHE.remoteTranslationTemplateKeys;
            } else {
                REMOTE_KEYS_CACHE_MISSES.incrementAndGet();
                remoteKeysCacheResult = "miss";
                remoteTranslationTemplateKeys = collectRemoteTranslationTemplateKeys(
                        tooltipPlan,
                        decorativeTooltipContext,
                        config
                );
                FRAME_CACHE.setPlanWithRemoteKeys(
                        fingerprint,
                        decorativeTooltipContext,
                        tooltipPlan,
                        remoteTranslationTemplateKeys,
                        decorativeTooltipContext
                );
            }
        } else {
            TOOLTIP_PLAN_CACHE_MISSES.incrementAndGet();
            REMOTE_KEYS_CACHE_MISSES.incrementAndGet();
            planCacheResult = "miss";
            remoteKeysCacheResult = "miss";
            tooltipPlan = TooltipRoutePlanner.planTooltip(tooltip, config, decorativeTooltipContext);
            remoteTranslationTemplateKeys = collectRemoteTranslationTemplateKeys(
                    tooltipPlan,
                    decorativeTooltipContext,
                    config
            );
            FRAME_CACHE.setPlanWithRemoteKeys(
                    fingerprint,
                    decorativeTooltipContext,
                    tooltipPlan,
                    remoteTranslationTemplateKeys,
                    decorativeTooltipContext
            );
        }

        Set<String> refreshKeys = tooltipPlan.translationTemplateKeys();
        TooltipRefreshNoticeSupport.maybeForceRefreshCurrentTooltip(tooltipPlan, refreshKeys, config);
        boolean showRefreshNotice = TooltipRefreshNoticeSupport.shouldShowRefreshNotice(refreshKeys);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            List<Component> tooltipWithNotice = TooltipRefreshNoticeSupport.appendRefreshNoticeLine(tooltip, showRefreshNotice);
            return new TranslatedTooltipBuildResult(tooltipWithNotice, false);
        }

        if (!TooltipRecentRenderGuardSupport.shouldSkipDuplicateRender(tooltip, showRefreshNotice)) {
            if (config.debug.enabled) {
                LOGGER.info(
                        "[ItemDev:llm-enqueue] source=\"{}\" keyCount={}",
                        "screen-mirror",
                        remoteTranslationTemplateKeys.size()
                );
            }
            queueRemoteTranslationTemplateKeys(remoteTranslationTemplateKeys);
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "screen-mirror", tooltip);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
        TooltipRoutePlanner.logLineDecisionsIfDev(tooltipPlan, config, emitDevLog, "screen-mirror");
        logFrameCacheStatsIfDev(config, emitDevLog, "screen-mirror", planCacheResult, remoteKeysCacheResult);

        try {
            TooltipProcessingResult processedTooltip = processTooltipPlan(
                    tooltipPlan,
                    config,
                    decorativeTooltipContext,
                    emitDevLog,
                    "screen-mirror"
            );
            List<Component> mirroredTooltip = TooltipInternalLineSupport.appendStatusLineIfNeeded(
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
            List<Component> tooltip,
            ItemTranslateConfig config,
            boolean useTagStylePreservation,
            boolean emitDevLog,
            String devSource
    ) {
        int fingerprint = computeTooltipFingerprint(tooltip, config, useTagStylePreservation);
        TooltipPlan tooltipPlan;
        String planCacheResult;
        if (FRAME_CACHE.match(fingerprint, useTagStylePreservation)) {
            TOOLTIP_PLAN_CACHE_HITS.incrementAndGet();
            planCacheResult = "hit";
            tooltipPlan = FRAME_CACHE.plan;
        } else {
            TOOLTIP_PLAN_CACHE_MISSES.incrementAndGet();
            planCacheResult = "miss";
            boolean decorativeTooltipContext = useTagStylePreservation
                    || TooltipDecorativeContextSupport.isDecorativeTooltipContext(tooltip);
            tooltipPlan = TooltipRoutePlanner.planTooltip(tooltip, config, useTagStylePreservation);
            FRAME_CACHE.setPlan(fingerprint, useTagStylePreservation, tooltipPlan, decorativeTooltipContext);
        }
        TooltipRoutePlanner.logLineDecisionsIfDev(tooltipPlan, config, emitDevLog, devSource);
        logFrameCacheStatsIfDev(config, emitDevLog, devSource, planCacheResult, "n/a");
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

        List<Component> translatedLines = new ArrayList<>(tooltipPlan.segments().size());
        int translatableLines = 0;
        boolean hasPending = false;
        boolean hasMissingKeyIssue = false;
        String errorMessage = "";

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
                    if (errorMessage.isBlank() && !lineResult.errorMessage().isBlank()) {
                        errorMessage = lineResult.errorMessage();
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
                            paragraphBlock.preparedLines().get(offset).sourceLine(),
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
                        useTagStylePreservation,
                        config
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
                TooltipParagraphSupport.logLineStyleMapIfDev(
                        config,
                        emitDevLog,
                        devSource,
                        segment.candidate().lineIndex(),
                        "line-template",
                        preparedTemplate
                );
                TooltipLineResult componentLineResult = null;
                if (!TooltipTemplateRuntime.hasLocalDictionaryTranslation(segment.candidate().line())) {
                    componentLineResult = TooltipComponentTranslationSupport.translatePreparedLine(
                            preparedTemplate,
                            com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute.TOOLTIP_LINE,
                            "tooltip:line",
                            "line-v2",
                            config
                    );
                }
                lineResult = componentLineResult == null
                        ? new TooltipLineResult(segment.candidate().line(), false, false)
                        : componentLineResult;
                route = "line-template";
                detail = "templateKey=" + (preparedTemplate.translationTemplateKey() == null
                        ? ""
                        : preparedTemplate.translationTemplateKey())
                        + ", " + TooltipTemplateRuntime.describeLocalDictionaryLookup(segment.candidate().line());
            }

            if (lineResult.pending()) {
                hasPending = true;
            }
            if (lineResult.missingKeyIssue()) {
                hasMissingKeyIssue = true;
            }
            if (errorMessage.isBlank() && !lineResult.errorMessage().isBlank()) {
                errorMessage = lineResult.errorMessage();
            }
            translatedLines.add(lineResult.translatedLine());
            TooltipTextMatcherSupport.logLineTranslationIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    segment.candidate().lineIndex(),
                    segment.candidate().line(),
                    lineResult,
                    route,
                    detail,
                    lineStartedAtNanos
            );
        }

        return new TooltipProcessingResult(translatedLines, translatableLines, hasPending, hasMissingKeyIssue, errorMessage);
    }

    private static int computeTooltipFingerprint(
            List<Component> tooltip,
            ItemTranslateConfig config,
            boolean useTagStylePreservation
    ) {
        int hash = config.enabled ? 1 : 0;
        hash = 31 * hash + Boolean.hashCode(useTagStylePreservation);
        hash = 31 * hash + Boolean.hashCode(config.enabled_translate_item_custom_name);
        hash = 31 * hash + Boolean.hashCode(config.enabled_translate_item_lore);
        hash = 31 * hash + (config.target_language == null ? 0 : config.target_language.hashCode());
        hash = 31 * hash + Long.hashCode(WynnSharedDictionaryService.getInstance().getItemSkillVersion());
        for (Component line : tooltip) {
            if (line != null) {
                hash = 31 * hash + line.hashCode();
                hash = 31 * hash + line.getString().hashCode();
                hash = 31 * hash + line.getStyle().hashCode();
            }
        }
        return hash;
    }

    private static void logFrameCacheStatsIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            String planCacheResult,
            String remoteKeysCacheResult
    ) {
        TooltipTextMatcherSupport.logTooltipCacheStatsIfDev(
                config,
                emitDevLog,
                source,
                "planLast=" + planCacheResult
                        + " planHits=" + TOOLTIP_PLAN_CACHE_HITS.get()
                        + " planMisses=" + TOOLTIP_PLAN_CACHE_MISSES.get()
                        + " remoteKeysLast=" + remoteKeysCacheResult
                        + " remoteKeysHits=" + REMOTE_KEYS_CACHE_HITS.get()
                        + " remoteKeysMisses=" + REMOTE_KEYS_CACHE_MISSES.get()
        );
    }

    private static Set<String> collectRemoteTranslationTemplateKeys(
            TooltipPlan tooltipPlan,
            boolean useTagStylePreservation,
            ItemTranslateConfig config
    ) {
        if (tooltipPlan == null || tooltipPlan.segments() == null || tooltipPlan.segments().isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> remoteKeys = new LinkedHashSet<>();
        for (TooltipRouteSegment segment : tooltipPlan.segments()) {
            if (segment == null || segment.kind() == null) {
                continue;
            }

            switch (segment.kind()) {
                case PASSTHROUGH -> {
                }
                case PARAGRAPH_BLOCK, STRUCTURED_LINE, LINE_TEMPLATE -> {
                }
            }
        }

        return remoteKeys.isEmpty() ? Set.of() : Collections.unmodifiableSet(remoteKeys);
    }
}
