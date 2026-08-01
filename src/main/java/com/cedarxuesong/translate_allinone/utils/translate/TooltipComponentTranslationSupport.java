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
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TooltipComponentTranslationSupport {
    private static final Pattern TEMPLATE_CONTROL_TOKEN = Pattern.compile("</?s\\d+>|\\{[dg]\\d+}");

    private TooltipComponentTranslationSupport() {
    }

    static TooltipTranslationSupport.TooltipLineResult translatePreparedLine(
            PreparedTooltipTemplate prepared,
            ComponentTranslationRoute route,
            String context,
            String policyVersion,
            ItemTranslateConfig config
    ) {
        if (prepared == null || config == null || !isEligibleLine(prepared.sourceLine(), config)) {
            return null;
        }
        PreparedLineDocument preparedDocument;
        try {
            preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        } catch (RuntimeException error) {
            return failedLineResult(
                    prepared,
                    route,
                    context,
                    "Failed to prepare tooltip Component translation: " + describeError(error),
                    error
            );
        }
        if (preparedDocument == null) {
            return null;
        }

        ComponentTranslationDocument document = preparedDocument.document();
        String targetLanguage = config.target_language;
        try {
            String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
            if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
                ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
            }
            ComponentTranslationApplier applier = new ComponentTranslationApplier();
            ComponentTranslationRuntime.Resolution<Text> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    targetLanguage,
                    TooltipTemplateRuntime.buildLegacyCompatibilityKey(prepared.sourceLine()),
                    () -> TooltipTemplateRuntime.peekTranslatedPreparedTemplate(prepared),
                    response -> {
                        long startedAt = System.nanoTime();
                        try {
                            Text translatedTemplate = applier.apply(document, response);
                            Text translated = TooltipTemplateRuntime.renderComponentTemplateTranslation(
                                    preparedDocument.renderTemplate(),
                                    translatedTemplate.getString()
                            );
                            if (translated == null) {
                                throw new IllegalArgumentException("Component tooltip template could not be rendered.");
                            }
                            return translated;
                        } finally {
                            ComponentTranslationMetrics.recordNanos(
                                    route,
                                    ComponentTranslationMetrics.Timing.APPLY,
                                    System.nanoTime() - startedAt
                            );
                        }
                    },
                    context
            );
            return toLineResult(prepared.sourceLine(), cacheKey, resolution);
        } catch (RuntimeException error) {
            return failedLineResult(
                    prepared,
                    route,
                    context,
                    "Failed to resolve tooltip Component translation: " + describeError(error),
                    error
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
        try {
            bundle = prepareParagraphBundle(block, config);
        } catch (RuntimeException error) {
            return translateParagraphLinesIndividually(
                    block,
                    config,
                    "Failed to prepare tooltip paragraph Component translation: " + describeError(error),
                    error
            );
        }
        if (bundle == null) {
            return null;
        }
        List<Text> lines = block.preparedLines().stream().map(PreparedTooltipTemplate::sourceLine).toList();

        String targetLanguage = config.target_language;
        String cacheKey;
        ComponentTranslationRuntime.Resolution<List<Text>> resolution;
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
                        try {
                            String translatedTemplate = bundle.coherentParagraphTranslation(response);
                            return TooltipParagraphSupport.renderComponentParagraphTranslation(
                                    block,
                                    translatedTemplate,
                                    config
                            );
                        } finally {
                            ComponentTranslationMetrics.recordNanos(
                                    ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                                    ComponentTranslationMetrics.Timing.APPLY,
                                    System.nanoTime() - startedAt
                            );
                        }
                    },
                    "tooltip:paragraph:lines=" + lines.size()
            );
        } catch (RuntimeException error) {
            return translateParagraphLinesIndividually(
                    block,
                    config,
                    "Failed to resolve tooltip paragraph Component translation: " + describeError(error),
                    error
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

        boolean pending = resolution.state() == ComponentTranslationRuntime.State.PENDING;
        boolean failed = resolution.state() == ComponentTranslationRuntime.State.FAILED
                || resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE;
        List<TooltipTranslationSupport.TooltipLineResult> fallback = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Text line = lines.get(index);
            Text rendered = pending
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
                Text.literal(renderTemplate.normalizedTemplate()),
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
        List<Text> lines = new ArrayList<>(block.preparedLines().size());
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
            Text original,
            String cacheKey,
            ComponentTranslationRuntime.Resolution<Text> resolution
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
        Text original = prepared == null || prepared.sourceLine() == null
                ? Text.empty()
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
            Throwable error
    ) {
        ComponentTranslationDebugLogger.error(
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                "tooltip paragraph preparation failed; using line fallback: source=\"{}\" reason={}",
                TooltipTemplateRuntime.truncateForLog(
                        TooltipParagraphSupport.buildParagraphLocalDictionaryLookupSource(block),
                        220
                ),
                reason,
                error
        );

        List<TooltipTranslationSupport.TooltipLineResult> lineResults = new ArrayList<>(block.preparedLines().size());
        boolean pending = false;
        boolean missingKeyIssue = false;
        for (int index = 0; index < block.preparedLines().size(); index++) {
            PreparedTooltipTemplate preparedLine = block.preparedLines().get(index);
            TooltipTranslationSupport.TooltipLineResult lineResult = translatePreparedLine(
                    preparedLine,
                    ComponentTranslationRoute.TOOLTIP_LINE,
                    "tooltip:paragraph:fallback:" + index,
                    "paragraph-line-fallback-v1",
                    config
            );
            if (lineResult == null) {
                Text original = preparedLine == null || preparedLine.sourceLine() == null
                        ? Text.empty()
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

    private static String describeError(RuntimeException error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "unknown error" : error.getClass().getSimpleName();
        }
        return error.getMessage();
    }

    static boolean isEligibleLine(Text line, ItemTranslateConfig config) {
        if (line == null || line.getString().isBlank()) {
            return false;
        }
        return true;
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
}
