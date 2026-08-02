package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

final class TooltipComponentTranslationSupport {
    private static final Pattern TEMPLATE_CONTROL_TOKEN = Pattern.compile("</?s\\d+>|\\{[dg]\\d+}");
    private static final String PARAGRAPH_LINE_FALLBACK_CONTEXT_PREFIX = "tooltip:paragraph:fallback:";
    private static final String PARAGRAPH_LINE_FALLBACK_POLICY = "paragraph-line-fallback-v1";

    private TooltipComponentTranslationSupport() {
    }

    static TooltipTranslationSupport.TooltipLineResult translatePreparedLine(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        return translatePreparedLineAttempt(prepared, route, context, policyVersion, config, true).lineResult();
    }

    static LineTranslationAttempt translatePreparedLineAttempt(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config,
            boolean queueIfMissing
    ) {
        if (prepared == null || config == null || !isEligibleLine(prepared.sourceLine(), config)) {
            return new LineTranslationAttempt(
                    null,
                    ComponentTranslationRuntime.FailureDisposition.INELIGIBLE,
                    "",
                    ComponentTranslationRuntime.State.INELIGIBLE
            );
        }
        PreparedLineDocument preparedDocument;
        try {
            preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        } catch (RuntimeException error) {
            return new LineTranslationAttempt(
                    failedLineResult(
                            prepared,
                            route,
                            context,
                            "Failed to prepare tooltip Component translation: " + describeError(error),
                            error
                    ),
                    ComponentTranslationRuntime.FailureDisposition.TERMINAL_CONTENT_FAILURE,
                    "",
                    ComponentTranslationRuntime.State.FAILED
            );
        }
        if (preparedDocument == null) {
            return new LineTranslationAttempt(
                    null,
                    ComponentTranslationRuntime.FailureDisposition.INELIGIBLE,
                    "",
                    ComponentTranslationRuntime.State.INELIGIBLE
            );
        }
        ComponentTranslationDocument document = preparedDocument.document();

        String targetLanguage = config.target_language;
        try {
            String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
            if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
                ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
            }

            ComponentTranslationApplier applier = new ComponentTranslationApplier();
            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    targetLanguage,
                    TooltipTemplateRuntime.buildLegacyCompatibilityKey(prepared.sourceLine()),
                    () -> TooltipTemplateRuntime.peekTranslatedPreparedTemplate(prepared),
                    response -> {
                        long startedAt = System.nanoTime();
                        Component translatedTemplate = applier.apply(document, response);
                        Component translated = TooltipTemplateRuntime.renderComponentTemplateTranslation(
                                preparedDocument.renderTemplate(),
                                translatedTemplate.getString()
                        );
                        if (translated == null) {
                            throw new IllegalArgumentException("Component tooltip template could not be rendered.");
                        }
                        ComponentTranslationMetrics.recordNanos(
                                route,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                        return translated;
                    },
                    context,
                    queueIfMissing
            );
            return new LineTranslationAttempt(
                    toLineResult(prepared.sourceLine(), cacheKey, resolution),
                    resolution.failureDisposition(),
                    resolution.cacheKey(),
                    resolution.state()
            );
        } catch (RuntimeException error) {
            return new LineTranslationAttempt(
                    failedLineResult(
                            prepared,
                            route,
                            context,
                            "Failed to resolve tooltip Component translation: " + describeError(error),
                            error
                    ),
                    ComponentTranslationRuntime.FailureDisposition.INFRASTRUCTURE_FAILURE,
                    "",
                    ComponentTranslationRuntime.State.FAILED
            );
        }
    }

    static int forceRefreshPreparedLine(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        PreparedLineDocument preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        if (preparedDocument == null) {
            return 0;
        }
        ComponentTranslationDocument document = preparedDocument.document();
        String cacheKey = ComponentTranslationRuntime.cacheKey(document, config.target_language);
        TooltipRefreshNoticeSupport.markComponentRefreshHandled(cacheKey);
        return ComponentTranslationRuntime.forceRefresh(document, config.target_language) ? 1 : 0;
    }

    static TooltipParagraphSupport.ParagraphTranslationAttempt translateParagraphBlock(
            TooltipParagraphBlock block,
            ItemTranslateConfig config
    ) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty() || config == null) {
            return null;
        }
        ComponentTranslationBundle bundle;
        TooltipParagraphSupport.ParagraphTranslationAttempt cachedFallback = readCompleteParagraphLineFallback(
                block,
                config
        );
        if (cachedFallback != null) {
            ComponentTranslationDebugLogger.flow(
                    ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                    "tooltip paragraph fallback state=CACHE_HIT lines={}",
                    block.preparedLines().size()
            );
            return cachedFallback;
        }
        try {
            bundle = prepareParagraphBundle(block, config);
        } catch (RuntimeException error) {
            return translateParagraphLinesIndividually(
                    block,
                    config,
                    "Failed to prepare tooltip paragraph Component translation: " + describeError(error),
                    error,
                    paragraphFallbackGenerationKey(block)
            );
        }
        if (bundle == null) {
            return null;
        }
        List<Component> lines = block.preparedLines().stream().map(PreparedTooltipTemplate::sourceLine).toList();

        String targetLanguage = config.target_language;
        String cacheKey;
        ComponentTranslationRuntime.Resolution<List<Component>> resolution;
        try {
            cacheKey = ComponentTranslationRuntime.cacheKey(bundle.cacheDocument(), targetLanguage);
            if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
                ComponentTranslationRuntime.forceRefresh(bundle.cacheDocument(), targetLanguage);
            }

            resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    targetLanguage,
                    block.paragraphTemplate().translationTemplateKey(),
                    () -> TooltipParagraphSupport.peekLegacyParagraphTranslation(block, config),
                    response -> {
                        long startedAt = System.nanoTime();
                        String translatedTemplate = bundle.coherentParagraphTranslation(response);
                        List<Component> translated = TooltipParagraphSupport.renderComponentParagraphTranslation(
                                block,
                                translatedTemplate,
                                config
                        );
                        if (translated == null || translated.isEmpty()) {
                            throw new IllegalArgumentException("Component tooltip paragraph translation was rejected.");
                        }
                        ComponentTranslationMetrics.recordNanos(
                                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                        return translated;
                    },
                    "tooltip:paragraph:lines=" + lines.size()
            );
        } catch (RuntimeException error) {
            return originalParagraphAttempt(
                    block,
                    "Failed to resolve tooltip paragraph Component translation: " + describeError(error)
            );
        }

        boolean validCacheHit = resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                && resolution.value() != null
                && !resolution.value().isEmpty();
        boolean validLegacyHit = resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT
                && resolution.value() != null
                && !resolution.value().isEmpty();
        if (validCacheHit || validLegacyHit) {
            List<TooltipTranslationSupport.TooltipLineResult> results = resolution.value().stream()
                    .map(line -> new TooltipTranslationSupport.TooltipLineResult(line, false, false))
                    .toList();
            return new TooltipParagraphSupport.ParagraphTranslationAttempt(results, false, false);
        }

        if (resolution.allowsTooltipFallback()) {
            return translateParagraphLinesIndividually(
                    block,
                    config,
                    resolution.errorMessage(),
                    null,
                    resolution.cacheKey()
            );
        }

        boolean pending = resolution.state() == ComponentTranslationRuntime.State.PENDING;
        boolean failed = resolution.state() == ComponentTranslationRuntime.State.FAILED
                || resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE;
        List<TooltipTranslationSupport.TooltipLineResult> fallback = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            Component rendered = pending
                    ? AnimationManager.getAnimatedStyledText(line, cacheKey + "#" + index, false)
                    : line;
            fallback.add(new TooltipTranslationSupport.TooltipLineResult(
                    rendered,
                    pending,
                    false,
                    failed && index == 0 ? resolution.errorMessage() : ""
            ));
        }
        return new TooltipParagraphSupport.ParagraphTranslationAttempt(fallback, pending, false);
    }

    static int forceRefreshParagraphBlock(TooltipParagraphBlock block, ItemTranslateConfig config) {
        if (block == null || block.preparedLines() == null || config == null) {
            return 0;
        }
        ComponentTranslationBundle bundle = prepareParagraphBundle(block, config);
        if (bundle == null) {
            return 0;
        }
        String cacheKey = ComponentTranslationRuntime.cacheKey(bundle.cacheDocument(), config.target_language);
        TooltipRefreshNoticeSupport.markComponentRefreshHandled(cacheKey);
        return ComponentTranslationRuntime.forceRefresh(bundle.cacheDocument(), config.target_language) ? 1 : 0;
    }

    private static PreparedLineDocument prepareLineDocument(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        if (prepared == null || config == null || !isEligibleLine(prepared.sourceLine(), config)) {
            return null;
        }
        PreparedTooltipTemplate renderTemplate = TooltipTemplateRuntime.prepareComponentTemplate(prepared);
        if (renderTemplate == null || !hasTranslatableText(renderTemplate.normalizedTemplate())) {
            return null;
        }
        ComponentTranslationDocument document = ComponentTranslationRuntime.prepare(
                Component.literal(renderTemplate.normalizedTemplate()),
                route,
                context,
                policyVersion
        );
        return document.units().isEmpty() ? null : new PreparedLineDocument(document, renderTemplate);
    }

    private static ComponentTranslationBundle prepareParagraphBundle(
            TooltipParagraphBlock block,
            ItemTranslateConfig config
    ) {
        if (block == null
                || block.preparedLines() == null
                || block.preparedLines().isEmpty()
                || block.paragraphTemplate() == null
                || config == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>(block.preparedLines().size());
        for (PreparedTooltipTemplate prepared : block.preparedLines()) {
            if (prepared == null || !isEligibleLine(prepared.sourceLine(), config)) {
                return null;
            }
            lines.add(prepared.sourceLine());
        }
        ComponentTranslationBundle bundle = ComponentTranslationBundle.createCoherentParagraph(
                lines,
                "tooltip:paragraph:coherent",
                "paragraph-v4",
                block.paragraphTemplate().componentTranslationTemplateKey()
        );
        return bundle.cacheDocument().units().isEmpty() ? null : bundle;
    }

    private static TooltipTranslationSupport.TooltipLineResult toLineResult(
            Component original,
            String cacheKey,
            ComponentTranslationRuntime.Resolution<Component> resolution
    ) {
        if ((resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                || resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT)
                && resolution.value() != null) {
            return new TooltipTranslationSupport.TooltipLineResult(resolution.value(), false, false);
        }
        if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    AnimationManager.getAnimatedStyledText(original, cacheKey, false),
                    true,
                    false
            );
        }
        if (resolution.state() == ComponentTranslationRuntime.State.FAILED
                || resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    original,
                    false,
                    false,
                    resolution.errorMessage()
            );
        }
        return new TooltipTranslationSupport.TooltipLineResult(original, false, false);
    }

    private static TooltipTranslationSupport.TooltipLineResult failedLineResult(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String reason,
            Throwable error
    ) {
        Component original = prepared == null || prepared.sourceLine() == null
                ? Component.empty()
                : prepared.sourceLine();
        ComponentTranslationDebugLogger.error(
                route,
                "tooltip preparation failed: route={} context={} source=\"{}\" reason={}",
                route == null ? "" : route.wireName(),
                context == null ? "" : context,
                TooltipTemplateRuntime.truncateForLog(original.getString(), 220),
                reason,
                error
        );
        return new TooltipTranslationSupport.TooltipLineResult(original, false, false, reason);
    }

    private static TooltipParagraphSupport.ParagraphTranslationAttempt translateParagraphLinesIndividually(
            TooltipParagraphBlock block,
            ItemTranslateConfig config,
            String reason,
            Throwable error,
            String fallbackGenerationKey
    ) {
        boolean queueIfMissing = ComponentTranslationRuntime.claimFallbackGeneration(fallbackGenerationKey);
        if (queueIfMissing) {
            String source = TooltipTemplateRuntime.truncateForLog(
                    TooltipParagraphSupport.buildParagraphLocalDictionaryLookupSource(block),
                    220
            );
            if (error == null) {
                ComponentTranslationDebugLogger.error(
                        ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                        "tooltip paragraph failed; using line fallback: source=\"{}\" reason={}",
                        source,
                        reason
                );
            } else {
                ComponentTranslationDebugLogger.error(
                        ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                        "tooltip paragraph failed; using line fallback: source=\"{}\" reason={}",
                        source,
                        reason,
                        error
                );
            }
        }

        List<TooltipTranslationSupport.TooltipLineResult> lineResults = new ArrayList<>(block.preparedLines().size());
        boolean pending = false;
        boolean missingKeyIssue = false;
        for (int index = 0; index < block.preparedLines().size(); index++) {
            PreparedTooltipTemplate preparedLine = block.preparedLines().get(index);
            LineTranslationAttempt lineAttempt = translatePreparedLineAttempt(
                    preparedLine,
                    ComponentTranslationRoute.TOOLTIP_LINE,
                    paragraphLineFallbackContext(index),
                    PARAGRAPH_LINE_FALLBACK_POLICY,
                    config,
                    queueIfMissing
            );
            TooltipTranslationSupport.TooltipLineResult lineResult = lineAttempt.lineResult();
            if (lineResult == null) {
                Component original = preparedLine == null || preparedLine.sourceLine() == null
                        ? Component.empty()
                        : preparedLine.sourceLine();
                lineResult = new TooltipTranslationSupport.TooltipLineResult(original, false, false);
            }
            if (lineResult.pending()) {
                pending = true;
            }
            if (lineResult.missingKeyIssue()) {
                missingKeyIssue = true;
            }
            lineResults.add(lineResult);
        }
        return new TooltipParagraphSupport.ParagraphTranslationAttempt(lineResults, pending, missingKeyIssue);
    }

    private static TooltipParagraphSupport.ParagraphTranslationAttempt readCompleteParagraphLineFallback(
            TooltipParagraphBlock block,
            ItemTranslateConfig config
    ) {
        List<TooltipTranslationSupport.TooltipLineResult> lineResults = new ArrayList<>(block.preparedLines().size());
        for (int index = 0; index < block.preparedLines().size(); index++) {
            LineTranslationAttempt attempt = translatePreparedLineAttempt(
                    block.preparedLines().get(index),
                    ComponentTranslationRoute.TOOLTIP_LINE,
                    paragraphLineFallbackContext(index),
                    PARAGRAPH_LINE_FALLBACK_POLICY,
                    config,
                    false
            );
            if (!attempt.isReusableCacheHit()) {
                return null;
            }
            lineResults.add(attempt.lineResult());
        }
        return new TooltipParagraphSupport.ParagraphTranslationAttempt(lineResults, false, false);
    }

    private static TooltipParagraphSupport.ParagraphTranslationAttempt originalParagraphAttempt(
            TooltipParagraphBlock block,
            String reason
    ) {
        if (block == null || block.preparedLines() == null) {
            return new TooltipParagraphSupport.ParagraphTranslationAttempt(List.of(), false, false);
        }
        List<TooltipTranslationSupport.TooltipLineResult> results = new ArrayList<>(block.preparedLines().size());
        for (int index = 0; index < block.preparedLines().size(); index++) {
            PreparedTooltipTemplate prepared = block.preparedLines().get(index);
            Component original = prepared == null || prepared.sourceLine() == null
                    ? Component.empty()
                    : prepared.sourceLine();
            results.add(new TooltipTranslationSupport.TooltipLineResult(
                    original,
                    false,
                    false,
                    index == 0 ? reason : ""
            ));
        }
        return new TooltipParagraphSupport.ParagraphTranslationAttempt(results, false, false);
    }

    private static String paragraphFallbackGenerationKey(TooltipParagraphBlock block) {
        if (block == null || block.paragraphTemplate() == null) {
            return "tooltip:paragraph:fallback";
        }
        String key = block.paragraphTemplate().componentTranslationTemplateKey();
        if (key == null || key.isBlank()) {
            key = block.paragraphTemplate().translationTemplateKey();
        }
        return "tooltip:paragraph:fallback:" + (key == null ? "" : key);
    }

    private static String paragraphLineFallbackContext(int index) {
        return PARAGRAPH_LINE_FALLBACK_CONTEXT_PREFIX + index;
    }

    private static String describeError(RuntimeException error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "unknown error" : error.getClass().getSimpleName();
        }
        return error.getMessage();
    }

    static boolean isEligibleLine(Component line, ItemTranslateConfig config) {
        return line != null && !line.getString().isBlank();
    }

    static boolean hasTranslatableText(String normalizedTemplate) {
        if (normalizedTemplate == null || normalizedTemplate.isBlank()) {
            return false;
        }
        String visibleText = TEMPLATE_CONTROL_TOKEN.matcher(normalizedTemplate).replaceAll("");
        return visibleText.codePoints().anyMatch(Character::isLetter);
    }

    private record PreparedLineDocument(
            ComponentTranslationDocument document,
            PreparedTooltipTemplate renderTemplate
    ) {
    }

    record LineTranslationAttempt(
            TooltipTranslationSupport.TooltipLineResult lineResult,
            ComponentTranslationRuntime.FailureDisposition failureDisposition,
            String cacheKey,
            ComponentTranslationRuntime.State state
    ) {
        LineTranslationAttempt {
            failureDisposition = failureDisposition == null
                    ? ComponentTranslationRuntime.FailureDisposition.INELIGIBLE
                    : failureDisposition;
            cacheKey = cacheKey == null ? "" : cacheKey;
            state = state == null ? ComponentTranslationRuntime.State.INELIGIBLE : state;
        }

        boolean allowsFallback() {
            return failureDisposition == ComponentTranslationRuntime.FailureDisposition.TERMINAL_CONTENT_FAILURE;
        }

        boolean isReusableCacheHit() {
            return (state == ComponentTranslationRuntime.State.CACHE_HIT
                    || state == ComponentTranslationRuntime.State.LEGACY_HIT)
                    && lineResult != null
                    && !lineResult.pending()
                    && lineResult.errorMessage().isBlank();
        }
    }
}
