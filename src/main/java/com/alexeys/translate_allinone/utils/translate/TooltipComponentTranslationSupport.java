package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationApplier;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationBundle;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDocument;
import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationResponse;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import com.alexeys.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.alexeys.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class TooltipComponentTranslationSupport {
    private static final Pattern TEMPLATE_CONTROL_TOKEN = Pattern.compile("</?s\\d+>|\\{[dg]\\d+}");
    private static final String RETAINED_PARAGRAPH_LINE_CONTEXT_PREFIX = "tooltip:paragraph:fallback:";
    private static final String RETAINED_PARAGRAPH_LINE_POLICY = "paragraph-line-fallback-v1";

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
        if (!TranslationFeatureGate.isEnabled()
                || prepared == null
                || config == null
                || !isEligibleLine(prepared.sourceLine(), config)) {
            logTooltipText(route, context, "INELIGIBLE", prepared);
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
            logTooltipText(route, context, "INELIGIBLE", prepared);
            return new LineTranslationAttempt(
                    null,
                    ComponentTranslationRuntime.FailureDisposition.INELIGIBLE,
                    "",
                    ComponentTranslationRuntime.State.INELIGIBLE
            );
        }
        ComponentTranslationDocument document = preparedDocument.document();
        logTooltipText(route, context, "COMPONENT_TEXT", prepared);

        String targetLanguage = config.target_language;
        try {
            String cacheKey = ComponentTranslationRuntime.cacheKey(document, targetLanguage);
            if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
                ComponentTranslationRuntime.forceRefresh(document, targetLanguage);
            }
            boolean suppressQueue = TooltipRefreshNoticeSupport.shouldSuppressComponentQueue(cacheKey);

            ComponentTranslationApplier applier = new ComponentTranslationApplier();
            ComponentTranslationRuntime.Resolution<Component> resolution = ComponentTranslationRuntime.resolve(
                    document,
                    targetLanguage,
                    TooltipTemplateRuntime.buildLegacyCompatibilityKey(prepared.sourceLine()),
                    () -> suppressQueue
                            ? null
                            : TooltipTemplateRuntime.peekTranslatedPreparedTemplate(prepared),
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
                    queueIfMissing && !suppressQueue
            );
            if (resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE) {
                logTooltipText(route, context, "INELIGIBLE", prepared);
            }
            return new LineTranslationAttempt(
                    toLineResult(prepared, cacheKey, resolution),
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
        if (!TranslationFeatureGate.isEnabled()) {
            return 0;
        }
        PreparedLineDocument preparedDocument;
        try {
            preparedDocument = prepareLineDocument(prepared, route, context, policyVersion, config);
        } catch (RuntimeException error) {
            ComponentTranslationDebugLogger.error(
                    route,
                    "force refresh document preparation failed: route={} context={} reason={}",
                    route == null ? "" : route.wireName(),
                    context == null ? "" : context,
                    describeError(error),
                    error
            );
            return 0;
        }
        if (preparedDocument == null) {
            return 0;
        }
        ComponentTranslationDocument document = preparedDocument.document();
        return forceRefreshDocuments(List.of(document), config);
    }

    static TooltipParagraphSupport.ParagraphTranslationAttempt translateParagraphBlock(
            TooltipParagraphBlock block,
            ItemTranslateConfig config
    ) {
        if (!TranslationFeatureGate.isEnabled()
                || block == null
                || block.preparedLines() == null
                || block.preparedLines().isEmpty()
                || config == null) {
            return null;
        }
        ComponentTranslationBundle bundle;
        try {
            bundle = prepareParagraphBundle(block, config);
        } catch (RuntimeException error) {
            return originalParagraphAttempt(
                    block,
                    "Failed to prepare tooltip paragraph Component translation: " + describeError(error)
            );
        }
        if (bundle == null) {
            logParagraphText(block, "INELIGIBLE");
            return null;
        }
        List<Component> lines = block.preparedLines().stream().map(PreparedTooltipTemplate::sourceLine).toList();
        ComponentTranslationDebugLogger.tooltipText(
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                "tooltip:paragraph:coherent",
                "COMPONENT_TEXT",
                lines
        );

        String targetLanguage = config.target_language;
        String cacheKey;
        ComponentTranslationRuntime.Resolution<List<Component>> resolution;
        try {
            cacheKey = ComponentTranslationRuntime.cacheKey(bundle.cacheDocument(), targetLanguage);
            if (TooltipRefreshNoticeSupport.consumeComponentRefresh(cacheKey, config)) {
                ComponentTranslationRuntime.forceRefresh(bundle.cacheDocument(), targetLanguage);
            }
            boolean suppressQueue = TooltipRefreshNoticeSupport.shouldSuppressComponentQueue(cacheKey);

            resolution = ComponentTranslationRuntime.resolve(
                    bundle.cacheDocument(),
                    targetLanguage,
                    block.paragraphTemplate().translationTemplateKey(),
                    () -> {
                        if (suppressQueue) {
                            return null;
                        }
                        List<Component> promoted = promoteCompatibleParagraphResponse(bundle, block, config);
                        return promoted != null
                                ? promoted
                                : TooltipParagraphSupport.peekLegacyParagraphTranslation(block, config);
                    },
                    response -> {
                        long startedAt = System.nanoTime();
                        TooltipParagraphSupport.ParagraphRenderResult renderResult = renderParagraphCandidate(
                                bundle,
                                block,
                                response,
                                config
                        );
                        if (!renderResult.accepted()) {
                            throw new IllegalArgumentException(renderResult.rejectionMessage());
                        }
                        ComponentTranslationMetrics.recordNanos(
                                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                                ComponentTranslationMetrics.Timing.APPLY,
                                System.nanoTime() - startedAt
                        );
                        return renderResult.lines();
                    },
                    "tooltip:paragraph:lines=" + lines.size(),
                    !suppressQueue
            );
        } catch (RuntimeException error) {
            return originalParagraphAttempt(
                    block,
                    "Failed to resolve tooltip paragraph Component translation: " + describeError(error)
            );
        }

        if (resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE) {
            logParagraphText(block, "INELIGIBLE");
        }

        boolean validCacheHit = resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                && resolution.value() != null
                && !resolution.value().isEmpty();
        boolean validLegacyHit = resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT
                && resolution.value() != null
                && !resolution.value().isEmpty();
        if (validCacheHit || validLegacyHit) {
            if (validCacheHit) {
                for (String templateKey : collectParagraphForceRefreshTemplateKeys(block)) {
                    TooltipTemplateRuntime.clearForceRefreshCompatBypass(templateKey);
                }
            }
            List<TooltipTranslationSupport.TooltipLineResult> results = resolution.value().stream()
                    .map(line -> new TooltipTranslationSupport.TooltipLineResult(line, false, false))
                    .toList();
            return new TooltipParagraphSupport.ParagraphTranslationAttempt(results, false, false);
        }

        if (resolution.allowsTooltipFallback()) {
            ComponentTranslationDebugLogger.flow(
                    ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                    "tooltip paragraph fallback state=ORIGINAL key={} reason={} lines={}",
                    resolution.cacheKey(),
                    resolution.errorMessage(),
                    lines.size()
            );
            return originalParagraphAttempt(
                    block,
                    resolution.errorMessage()
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

    private static TooltipParagraphSupport.ParagraphRenderResult renderParagraphCandidate(
            ComponentTranslationBundle bundle,
            TooltipParagraphBlock block,
            ComponentTranslationResponse response,
            ItemTranslateConfig config
    ) {
        TooltipParagraphSupport.ParagraphRenderResult fullResult;
        try {
            fullResult = TooltipParagraphSupport.renderComponentParagraphTranslationResult(
                    block,
                    bundle.coherentParagraphTranslation(response),
                    config
            );
        } catch (ComponentJsonException error) {
            if (error.kind() != ComponentJsonException.Kind.APPLY
                    && error.kind() != ComponentJsonException.Kind.VALIDATION) {
                throw error;
            }
            fullResult = TooltipParagraphSupport.ParagraphRenderResult.rejected(
                    TooltipParagraphSupport.ParagraphRenderStage.STYLE_SEMANTICS,
                    TooltipParagraphSupport.ParagraphRejectReason.STYLE_SEMANTICS_REJECTED,
                    error.getMessage()
            );
        }
        if (fullResult.accepted()
                || fullResult.stage() != TooltipParagraphSupport.ParagraphRenderStage.STYLE_SEMANTICS) {
            return fullResult;
        }
        ComponentTranslationDebugLogger.flow(
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                "tooltip paragraph fallback state=COHERENT_SAFE_BODY reason={}",
                fullResult.rejectionMessage()
        );
        String safeBodyTemplate = bundle.coherentSafeBodyParagraphTranslation(
                response,
                block.paragraphTemplate().bodyStyleId()
        );
        return TooltipParagraphSupport.renderSafeBodyComponentParagraphTranslationResult(
                block,
                safeBodyTemplate,
                config
        );
    }

    static int forceRefreshParagraphBlock(TooltipParagraphBlock block, ItemTranslateConfig config) {
        if (!TranslationFeatureGate.isEnabled() || block == null || block.preparedLines() == null || config == null) {
            return 0;
        }
        List<ComponentTranslationDocument> documents = new ArrayList<>();
        try {
            ComponentTranslationBundle bundle = prepareParagraphBundle(block, config);
            if (bundle != null) {
                documents.add(bundle.cacheDocument());
            }
            ComponentTranslationBundle semanticCompatibilityBundle = prepareSemanticSlotCompatibilityBundle(block);
            if (semanticCompatibilityBundle != null) {
                documents.add(semanticCompatibilityBundle.cacheDocument());
            }
        } catch (RuntimeException error) {
            ComponentTranslationDebugLogger.error(
                    ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                    "force refresh paragraph document preparation failed: reason={}",
                    describeError(error),
                    error
            );
        }
        for (int index = 0; index < block.preparedLines().size(); index++) {
            try {
                PreparedLineDocument preparedDocument = prepareLineDocument(
                        block.preparedLines().get(index),
                        ComponentTranslationRoute.TOOLTIP_LINE,
                        retainedParagraphLineContext(index),
                        RETAINED_PARAGRAPH_LINE_POLICY,
                        config
                );
                if (preparedDocument != null) {
                    documents.add(preparedDocument.document());
                }
            } catch (RuntimeException error) {
                ComponentTranslationDebugLogger.error(
                        ComponentTranslationRoute.TOOLTIP_LINE,
                        "force refresh retained paragraph line document preparation failed: lineIndex={} reason={}",
                        index,
                        describeError(error),
                        error
                );
            }
        }
        return forceRefreshDocuments(documents, config);
    }

    static Set<String> collectParagraphForceRefreshTemplateKeys(TooltipParagraphBlock block) {
        if (!TranslationFeatureGate.isEnabled()) {
            return Set.of();
        }
        LinkedHashSet<String> templateKeys = new LinkedHashSet<>();
        if (block == null) {
            return templateKeys;
        }
        if (block.paragraphTemplate() != null) {
            addTemplateKey(templateKeys, block.paragraphTemplate().translationTemplateKey());
        }
        if (block.preparedLines() != null) {
            for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
                if (preparedLine != null) {
                    addTemplateKey(templateKeys, preparedLine.translationTemplateKey());
                }
            }
        }
        return templateKeys;
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
        ParagraphTranslationPlan translationPlan = block.paragraphTemplate().translationPlan();
        if (translationPlan == null || translationPlan.requestText().isBlank()) {
            return null;
        }
        ComponentTranslationBundle bundle = ComponentTranslationBundle.createInlineAnchoredParagraph(
                "tooltip:paragraph:coherent",
                "paragraph-v6",
                translationPlan.requestText(),
                translationPlan.hardTokenIds(),
                translationPlan.accentIds()
        );
        return bundle.cacheDocument().units().isEmpty() ? null : bundle;
    }

    private static List<Component> promoteCompatibleParagraphResponse(
            ComponentTranslationBundle currentBundle,
            TooltipParagraphBlock block,
            ItemTranslateConfig config
    ) {
        if (currentBundle == null
                || block == null
                || block.paragraphTemplate() == null
                || block.paragraphTemplate().translationPlan() == null
                || block.paragraphTemplate().semanticSlots().isEmpty()
                || TooltipTemplateRuntime.shouldBypassCompatibilityFallback(
                block.paragraphTemplate().translationTemplateKey()
        )) {
            return null;
        }
        ComponentTranslationBundle legacyBundle = prepareSemanticSlotCompatibilityBundle(block);
        if (legacyBundle == null) {
            return null;
        }
        return ComponentTranslationRuntime.promoteCompatibleResponse(
                currentBundle.cacheDocument(),
                legacyBundle.cacheDocument(),
                config.target_language,
                response -> new ComponentTranslationResponse(
                        currentBundle.cacheDocument().protocol(),
                        Map.of(
                                "paragraph",
                                promoteSemanticSlotParagraph(
                                        block,
                                        legacyBundle.coherentParagraphTranslation(response)
                                )
                        )
                ),
                response -> {
                    TooltipParagraphSupport.ParagraphRenderResult renderResult = renderParagraphCandidate(
                            currentBundle,
                            block,
                            response,
                            config
                    );
                    if (!renderResult.accepted()) {
                        throw new IllegalArgumentException(renderResult.rejectionMessage());
                    }
                    return renderResult.lines();
                },
                "tooltip:paragraph:legacy-promotion"
        );
    }

    private static ComponentTranslationBundle prepareSemanticSlotCompatibilityBundle(TooltipParagraphBlock block) {
        if (block == null
                || block.preparedLines() == null
                || block.preparedLines().isEmpty()
                || block.paragraphTemplate() == null
                || block.paragraphTemplate().semanticSlots().isEmpty()
                || block.paragraphTemplate().semanticSlotCompatibilityTemplateKey() == null
                || block.paragraphTemplate().semanticSlotCompatibilityTemplateKey().isBlank()) {
            return null;
        }
        List<Component> lines = block.preparedLines().stream()
                .map(PreparedTooltipTemplate::sourceLine)
                .toList();
        return ComponentTranslationBundle.createCoherentParagraph(
                lines,
                "tooltip:paragraph:coherent",
                "paragraph-v5",
                block.paragraphTemplate().semanticSlotCompatibilityTemplateKey(),
                block.paragraphTemplate().semanticSlots().stream()
                        .map(slot -> new ComponentTranslationBundle.SemanticSlot(
                                slot.id(),
                                slot.styleId(),
                                slot.sourceText()
                        ))
                        .toList()
        );
    }

    private static String promoteSemanticSlotParagraph(TooltipParagraphBlock block, String legacyParagraph) {
        if (block == null
                || block.paragraphTemplate() == null
                || block.paragraphTemplate().translationPlan() == null
                || legacyParagraph == null
                || legacyParagraph.isBlank()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.APPLY,
                    "Semantic-slot paragraph compatibility input is incomplete."
            );
        }

        ParagraphTranslationPlan plan = block.paragraphTemplate().translationPlan();
        Map<Style, List<ParagraphTranslationPlan.AccentAnchor>> anchorsByStyle = new LinkedHashMap<>();
        for (ParagraphTranslationPlan.AccentAnchor anchor : plan.accentAnchors()) {
            Style visualStyle = StylePreserver.sanitizeStyleForComparison(anchor.style(), true);
            anchorsByStyle.computeIfAbsent(visualStyle, ignored -> new ArrayList<>()).add(anchor);
        }

        Pattern spanPattern = Pattern.compile("<s(\\d+)>(.*?)</s\\1>");
        Matcher matcher = spanPattern.matcher(legacyParagraph);
        StringBuilder promoted = new StringBuilder(legacyParagraph.length());
        Set<String> usedAnchors = new LinkedHashSet<>();
        while (matcher.find()) {
            int styleId = Integer.parseInt(matcher.group(1));
            Style style = block.paragraphTemplate().styleMap().getOrDefault(styleId, Style.EMPTY);
            List<ParagraphTranslationPlan.AccentAnchor> matchingAnchors = anchorsByStyle.getOrDefault(
                    StylePreserver.sanitizeStyleForComparison(style, true),
                    List.of()
            );
            String content = matcher.group(2);
            String replacement = content;
            if (matchingAnchors.size() == 1
                    && containsNaturalLanguageText(content)
                    && usedAnchors.add(matchingAnchors.getFirst().id())) {
                ParagraphTranslationPlan.AccentAnchor anchor = matchingAnchors.getFirst();
                replacement = anchor.beginToken() + content + anchor.endToken();
            }
            matcher.appendReplacement(promoted, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(promoted);
        String withoutLegacyTags = TooltipTemplateRuntime.STYLE_TAG_ID_PATTERN.matcher(promoted).replaceAll("");
        String withDynamicTokens = replaceLegacyParagraphTokenIds(withoutLegacyTags, Pattern.compile("\\{d(\\d+)}"), "value");
        return replaceLegacyParagraphTokenIds(withDynamicTokens, Pattern.compile("\\{g(\\d+)}"), "glyph");
    }

    private static boolean containsNaturalLanguageText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static String replaceLegacyParagraphTokenIds(String text, Pattern pattern, String prefix) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder replaced = new StringBuilder(text.length());
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            matcher.appendReplacement(replaced, Matcher.quoteReplacement("{" + prefix + index + "}"));
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    private static int forceRefreshDocuments(
            List<ComponentTranslationDocument> documents,
            ItemTranslateConfig config
    ) {
        if (documents == null || documents.isEmpty() || config == null) {
            return 0;
        }
        List<ComponentTranslationDocument> refreshDocuments = new ArrayList<>(documents.size());
        for (ComponentTranslationDocument document : documents) {
            if (document == null) {
                continue;
            }
            try {
                String cacheKey = ComponentTranslationRuntime.cacheKey(document, config.target_language);
                TooltipRefreshNoticeSupport.markComponentRefreshHandled(cacheKey);
                refreshDocuments.add(document);
            } catch (RuntimeException error) {
                ComponentTranslationDebugLogger.error(
                        document.route(),
                        "force refresh cache key preparation failed: route={} reason={}",
                        document.route().wireName(),
                        describeError(error),
                        error
                );
            }
        }
        int refreshed = 0;
        for (ComponentTranslationDocument document : refreshDocuments) {
            try {
                if (ComponentTranslationRuntime.forceRefresh(document, config.target_language)) {
                    refreshed++;
                }
            } catch (RuntimeException error) {
                ComponentTranslationDebugLogger.error(
                        document.route(),
                        "force refresh cache removal failed: route={} reason={}",
                        document.route().wireName(),
                        describeError(error),
                        error
                );
            }
        }
        return refreshed;
    }

    private static void logTooltipText(
            ComponentTranslationRoute route,
            String context,
            String marker,
            PreparedTooltipTemplate prepared
    ) {
        ComponentTranslationDebugLogger.tooltipText(
                route,
                context,
                marker,
                prepared == null ? null : prepared.sourceLine()
        );
    }

    private static void logParagraphText(TooltipParagraphBlock block, String marker) {
        if (block == null || block.preparedLines() == null) {
            return;
        }
        List<Component> labels = new ArrayList<>(block.preparedLines().size());
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            labels.add(preparedLine == null ? null : preparedLine.sourceLine());
        }
        ComponentTranslationDebugLogger.tooltipText(
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH,
                "tooltip:paragraph:coherent",
                marker,
                labels
        );
    }

    private static void addTemplateKey(Set<String> templateKeys, String templateKey) {
        if (templateKey != null && !templateKey.isBlank()) {
            templateKeys.add(templateKey);
        }
    }

    private static TooltipTranslationSupport.TooltipLineResult toLineResult(
            PreparedTooltipTemplate prepared,
            String cacheKey,
            ComponentTranslationRuntime.Resolution<Component> resolution
    ) {
        if ((resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT
                || resolution.state() == ComponentTranslationRuntime.State.LEGACY_HIT)
                && resolution.value() != null) {
            if (resolution.state() == ComponentTranslationRuntime.State.CACHE_HIT && prepared != null) {
                TooltipTemplateRuntime.clearForceRefreshCompatBypass(prepared.translationTemplateKey());
            }
            return new TooltipTranslationSupport.TooltipLineResult(resolution.value(), false, false);
        }
        if (resolution.state() == ComponentTranslationRuntime.State.PENDING) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    AnimationManager.getAnimatedStyledText(prepared.sourceLine(), cacheKey, false),
                    true,
                    false
            );
        }
        if (resolution.state() == ComponentTranslationRuntime.State.FAILED
                || resolution.state() == ComponentTranslationRuntime.State.INELIGIBLE) {
            return new TooltipTranslationSupport.TooltipLineResult(
                    prepared.sourceLine(),
                    false,
                    false,
                    resolution.errorMessage()
            );
        }
        return new TooltipTranslationSupport.TooltipLineResult(prepared.sourceLine(), false, false);
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

    private static String retainedParagraphLineContext(int index) {
        return RETAINED_PARAGRAPH_LINE_CONTEXT_PREFIX + index;
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
            return state == ComponentTranslationRuntime.State.CACHE_HIT
                    && lineResult != null
                    && !lineResult.pending()
                    && lineResult.errorMessage().isBlank();
        }
    }
}
