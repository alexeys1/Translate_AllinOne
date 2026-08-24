package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.cache.ItemTemplateCache;
import com.alexeys.translate_allinone.utils.cache.LookupResult;
import com.alexeys.translate_allinone.utils.cache.TranslationStatus;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationMetrics;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationPolicy;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentJsonException;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import com.alexeys.translate_allinone.utils.text.TemplateProcessor;
import com.alexeys.translate_allinone.utils.textmatcher.FlatNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
final class TooltipTemplateRuntime {
    private static final String STORED_LEGACY_PREFIX = "[taio:legacy]";
    private static final long CACHE_MIGRATION_LOG_THROTTLE_WINDOW_MILLIS = 5000L;
    private static final int CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT = 4096;
    private static final long FORCE_REFRESH_COMPAT_BYPASS_MILLIS = 300_000L;
    private static final int FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT = 4096;
    private static final String UNRESOLVED_PLACEHOLDER_ERROR = "Cached translation contains unresolved placeholders";
    static final Pattern STYLE_TAG_ID_PATTERN = Pattern.compile("</?s(\\d+)>");
    private static final Pattern NUMERIC_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{d(\\d+)}");
    private static final Pattern GLYPH_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{g(\\d+)}");
    private static final Pattern PARAGRAPH_SOURCE_PLACEHOLDER_PATTERN = Pattern.compile("\\{([dg])(\\d+)}");
    private static final List<String> PARAGRAPH_DYNAMIC_SUFFIXES = List.of(
            "分钟", "小时", "min", "ms", "px", "%", "s", "h", "d", "秒", "天", "格", "级", "点", "次", "个", "块", "米"
    );
    private static final Pattern LEGACY_FORMATTING_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?");
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");
    private static final ConcurrentHashMap<CacheMigrationLogKey, CacheMigrationLogThrottleState> CACHE_MIGRATION_LOG_THROTTLE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ITEM_LOCAL_HIT_LOG_TIMESTAMPS =
            new ConcurrentHashMap<>();
    private static final long ITEM_LOCAL_HIT_LOG_THROTTLE_MILLIS = 5000L;
    private static final WynnSharedDictionaryService SHARED_DICTIONARY_SERVICE = WynnSharedDictionaryService.getInstance();
    private static final Set<String> SUSPICIOUS_ENGLISH_CONNECTORS = Set.of(
            "a",
            "an",
            "and",
            "are",
            "as",
            "at",
            "be",
            "by",
            "for",
            "from",
            "in",
            "into",
            "is",
            "of",
            "on",
            "or",
            "the",
            "to",
            "using",
            "when",
            "while",
            "with",
            "you",
            "your"
    );
    private static final StyleSpriteSource.Font WYNNCRAFT_TOOLTIP_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "language/wynncraft"));

    static boolean isSuspiciousEnglishConnector(String word) {
        return word != null && SUSPICIOUS_ENGLISH_CONNECTORS.contains(word);
    }

    private TooltipTemplateRuntime() {
    }

    enum CachedTranslationFormat {
        TAGGED,
        LEGACY
    }

    record PreparedTooltipTemplate(
            Text sourceLine,
            boolean useTagStylePreservation,
            StylePreserver.ExtractionResult styleResult,
            TemplateProcessor.TemplateExtractionResult templateResult,
            TemplateProcessor.DecorativeGlyphExtractionResult glyphResult,
            String unicodeTemplate,
            String normalizedTemplate,
            String translationTemplateKey
    ) {
    }

    record PreparedParagraphTemplate(
            String translationTemplateKey,
            String componentTranslationTemplateKey,
            Map<Integer, Style> styleMap,
            List<String> templateValues,
            List<String> glyphValues,
            Integer bodyStyleId,
            int wrapWidth,
            List<Integer> lineEndStyleIds,
            List<ParagraphSemanticSlot> semanticSlots,
            String semanticSlotCompatibilityTemplateKey,
            ParagraphTranslationPlan translationPlan
    ) {
        String legacyComponentTranslationTemplateKey() {
            String legacy = semanticSlotCompatibilityTemplateKey;
            for (ParagraphSemanticSlot slot : semanticSlots) {
                String styledPlaceholder = "<s" + slot.styleId() + ">" + slot.placeholder() + "</s" + slot.styleId() + ">";
                String styledSource = "<s" + slot.styleId() + ">" + slot.sourceText() + "</s" + slot.styleId() + ">";
                legacy = legacy.replace(styledPlaceholder, styledSource);
            }
            return legacy;
        }
    }

    record ParagraphSemanticSlot(
            String id,
            int styleId,
            String sourceText
    ) {
        String placeholder() {
            return "{" + id + "}";
        }
    }

    private record ParagraphSemanticPlan(
            String template,
            Integer bodyStyleId,
            List<ParagraphSemanticSlot> slots
    ) {
    }

    private record CompatibilityTemplateKey(String key, CachedTranslationFormat format) {
    }

    private record AdaptedCachedTranslation(String translation, CachedTranslationFormat format) {
    }

    private record DecodedStoredTranslation(String translation, CachedTranslationFormat format) {
    }

    private record LocalDictionaryEvaluation(
            WynnSharedDictionaryService.LookupResult lookupResult,
            String rejectionReason,
            String lookupSource,
            String lookupText
    ) {
        private boolean hit() {
            return lookupResult != null && lookupResult.hit();
        }

        private boolean accepted() {
            return hit() && rejectionReason == null;
        }
    }

    private record ResolvedTemplateLookup(
            LookupResult lookupResult,
            CachedTranslationFormat format,
            Text renderedLineOverride
    ) {
    }

    private record CacheMigrationLogKey(
            String phase,
            CachedTranslationFormat format,
            String newKey,
            String compatibilityKey
    ) {
    }

    private static final class CacheMigrationLogThrottleState {
        private long lastLoggedAtMillis = 0L;
        private int suppressedCount = 0;
    }

    static TooltipTranslationSupport.TooltipLineResult translateLine(Text line, boolean useTagStylePreservation) {
        if (!TranslationFeatureGate.isEnabled()) {
            return new TooltipTranslationSupport.TooltipLineResult(line, false, false);
        }
        return translatePreparedTemplate(prepareTemplate(line, useTagStylePreservation));
    }

    static boolean hasLocalDictionaryTranslation(Text line) {
        if (line == null) {
            return false;
        }
        return hasLocalDictionaryTranslation(line.getString());
    }

    static boolean hasLocalDictionaryTranslation(String sourceText) {
        if (!TranslationFeatureGate.isEnabled() || sourceText == null || sourceText.isBlank()) {
            return false;
        }
        return evaluateLocalDictionaryLookup(sourceText).accepted();
    }

    static String describeLocalDictionaryLookup(Text line) {
        if (line == null) {
            return "dictionary=miss";
        }
        return describeLocalDictionaryLookup(line.getString());
    }

    static String describeLocalDictionaryLookup(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "dictionary=miss";
        }

        LocalDictionaryEvaluation evaluation = evaluateLocalDictionaryLookup(sourceText);
        if (!evaluation.hit()) {
            return "dictionary=miss";
        }

        WynnSharedDictionaryService.LookupResult lookup = evaluation.lookupResult();
        String dictionaryId = lookup.dictionaryId() == null || lookup.dictionaryId().isBlank()
                ? "unknown"
                : lookup.dictionaryId();
        String matchType = lookup.matchType() == null ? "" : lookup.matchType().name();
            String description = "dictionary=" + dictionaryId
                + ", match=" + matchType
                + ", translation=\"" + truncateForLog(lookup.translation(), 120) + "\"";
        if (evaluation.lookupSource() != null && !evaluation.lookupSource().isBlank()) {
            description += ", source=" + evaluation.lookupSource();
        }
        if (evaluation.lookupText() != null && !evaluation.lookupText().isBlank()) {
            description += ", lookupText=\"" + truncateForLog(evaluation.lookupText(), 120) + "\"";
        }
        if (!evaluation.accepted()) {
            description += ", rejected=\"" + truncateForLog(evaluation.rejectionReason(), 120) + "\"";
        }
        return description;
    }

    static boolean shouldAcceptLocalDictionaryTranslation(
            String sourceText,
            WynnSharedDictionaryService.LookupResult localLookup
    ) {
        return localLookup != null
                && localLookup.hit()
                && describeLocalDictionaryRejectionReason(sourceText, localLookup) == null;
    }

    static WynnSharedDictionaryService.LookupResult lookupAcceptedLocalDictionaryTranslation(String sourceText) {
        return lookupAcceptedLocalDictionaryTranslation(sourceText, SHARED_DICTIONARY_SERVICE::lookupItemLine);
    }

    static WynnSharedDictionaryService.LookupResult lookupAcceptedLocalDictionaryTranslation(
            String sourceText,
            Function<String, WynnSharedDictionaryService.LookupResult> lookupFunction
    ) {
        if (!TranslationFeatureGate.isEnabled()) {
            return null;
        }
        LocalDictionaryEvaluation evaluation = evaluateLocalDictionaryLookup(sourceText, lookupFunction);
        if (!evaluation.accepted()) {
            logItemLocalDictionaryEvaluation(currentItemTranslateConfig(), "paragraph", sourceText, evaluation);
        }
        return evaluation.accepted() ? evaluation.lookupResult() : null;
    }

    static void logAcceptedLocalDictionaryHit(
            ItemTranslateConfig config,
            WynnSharedDictionaryService.LookupResult localLookup,
            String sourceText
    ) {
        if (localLookup == null || !localLookup.hit()) {
            return;
        }

        throttledItemLocalLookupLog(
                config,
                localLookup.dictionaryId(),
                "hit",
                sourceText,
                "paragraph",
                sourceText,
                localLookup.matchType() == null ? "" : localLookup.matchType().name().toLowerCase(Locale.ROOT),
                "",
                localLookup.translation()
        );
    }

    static TooltipTranslationSupport.TooltipLineResult translatePreparedTemplate(PreparedTooltipTemplate preparedTemplate) {
        if (!TranslationFeatureGate.isEnabled()) {
            return new TooltipTranslationSupport.TooltipLineResult(preparedTemplate.sourceLine(), false, false);
        }
        ResolvedTemplateLookup resolvedLookup = resolveLookup(preparedTemplate);
        LookupResult lookupResult = resolvedLookup.lookupResult();
        TranslationStatus status = lookupResult.status();
        boolean pending = status == TranslationStatus.PENDING
                || status == TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = false;

        String translatedTemplate = lookupResult.translation();
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(preparedTemplate.normalizedTemplate(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values()
        );
        Text originalTextObject = preparedTemplate.useTagStylePreservation()
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, preparedTemplate.styleResult().styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, preparedTemplate.styleResult().styleMap);

        Text finalTooltipLine;
        String errorMessage = "";
        if (status == TranslationStatus.TRANSLATED && resolvedLookup.renderedLineOverride() != null) {
            finalTooltipLine = resolvedLookup.renderedLineOverride();
        } else if (status == TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                    TemplateProcessor.reassemble(translatedTemplate, preparedTemplate.templateResult().values()),
                    preparedTemplate.glyphResult().values(),
                    true
            );
            finalTooltipLine = resolvedLookup.format() == CachedTranslationFormat.TAGGED
                    ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, preparedTemplate.styleResult().styleMap, true)
                    : StylePreserver.fromLegacyText(reassembledTranslated);
        } else if (status == TranslationStatus.ERROR) {
            errorMessage = lookupResult.errorMessage();
            if (TooltipInternalLineSupport.isMissingKeyIssue(errorMessage)) {
                pending = true;
                missingKeyIssue = true;
                errorMessage = "";
                finalTooltipLine = originalTextObject;
            } else {
                finalTooltipLine = originalTextObject;
            }
        } else {
            finalTooltipLine = AnimationManager.getAnimatedStyledText(
                    originalTextObject,
                    preparedTemplate.translationTemplateKey(),
                    false
            );
        }

        return new TooltipTranslationSupport.TooltipLineResult(finalTooltipLine, pending, missingKeyIssue, errorMessage);
    }

    static Text peekTranslatedPreparedTemplate(PreparedTooltipTemplate preparedTemplate) {
        if (!TranslationFeatureGate.isEnabled() || preparedTemplate == null) {
            return null;
        }
        LocalDictionaryEvaluation localEvaluation = evaluateLocalDictionaryLookup(preparedTemplate.sourceLine().getString());
        if (localEvaluation.accepted()) {
            return renderLocalDictionaryTranslation(preparedTemplate, localEvaluation.lookupResult().translation());
        }

        if (shouldBypassCompatibilityFallback(preparedTemplate.translationTemplateKey())) {
            return null;
        }

        CachedTranslationFormat currentFormat = preparedTemplate.useTagStylePreservation()
                ? CachedTranslationFormat.TAGGED
                : CachedTranslationFormat.LEGACY;
        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        LookupResult currentLookup = cache.peek(preparedTemplate.translationTemplateKey());
            Text current = renderPeekedTranslation(preparedTemplate, currentLookup, currentFormat);
        if (current != null) {
            return current;
        }

        for (CompatibilityTemplateKey compatibilityKey : collectCompatibilityKeys(preparedTemplate)) {
            Text translated = renderPeekedTranslation(
                    preparedTemplate,
                    cache.peek(compatibilityKey.key()),
                    compatibilityKey.format()
            );
            if (translated != null) {
                return translated;
            }
        }
        return null;
    }

    private static Text renderPeekedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            LookupResult lookup,
            CachedTranslationFormat defaultFormat
    ) {
        if (lookup == null || lookup.status() != TranslationStatus.TRANSLATED) {
            return null;
        }
        DecodedStoredTranslation decoded = decodeStoredTranslation(lookup.translation(), defaultFormat);
        if (!isUsableCachedTranslation(preparedTemplate, decoded.translation(), decoded.format())) {
            return null;
        }
        return renderCompatibilityText(preparedTemplate, decoded.translation(), decoded.format());
    }

    static String extractTemplateKeyForLine(Text line, boolean useTagStylePreservation) {
        return prepareTemplate(line, useTagStylePreservation).translationTemplateKey();
    }

    static PreparedTooltipTemplate prepareTemplate(Text line, boolean useTagStylePreservation) {
        Text normalizedLine = normalizeInlineFormattingCodes(line);
        boolean resolvedUseTagStylePreservation = shouldUseTagStylePreservation(normalizedLine, useTagStylePreservation);
        StylePreserver.ExtractionResult styleResult = resolvedUseTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(normalizedLine)
                : StylePreserver.extractAndMark(normalizedLine);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = resolvedUseTagStylePreservation
                ? TemplateProcessor.extractDecorativeGlyphTags(
                unicodeTemplate,
                styleId -> {
                    Style style = styleResult.styleMap.get(styleId);
                    return hasCustomFont(style);
                }
        )
                : new TemplateProcessor.DecorativeGlyphExtractionResult(unicodeTemplate, List.of());
        String normalizedTemplate = resolvedUseTagStylePreservation
                ? TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(glyphResult.template())
                : glyphResult.template();
        String translationTemplateKey = resolvedUseTagStylePreservation
                ? normalizedTemplate
                : StylePreserver.toLegacyTemplate(unicodeTemplate, styleResult.styleMap);
        return new PreparedTooltipTemplate(
                line,
                resolvedUseTagStylePreservation,
                styleResult,
                templateResult,
                glyphResult,
                unicodeTemplate,
                normalizedTemplate,
                translationTemplateKey
        );
    }

    static PreparedTooltipTemplate prepareComponentTemplate(PreparedTooltipTemplate preparedTemplate) {
        if (preparedTemplate == null || preparedTemplate.sourceLine() == null) {
            return null;
        }
        return prepareTemplate(preparedTemplate.sourceLine(), true);
    }

    static PreparedParagraphTemplate prepareParagraphTemplate(List<PreparedTooltipTemplate> preparedLines) {
        if (preparedLines == null || preparedLines.isEmpty()) {
            return null;
        }

        String legacyTemplateKey = combinePreparedParagraphTemplateKey(preparedLines);
        Map<Integer, Style> combinedStyleMap = new HashMap<>();
        List<String> combinedTemplateValues = new ArrayList<>();
        List<String> combinedGlyphValues = new ArrayList<>();
        List<Integer> lineEndStyleIds = new ArrayList<>();
        StringBuilder combinedTemplateKey = new StringBuilder();
        int nextStyleId = 0;
        int nextNumericId = 0;
        int nextGlyphId = 0;

        for (PreparedTooltipTemplate originalPreparedLine : preparedLines) {
            PreparedTooltipTemplate preparedLine = originalPreparedLine == null
                    || originalPreparedLine.useTagStylePreservation()
                    ? originalPreparedLine
                    : prepareComponentTemplate(originalPreparedLine);
            if (preparedLine == null || preparedLine.normalizedTemplate() == null || preparedLine.normalizedTemplate().isBlank()) {
                continue;
            }

            if (combinedTemplateKey.length() > 0) {
                appendParagraphSemanticBoundary(combinedTemplateKey, preparedLine.normalizedTemplate());
            }
            combinedTemplateKey.append(remapParagraphTemplateIds(
                    preparedLine.normalizedTemplate(),
                    nextStyleId,
                    nextNumericId,
                    nextGlyphId
            ));

            for (Map.Entry<Integer, Style> entry : preparedLine.styleResult().styleMap.entrySet()) {
                combinedStyleMap.put(entry.getKey() + nextStyleId, entry.getValue());
            }
            combinedTemplateValues.addAll(preparedLine.templateResult().values());
            for (String glyphValue : preparedLine.glyphResult().values()) {
                combinedGlyphValues.add(remapPatternIds(
                        glyphValue,
                        STYLE_TAG_ID_PATTERN,
                        "s",
                        nextStyleId,
                        true
                ));
            }

            int lineStyleCount = countStyleIds(preparedLine.styleResult().styleMap);
            nextStyleId += lineStyleCount;
            nextNumericId += preparedLine.templateResult().values().size();
            nextGlyphId += preparedLine.glyphResult().values().size();

            if (lineStyleCount > 0) {
                lineEndStyleIds.add(nextStyleId - 1);
            }
        }

        if (combinedTemplateKey.isEmpty()) {
            return null;
        }

        String componentTemplateKey = canonicalizeParagraphStyleIds(combinedTemplateKey.toString(), combinedStyleMap);
        List<String> canonicalGlyphValues = combinedGlyphValues.stream()
                .map(value -> canonicalizeParagraphGlyphStyles(value, componentTemplateKey, combinedStyleMap))
                .toList();
        Integer bodyStyleId = TooltipParagraphSupport.findDominantParagraphBodyStyleId(
                componentTemplateKey,
                combinedStyleMap
        );
        ParagraphSemanticPlan semanticPlan = buildParagraphSemanticPlan(componentTemplateKey, bodyStyleId);
        int wrapWidth = computeParagraphWrapWidth(preparedLines);
        ParagraphTranslationPlan translationPlan = buildParagraphTranslationPlan(
                preparedLines.stream().map(PreparedTooltipTemplate::sourceLine).toList(),
                componentTemplateKey,
                combinedStyleMap,
                combinedTemplateValues,
                canonicalGlyphValues,
                semanticPlan.bodyStyleId(),
                wrapWidth
        );
        return new PreparedParagraphTemplate(
                legacyTemplateKey,
                translationPlan.requestText(),
                combinedStyleMap,
                combinedTemplateValues,
                canonicalGlyphValues,
                semanticPlan.bodyStyleId(),
                wrapWidth,
                lineEndStyleIds,
                semanticPlan.slots(),
                semanticPlan.template(),
                translationPlan
        );
    }

    private static String combinePreparedParagraphTemplateKey(List<PreparedTooltipTemplate> preparedLines) {
        StringBuilder combined = new StringBuilder();
        int nextStyleId = 0;
        int nextNumericId = 0;
        int nextGlyphId = 0;
        for (PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.normalizedTemplate() == null || preparedLine.normalizedTemplate().isBlank()) {
                continue;
            }
            if (!combined.isEmpty()) {
                appendParagraphSemanticBoundary(combined, preparedLine.normalizedTemplate());
            }
            combined.append(remapParagraphTemplateIds(
                    preparedLine.normalizedTemplate(),
                    nextStyleId,
                    nextNumericId,
                    nextGlyphId
            ));
            nextStyleId += countStyleIds(preparedLine.styleResult().styleMap);
            nextNumericId += preparedLine.templateResult().values().size();
            nextGlyphId += preparedLine.glyphResult().values().size();
        }
        return combined.toString();
    }

    private static Text normalizeInlineFormattingCodes(Text line) {
        if (line == null || line.getString().indexOf('§') < 0) {
            return line == null ? Text.empty() : line;
        }
        MutableText normalized = Text.empty();
        line.visit((style, text) -> {
            appendInlineFormattedRuns(normalized, style, text);
            return Optional.empty();
        }, Style.EMPTY);
        return normalized;
    }

    private static void appendInlineFormattedRuns(MutableText target, Style inheritedStyle, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Style activeStyle = inheritedStyle == null ? Style.EMPTY : inheritedStyle;
        StringBuilder visible = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current != '§' || index + 1 >= text.length()) {
                visible.append(current);
                continue;
            }
            Formatting formatting = Formatting.byCode(Character.toLowerCase(text.charAt(index + 1)));
            if (formatting == null) {
                visible.append(current);
                continue;
            }
            appendStyledText(target, visible, activeStyle);
            activeStyle = formatting == Formatting.RESET || formatting.isColor()
                    ? Style.EMPTY.withFormatting(formatting)
                    : activeStyle.withFormatting(formatting);
            index++;
        }
        appendStyledText(target, visible, activeStyle);
    }

    private static void appendStyledText(MutableText target, StringBuilder text, Style style) {
        if (text.isEmpty()) {
            return;
        }
        target.append(Text.literal(text.toString()).setStyle(style == null ? Style.EMPTY : style));
        text.setLength(0);
    }

    private static ParagraphSemanticPlan buildParagraphSemanticPlan(String template, Integer preferredBodyStyleId) {
        if (template == null || template.isBlank()) {
            return new ParagraphSemanticPlan("", preferredBodyStyleId, List.of());
        }
        Integer bodyStyleId = preferredBodyStyleId;

        Pattern styledSpanPattern = Pattern.compile("<s(\\d+)>(.*?)</s\\1>");
        Matcher matcher = styledSpanPattern.matcher(template);
        StringBuilder rebuilt = new StringBuilder(template.length());
        List<ParagraphSemanticSlot> slots = new ArrayList<>();
        while (matcher.find()) {
            int styleId = Integer.parseInt(matcher.group(1));
            String content = matcher.group(2);
            if (Objects.equals(styleId, bodyStyleId) || !containsSemanticSlotText(content)) {
                matcher.appendReplacement(rebuilt, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            ParagraphSemanticSlot slot = new ParagraphSemanticSlot("slot" + slots.size(), styleId, content);
            slots.add(slot);
            String replacement = "<s" + styleId + ">" + slot.placeholder() + "</s" + styleId + ">";
            matcher.appendReplacement(rebuilt, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rebuilt);
        return new ParagraphSemanticPlan(rebuilt.toString(), bodyStyleId, List.copyOf(slots));
    }

    private static ParagraphTranslationPlan buildParagraphTranslationPlan(
            List<Text> sourceLines,
            String canonicalTemplate,
            Map<Integer, Style> styleMap,
            List<String> dynamicValues,
            List<String> glyphValues,
            Integer bodyStyleId,
            int wrapWidth
    ) {
        if (canonicalTemplate == null || canonicalTemplate.isBlank()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph normalized source is empty."
            );
        }
        String unsafeSourceIssue = describeUnsafeParagraphSource(canonicalTemplate);
        if (unsafeSourceIssue != null) {
            throw new ComponentJsonException(ComponentJsonException.Kind.DOCUMENT, unsafeSourceIssue);
        }

        Style bodyStyle = bodyStyleId == null
                ? Style.EMPTY
                : styleMap.getOrDefault(bodyStyleId, Style.EMPTY);
        List<ParagraphTranslationPlan.StyledRun> runs = new ArrayList<>();
        Map<String, Text> hardTokens = new LinkedHashMap<>();
        for (ParagraphSourceRun sourceRun : parseParagraphSourceRuns(canonicalTemplate)) {
            appendParagraphPlanRuns(
                    runs,
                    hardTokens,
                    sourceRun,
                    styleMap,
                    dynamicValues,
                    glyphValues,
                    bodyStyle
            );
        }
        runs = mergeEquivalentParagraphPlanRuns(runs, bodyStyle);

        StringBuilder request = new StringBuilder(canonicalTemplate.length());
        List<ParagraphTranslationPlan.AccentAnchor> anchors = new ArrayList<>();
        for (int index = 0; index < runs.size(); ) {
            ParagraphTranslationPlan.StyledRun run = runs.get(index);
            if (run.type() != ParagraphTranslationPlan.RunType.ACCENT) {
                request.append(run.token().isEmpty() ? run.sourceText() : run.token());
                index++;
                continue;
            }

            int end = index + 1;
            StringBuilder anchoredSource = new StringBuilder(run.sourceText());
            while (end < runs.size()) {
                ParagraphTranslationPlan.StyledRun next = runs.get(end);
                if (next.type() == ParagraphTranslationPlan.RunType.ACCENT
                        && equivalentNaturalLanguageStyle(run.style(), next.style())) {
                    anchoredSource.append(next.sourceText());
                    end++;
                    continue;
                }
                if (next.type() == ParagraphTranslationPlan.RunType.GLYPH
                        || next.type() == ParagraphTranslationPlan.RunType.DYNAMIC) {
                    anchoredSource.append(next.token());
                    end++;
                    continue;
                }
                break;
            }
            ParagraphTranslationPlan.AccentAnchor anchor = new ParagraphTranslationPlan.AccentAnchor(
                    "accent" + anchors.size(),
                    anchoredSource.toString(),
                    run.style()
            );
            anchors.add(anchor);
            request.append(anchor.beginToken()).append(anchoredSource).append(anchor.endToken());
            index = end;
        }

        String requestText = normalizeParagraphRequestWhitespace(request.toString());
        String hardTopology = runs.stream()
                .filter(run -> run.type() == ParagraphTranslationPlan.RunType.DYNAMIC
                        || run.type() == ParagraphTranslationPlan.RunType.GLYPH)
                .map(run -> run.type().name().toLowerCase(Locale.ROOT) + ':' + run.token())
                .reduce((left, right) -> left + ',' + right)
                .orElse("");
        String accentTopology = anchors.stream()
                .map(ParagraphTranslationPlan.AccentAnchor::id)
                .reduce((left, right) -> left + ',' + right)
                .orElse("");
        String stableIdentity = ParagraphTranslationPlan.INLINE_ANCHOR_STRATEGY
                + '\n' + requestText
                + '\n' + hardTopology
                + '\n' + accentTopology;
        return new ParagraphTranslationPlan(
                sourceLines,
                runs,
                requestText,
                bodyStyle,
                hardTokens,
                anchors,
                stableIdentity,
                ParagraphTranslationPlan.INLINE_ANCHOR_STRATEGY,
                wrapWidth
        );
    }

    private static void appendParagraphPlanRuns(
            List<ParagraphTranslationPlan.StyledRun> runs,
            Map<String, Text> hardTokens,
            ParagraphSourceRun sourceRun,
            Map<Integer, Style> styleMap,
            List<String> dynamicValues,
            List<String> glyphValues,
            Style bodyStyle
    ) {
        Style sourceStyle = sourceRun.styleId() == null
                ? bodyStyle
                : styleMap.getOrDefault(sourceRun.styleId(), Style.EMPTY);
        Matcher placeholderMatcher = PARAGRAPH_SOURCE_PLACEHOLDER_PATTERN.matcher(sourceRun.content());
        int previousEnd = 0;
        while (placeholderMatcher.find()) {
            boolean dynamic = "d".equals(placeholderMatcher.group(1));
            int visibleStart = dynamic
                    ? paragraphDynamicValueStart(sourceRun.content(), placeholderMatcher.start(), previousEnd)
                    : placeholderMatcher.start();
            int visibleEnd = dynamic
                    ? paragraphDynamicValueEnd(sourceRun.content(), placeholderMatcher.end())
                    : placeholderMatcher.end();
            appendParagraphNaturalRun(
                    runs,
                    sourceRun.content().substring(previousEnd, visibleStart),
                    sourceStyle,
                    bodyStyle
            );
            int sourceIndex = Integer.parseInt(placeholderMatcher.group(2)) - 1;
            String token = dynamic ? "{value" + sourceIndex + "}" : "{glyph" + sourceIndex + "}";
            Text component = dynamic
                    ? dynamicValueComponent(
                            dynamicValues,
                            sourceIndex,
                            sourceRun.content().substring(visibleStart, placeholderMatcher.start()),
                            sourceRun.content().substring(placeholderMatcher.end(), visibleEnd),
                            sourceStyle
                    )
                    : glyphValueComponent(glyphValues, sourceIndex, sourceStyle, styleMap);
            if (sourceIndex < 0 || hardTokens.putIfAbsent(token, component) != null) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.DOCUMENT,
                        "Coherent paragraph hard token topology is invalid: " + token
                );
            }
            runs.add(new ParagraphTranslationPlan.StyledRun(
                    dynamic ? ParagraphTranslationPlan.RunType.DYNAMIC : ParagraphTranslationPlan.RunType.GLYPH,
                    component.getString(),
                    component.getStyle(),
                    token
            ));
            previousEnd = visibleEnd;
        }
        appendParagraphNaturalRun(
                runs,
                sourceRun.content().substring(previousEnd),
                sourceStyle,
                bodyStyle
        );
    }

    private static void appendParagraphNaturalRun(
            List<ParagraphTranslationPlan.StyledRun> runs,
            String text,
            Style sourceStyle,
            Style bodyStyle
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean accent = semanticTextScore(text) > 0
                && !equivalentNaturalLanguageStyle(sourceStyle, bodyStyle);
        runs.add(new ParagraphTranslationPlan.StyledRun(
                accent ? ParagraphTranslationPlan.RunType.ACCENT : ParagraphTranslationPlan.RunType.BODY,
                text,
                accent ? sourceStyle : bodyStyle,
                ""
        ));
    }

    private static Text dynamicValueComponent(
            List<String> values,
            int index,
            String prefix,
            String suffix,
            Style style
    ) {
        if (values == null || index < 0 || index >= values.size()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph dynamic value mapping is incomplete: value" + index
            );
        }
        return Text.literal((prefix == null ? "" : prefix) + values.get(index) + (suffix == null ? "" : suffix))
                .setStyle(style == null ? Style.EMPTY : style);
    }

    private static int paragraphDynamicValueStart(String text, int placeholderStart, int minimum) {
        if (text == null || placeholderStart <= minimum || placeholderStart > text.length()) {
            return placeholderStart;
        }
        char previous = text.charAt(placeholderStart - 1);
        return previous == '+' || previous == '-' ? placeholderStart - 1 : placeholderStart;
    }

    private static int paragraphDynamicValueEnd(String text, int placeholderEnd) {
        if (text == null || placeholderEnd < 0 || placeholderEnd >= text.length()) {
            return placeholderEnd;
        }
        int suffixStart = placeholderEnd;
        int tokenStart = suffixStart;
        if (Character.isWhitespace(text.charAt(tokenStart))) {
            tokenStart++;
        }
        for (String suffix : PARAGRAPH_DYNAMIC_SUFFIXES) {
            if (text.startsWith(suffix, tokenStart)
                    && paragraphDynamicSuffixHasBoundary(text, tokenStart + suffix.length(), suffix)) {
                return tokenStart + suffix.length();
            }
        }
        return placeholderEnd;
    }

    private static boolean paragraphDynamicSuffixHasBoundary(String text, int suffixEnd, String suffix) {
        if (suffix == null || suffix.isEmpty() || suffixEnd >= text.length()) {
            return true;
        }
        int lastCodePoint = suffix.codePointBefore(suffix.length());
        int nextCodePoint = text.codePointAt(suffixEnd);
        return !Character.isLetter(lastCodePoint) || !Character.isLetter(nextCodePoint);
    }

    private static Text glyphValueComponent(
            List<String> values,
            int index,
            Style fallbackStyle,
            Map<Integer, Style> styleMap
    ) {
        if (values == null || index < 0 || index >= values.size()) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph decorative glyph mapping is incomplete: glyph" + index
            );
        }
        String value = values.get(index);
        if (STYLE_TAG_ID_PATTERN.matcher(value).find()) {
            return StylePreserver.reapplyStylesFromTags(value, styleMap, false);
        }
        return Text.literal(value).setStyle(fallbackStyle == null ? Style.EMPTY : fallbackStyle);
    }

    private static List<ParagraphTranslationPlan.StyledRun> mergeEquivalentParagraphPlanRuns(
            List<ParagraphTranslationPlan.StyledRun> sourceRuns,
            Style bodyStyle
    ) {
        List<ParagraphTranslationPlan.StyledRun> merged = new ArrayList<>();
        for (ParagraphTranslationPlan.StyledRun run : sourceRuns) {
            if (run == null || (run.sourceText().isEmpty() && run.token().isEmpty())) {
                continue;
            }
            if (!merged.isEmpty()) {
                ParagraphTranslationPlan.StyledRun previous = merged.getLast();
                if (previous.token().isEmpty()
                        && run.token().isEmpty()
                        && previous.type() == run.type()
                        && equivalentNaturalLanguageStyle(previous.style(), run.style())) {
                    merged.set(
                            merged.size() - 1,
                            new ParagraphTranslationPlan.StyledRun(
                                    previous.type(),
                                    previous.sourceText() + run.sourceText(),
                                    previous.style(),
                                    ""
                            )
                    );
                    continue;
                }
            }
            merged.add(run);
        }

        for (int index = 1; index + 1 < merged.size(); ) {
            ParagraphTranslationPlan.StyledRun middle = merged.get(index);
            ParagraphTranslationPlan.StyledRun previous = merged.get(index - 1);
            ParagraphTranslationPlan.StyledRun next = merged.get(index + 1);
            if (middle.type() == ParagraphTranslationPlan.RunType.BODY
                    && middle.sourceText().isBlank()
                    && previous.type() == ParagraphTranslationPlan.RunType.ACCENT
                    && next.type() == ParagraphTranslationPlan.RunType.ACCENT
                    && equivalentNaturalLanguageStyle(previous.style(), next.style())) {
                merged.set(
                        index - 1,
                        new ParagraphTranslationPlan.StyledRun(
                                ParagraphTranslationPlan.RunType.ACCENT,
                                previous.sourceText() + middle.sourceText() + next.sourceText(),
                                previous.style(),
                                ""
                        )
                );
                merged.remove(index + 1);
                merged.remove(index);
                continue;
            }
            index++;
        }
        return List.copyOf(merged);
    }

    private static boolean equivalentNaturalLanguageStyle(Style left, Style right) {
        return StylePreserver.sanitizeStyleForComparison(left, true)
                .equals(StylePreserver.sanitizeStyleForComparison(right, true));
    }

    private static List<ParagraphSourceRun> parseParagraphSourceRuns(String taggedText) {
        List<ParagraphSourceRun> runs = new ArrayList<>();
        Matcher matcher = STYLE_TAG_ID_PATTERN.matcher(taggedText);
        int previousEnd = 0;
        Integer activeStyleId = null;
        while (matcher.find()) {
            if (matcher.start() > previousEnd) {
                runs.add(new ParagraphSourceRun(activeStyleId, taggedText.substring(previousEnd, matcher.start())));
            }
            boolean closing = taggedText.charAt(matcher.start() + 1) == '/';
            activeStyleId = closing ? null : Integer.parseInt(matcher.group(1));
            previousEnd = matcher.end();
        }
        if (previousEnd < taggedText.length()) {
            runs.add(new ParagraphSourceRun(activeStyleId, taggedText.substring(previousEnd)));
        }
        if (activeStyleId != null) {
            throw new ComponentJsonException(
                    ComponentJsonException.Kind.DOCUMENT,
                    "Coherent paragraph source style tag is not closed."
            );
        }
        return List.copyOf(runs);
    }

    private static String describeUnsafeParagraphSource(String text) {
        if (text.indexOf('§') >= 0) {
            return "Coherent paragraph contains an unparsed Minecraft formatting code.";
        }
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.PRIVATE_USE || type == Character.UNASSIGNED) {
                return "Coherent paragraph contains an unextracted decorative glyph: U+"
                        + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT) + ".";
            }
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                return "Coherent paragraph contains an illegal control character.";
            }
            offset += Character.charCount(codePoint);
        }
        return null;
    }

    private static String normalizeParagraphRequestWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private record ParagraphSourceRun(Integer styleId, String content) {
    }

    private static boolean containsSemanticSlotText(String content) {
        return semanticTextScore(content) > 0;
    }

    private static int semanticTextScore(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String visible = NUMERIC_PLACEHOLDER_ID_PATTERN.matcher(content).replaceAll("");
        visible = GLYPH_PLACEHOLDER_ID_PATTERN.matcher(visible).replaceAll("");
        int score = 0;
        for (int offset = 0; offset < visible.length(); ) {
            int codePoint = visible.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                score++;
            }
            offset += Character.charCount(codePoint);
        }
        return score;
    }

    static Text renderOriginalPreparedLine(PreparedTooltipTemplate preparedTemplate) {
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(preparedTemplate.normalizedTemplate(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values()
        );
        return preparedTemplate.useTagStylePreservation()
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, preparedTemplate.styleResult().styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, preparedTemplate.styleResult().styleMap);
    }

    static Text renderComponentTemplateTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String translatedTemplate
    ) {
        return renderCompatibilityText(preparedTemplate, translatedTemplate, CachedTranslationFormat.TAGGED);
    }

    static Text normalizeDecorativePassthroughText(Text text) {
        if (text == null) {
            return null;
        }

        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(text);
        String normalized = TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(styleResult.markedText);
        if (normalized == null || normalized.isEmpty()) {
            return Text.empty();
        }
        return StylePreserver.reapplyStylesFromTags(normalized, styleResult.styleMap, true);
    }

    static Text preserveDecorativePrefixPassthroughText(Text text) {
        if (text == null) {
            return null;
        }

        StylePreserver.ExtractionResult styleResult = StylePreserver.extractAndMarkWithTags(text);
        if (styleResult.markedText == null || styleResult.markedText.isEmpty()) {
            return Text.empty();
        }
        return StylePreserver.reapplyStylesFromTags(styleResult.markedText, styleResult.styleMap, true);
    }

    static void registerForceRefreshCompatBypass(Iterable<String> translationTemplateKeys) {
        if (translationTemplateKeys == null) {
            return;
        }

        cleanupForceRefreshCompatBypassState();
        long expiresAtMillis = System.currentTimeMillis() + FORCE_REFRESH_COMPAT_BYPASS_MILLIS;
        for (String translationTemplateKey : translationTemplateKeys) {
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                continue;
            }
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.put(translationTemplateKey, expiresAtMillis);
        }
    }

    static boolean containsNumericPlaceholder(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("{d1}") || text.matches(".*\\{d\\d+}.*");
    }

    static boolean containsDecorativeGlyph(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isDecorativeGlyphCodePoint(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    static String stripDecorativeGlyphsForHeuristics(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(raw.length());
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (isDecorativeGlyphCodePoint(codePoint)) {
                builder.append(' ');
            } else {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static ResolvedTemplateLookup resolveLookup(PreparedTooltipTemplate preparedTemplate) {
        long resolveStartedAtNanos = System.nanoTime();
        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        CachedTranslationFormat currentFormat = preparedTemplate.useTagStylePreservation()
                ? CachedTranslationFormat.TAGGED
                : CachedTranslationFormat.LEGACY;
        boolean invalidCurrentTranslation = false;
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;

        LocalDictionaryEvaluation localEvaluation =
                evaluateLocalDictionaryLookup(preparedTemplate.sourceLine().getString());
        logItemLocalDictionaryEvaluation(
                config,
                "line-template",
                preparedTemplate.sourceLine().getString(),
                localEvaluation
        );
        if (localEvaluation.accepted()) {
            WynnSharedDictionaryService.LookupResult localLookup = localEvaluation.lookupResult();
            return new ResolvedTemplateLookup(
                    translatedLookup(localLookup.translation()),
                    CachedTranslationFormat.LEGACY,
                    renderLocalDictionaryTranslation(preparedTemplate, localLookup.translation())
            );
        }

        LookupResult currentLookup = cache.peek(preparedTemplate.translationTemplateKey());
        DecodedStoredTranslation decodedCurrentTranslation = decodeStoredTranslation(currentLookup.translation(), currentFormat);
        if (currentLookup.status() == TranslationStatus.TRANSLATED
                && isUsableCachedTranslation(
                preparedTemplate,
                decodedCurrentTranslation.translation(),
                decodedCurrentTranslation.format()
        )) {
            clearForceRefreshCompatBypass(preparedTemplate.translationTemplateKey());
            return new ResolvedTemplateLookup(
                    translatedLookup(decodedCurrentTranslation.translation()),
                    decodedCurrentTranslation.format(),
                    null
            );
        } else if (currentLookup.status() == TranslationStatus.TRANSLATED) {
            invalidCurrentTranslation = true;
            logCacheMigrationIfDev(
                    config,
                    "reject-new-key",
                    preparedTemplate.translationTemplateKey(),
                    null,
                    decodedCurrentTranslation.format(),
                    false,
                    "Current newKey cache entry still renders unresolved placeholders; forcing refresh."
            );
        }

        if (shouldBypassCompatibilityFallback(preparedTemplate.translationTemplateKey())) {
            if (invalidCurrentTranslation) {
                cache.markInvalidTranslation(
                        preparedTemplate.translationTemplateKey(),
                        UNRESOLVED_PLACEHOLDER_ERROR
                );
                registerForceRefreshCompatBypass(List.of(preparedTemplate.translationTemplateKey()));
                return new ResolvedTemplateLookup(
                        new LookupResult(TranslationStatus.ERROR, "", UNRESOLVED_PLACEHOLDER_ERROR),
                        currentFormat,
                        null
                );
            }
            return new ResolvedTemplateLookup(
                    cache.lookupOrQueue(preparedTemplate.translationTemplateKey()),
                    currentFormat,
                    null
            );
        }

        long collectCompatibilityKeysStartedAtNanos = System.nanoTime();
        List<CompatibilityTemplateKey> compatibilityKeys = collectCompatibilityKeys(preparedTemplate);
        long collectCompatibilityKeysElapsedNanos = System.nanoTime() - collectCompatibilityKeysStartedAtNanos;
        long compatibilityScanStartedAtNanos = System.nanoTime();
        for (CompatibilityTemplateKey compatibilityKey : compatibilityKeys) {
            LookupResult compatibilityLookup = cache.peek(compatibilityKey.key());
            if (compatibilityLookup.status() != TranslationStatus.TRANSLATED) {
                continue;
            }

            DecodedStoredTranslation decodedCompatibilityTranslation = decodeStoredTranslation(
                    compatibilityLookup.translation(),
                    compatibilityKey.format()
            );
            if (!isUsableCachedTranslation(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            )) {
                continue;
            }

            Text compatibilityRenderedText = renderCompatibilityText(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            );
            AdaptedCachedTranslation adaptedTranslation = adaptCachedTranslation(
                    preparedTemplate,
                    decodedCompatibilityTranslation.translation(),
                    decodedCompatibilityTranslation.format()
            );
            if (adaptedTranslation != null
                    && adaptedTranslation.translation() != null
                    && !adaptedTranslation.translation().isBlank()
                    && isSafeAdaptedTranslation(preparedTemplate, adaptedTranslation, compatibilityRenderedText)) {
                long promoteStartedAtNanos = System.nanoTime();
                cache.promoteTranslation(
                        preparedTemplate.translationTemplateKey(),
                        encodeStoredTranslation(adaptedTranslation.translation(), adaptedTranslation.format())
                );
                long promoteElapsedNanos = System.nanoTime() - promoteStartedAtNanos;
                logCacheMigrationIfDev(
                        config,
                        "promote",
                        preparedTemplate.translationTemplateKey(),
                        compatibilityKey.key(),
                        decodedCompatibilityTranslation.format(),
                        true,
                        adaptedTranslation.format() == decodedCompatibilityTranslation.format()
                                ? "Reused compatibility cache entry and wrote it into newKey."
                                : "Reused compatibility cache entry, adapted it, and wrote it into newKey."
                );
                logCacheMigrationTimingIfDev(
                        config,
                        "promote",
                        preparedTemplate.translationTemplateKey(),
                        compatibilityKey.key(),
                        decodedCompatibilityTranslation.format(),
                        collectCompatibilityKeysElapsedNanos,
                        System.nanoTime() - compatibilityScanStartedAtNanos,
                        promoteElapsedNanos,
                        System.nanoTime() - resolveStartedAtNanos,
                        "compatibilityKeyCount=" + compatibilityKeys.size()
                );
                return new ResolvedTemplateLookup(
                        translatedLookup(adaptedTranslation.translation()),
                        adaptedTranslation.format(),
                        null
                );
            }

            long promoteStartedAtNanos = System.nanoTime();
            cache.promoteTranslation(
                    preparedTemplate.translationTemplateKey(),
                    encodeStoredTranslation(
                            decodedCompatibilityTranslation.translation(),
                            decodedCompatibilityTranslation.format()
                    )
            );
            long promoteElapsedNanos = System.nanoTime() - promoteStartedAtNanos;
            logCacheMigrationIfDev(
                    config,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "promote-legacy"
                            : "promote-compatible-format",
                    preparedTemplate.translationTemplateKey(),
                    compatibilityKey.key(),
                    decodedCompatibilityTranslation.format(),
                    true,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "Reused compatibility cache entry and wrote legacy-compatible content into newKey."
                            : "Reused compatibility cache entry and wrote compatible content into newKey."
            );
            logCacheMigrationTimingIfDev(
                    config,
                    decodedCompatibilityTranslation.format() == CachedTranslationFormat.LEGACY
                            ? "promote-legacy"
                            : "promote-compatible-format",
                    preparedTemplate.translationTemplateKey(),
                    compatibilityKey.key(),
                    decodedCompatibilityTranslation.format(),
                    collectCompatibilityKeysElapsedNanos,
                    System.nanoTime() - compatibilityScanStartedAtNanos,
                    promoteElapsedNanos,
                    System.nanoTime() - resolveStartedAtNanos,
                    "compatibilityKeyCount=" + compatibilityKeys.size()
            );
            return new ResolvedTemplateLookup(
                    translatedLookup(decodedCompatibilityTranslation.translation()),
                    decodedCompatibilityTranslation.format(),
                    null
            );
        }

        if (invalidCurrentTranslation) {
            cache.markInvalidTranslation(
                    preparedTemplate.translationTemplateKey(),
                    UNRESOLVED_PLACEHOLDER_ERROR
            );
            return new ResolvedTemplateLookup(
                    new LookupResult(TranslationStatus.ERROR, "", UNRESOLVED_PLACEHOLDER_ERROR),
                    currentFormat,
                    null
            );
        }

        return new ResolvedTemplateLookup(cache.lookupOrQueue(preparedTemplate.translationTemplateKey()), currentFormat, null);
    }

    private static LocalDictionaryEvaluation evaluateLocalDictionaryLookup(String sourceText) {
        return evaluateLocalDictionaryLookup(sourceText, SHARED_DICTIONARY_SERVICE::lookupItemLine);
    }

    private static LocalDictionaryEvaluation evaluateLocalDictionaryLookup(
            String sourceText,
            Function<String, WynnSharedDictionaryService.LookupResult> lookupFunction
    ) {
        if (sourceText == null || sourceText.isBlank()) {
            return new LocalDictionaryEvaluation(null, null, "", "");
        }

        String visibleSource = normalizeLocalDictionaryLookupSourceText(sourceText);
        LocalDictionaryEvaluation visibleEvaluation = evaluateLocalDictionaryLookupCandidate(
                sourceText,
                visibleSource,
                "visible",
                lookupFunction
        );
        if (visibleEvaluation.accepted()) {
            return visibleEvaluation;
        }

        LocalDictionaryEvaluation rawEvaluation = null;
        if (!sameDictionaryLookupText(sourceText, visibleSource)) {
            rawEvaluation = evaluateLocalDictionaryLookupCandidate(
                    sourceText,
                    sourceText,
                    "raw",
                    lookupFunction
            );
            if (rawEvaluation.accepted()) {
                return rawEvaluation;
            }
        }

        if (rawEvaluation != null && rawEvaluation.hit()) {
            return rawEvaluation;
        }
        if (visibleEvaluation.hit()) {
            return visibleEvaluation;
        }
        return rawEvaluation != null
                ? rawEvaluation
                : visibleEvaluation;
    }

    private static LocalDictionaryEvaluation evaluateLocalDictionaryLookupCandidate(
            String originalSourceText,
            String lookupSourceText,
            String lookupSourceLabel,
            Function<String, WynnSharedDictionaryService.LookupResult> lookupFunction
    ) {
        if (lookupSourceText == null || lookupSourceText.isBlank()) {
            return new LocalDictionaryEvaluation(null, null, lookupSourceLabel, "");
        }

        WynnSharedDictionaryService.LookupResult lookup = lookupFunction == null
                ? null
                : lookupFunction.apply(lookupSourceText);
        if (lookup == null || !lookup.hit()) {
            return new LocalDictionaryEvaluation(lookup, null, lookupSourceLabel, lookupSourceText);
        }
        return new LocalDictionaryEvaluation(
                lookup,
                describeLocalDictionaryRejectionReason(originalSourceText, lookup),
                lookupSourceLabel,
                lookupSourceText
        );
    }

    private static boolean sameDictionaryLookupText(String rawSourceText, String normalizedVisibleSource) {
        if (rawSourceText == null || normalizedVisibleSource == null) {
            return false;
        }
        return normalizeLocalDictionaryLookupSourceText(rawSourceText).equals(normalizedVisibleSource)
                && rawSourceText.equals(normalizedVisibleSource);
    }

    private static String describeLocalDictionaryRejectionReason(
            String sourceText,
            WynnSharedDictionaryService.LookupResult localLookup
    ) {
        if (!shouldApplyLocalDictionaryEnglishFallbackHeuristics()
                || sourceText == null
                || sourceText.isBlank()
                || localLookup == null
                || !localLookup.hit()) {
            return null;
        }

        String visibleSource = normalizeLocalDictionaryLookupSourceText(sourceText);
        String visibleTranslation = normalizeLocalDictionaryLookupSourceText(localLookup.translation());
        if (visibleSource.isEmpty() || visibleTranslation.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> sourceEnglishWords = extractEnglishWordTokens(visibleSource);
        LinkedHashSet<String> translatedEnglishWords = extractEnglishWordTokens(visibleTranslation);
        if (sourceEnglishWords.isEmpty() || translatedEnglishWords.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> overlappingEnglishWords = new LinkedHashSet<>(translatedEnglishWords);
        overlappingEnglishWords.retainAll(sourceEnglishWords);
        if (overlappingEnglishWords.isEmpty()) {
            return null;
        }

        boolean translatedContainsCjk = containsCjkText(visibleTranslation);
        String connector = findSuspiciousEnglishConnector(overlappingEnglishWords);
        if (translatedContainsCjk && connector != null) {
            return "still contains source English connector: " + connector;
        }
        if (translatedContainsCjk && overlappingEnglishWords.size() >= 2) {
            return "still contains source English words: " + summarizeEnglishWords(overlappingEnglishWords, 4);
        }
        if (localLookup.matchType() == WynnSharedDictionaryService.MatchType.PATTERN
                && overlappingEnglishWords.size() >= 2) {
            return "pattern translation still contains source English words: "
                    + summarizeEnglishWords(overlappingEnglishWords, 4);
        }
        if (!translatedContainsCjk && overlappingEnglishWords.size() >= 3) {
            return "still looks untranslated: " + summarizeEnglishWords(overlappingEnglishWords, 4);
        }
        return null;
    }

    private static boolean shouldApplyLocalDictionaryEnglishFallbackHeuristics() {
        return TooltipParagraphSupport.shouldApplyChineseParagraphQualityHeuristics(resolveItemTranslateConfigOrDefault());
    }

    private static ItemTranslateConfig resolveItemTranslateConfigOrDefault() {
        try {
            if (Translate_AllinOne.getConfig() != null && Translate_AllinOne.getConfig().itemTranslate != null) {
                return Translate_AllinOne.getConfig().itemTranslate;
            }
        } catch (RuntimeException ignored) {
            // Unit tests can exercise tooltip helpers before config registration.
        }
        return new ItemTranslateConfig();
    }

    static String normalizeLocalDictionaryLookupSourceText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = LEGACY_FORMATTING_CODE_PATTERN.matcher(text).replaceAll("");
        normalized = normalized
                .replace('\u00A0', ' ')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"');
        normalized = TooltipRoutePlanner.normalizeTooltipText(stripDecorativeGlyphsForHeuristics(normalized));
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static LinkedHashSet<String> extractEnglishWordTokens(String text) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return words;
        }

        Matcher matcher = ENGLISH_WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (word.length() <= 1 && !SUSPICIOUS_ENGLISH_CONNECTORS.contains(word)) {
                continue;
            }
            words.add(word);
        }
        return words;
    }

    private static String findSuspiciousEnglishConnector(Iterable<String> words) {
        if (words == null) {
            return null;
        }

        for (String word : words) {
            if (word != null && SUSPICIOUS_ENGLISH_CONNECTORS.contains(word)) {
                return word;
            }
        }
        return null;
    }

    private static String summarizeEnglishWords(Iterable<String> words, int maxCount) {
        if (words == null || maxCount <= 0) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (count >= maxCount) {
                break;
            }
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(word);
            count++;
        }
        return summary.toString();
    }

    private static boolean containsCjkText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static LookupResult translatedLookup(String translation) {
        return new LookupResult(
                TranslationStatus.TRANSLATED,
                translation,
                null
        );
    }

    private static void logItemLocalDictionaryEvaluation(
            ItemTranslateConfig config,
            String phase,
            String originalText,
            LocalDictionaryEvaluation evaluation
    ) {
        if (evaluation == null) {
            return;
        }

        WynnSharedDictionaryService.LookupResult lookupResult = evaluation.lookupResult();
        boolean hit = lookupResult != null && lookupResult.hit();
        String status = evaluation.accepted() ? "hit" : (hit ? "rejected" : "miss");
        String dictionaryId = hit ? lookupResult.dictionaryId() : "";
        throttledItemLocalLookupLog(
                config,
                dictionaryId,
                status,
                originalText,
                evaluation.lookupSource(),
                evaluation.lookupText(),
                lookupResult == null || lookupResult.matchType() == null
                        ? ""
                        : lookupResult.matchType().name().toLowerCase(Locale.ROOT),
                hit ? evaluation.rejectionReason() : "dictionary miss",
                hit ? lookupResult.translation() : ""
        );
    }

    private static void throttledItemLocalLookupLog(
            ItemTranslateConfig config,
            String dictionaryId,
            String status,
            String originalText,
            String lookupSource,
            String lookupText,
            String match,
            String rejectionReason,
            String translation
    ) {
        String resolvedStatus = status == null || status.isBlank() ? "hit" : status;
        String resolvedDictionaryId = resolveItemLocalLookupLogDictionaryId(config, dictionaryId);
        if (!isItemLocalHitLoggingEnabled(config, resolvedDictionaryId)) {
            return;
        }

        String normalized = originalText == null ? "" : originalText.replaceAll("\\s+", " ").trim();
        String normalizedLookup = lookupText == null || lookupText.isBlank()
                ? ""
                : lookupText.replaceAll("\\s+", " ").trim();
        String logKey = resolvedDictionaryId
                + "_local_"
                + resolvedStatus
                + ':'
                + Integer.toHexString((normalized + "|" + normalizedLookup + "|" + rejectionReason).hashCode());
        long now = System.currentTimeMillis();
        Long lastAt = ITEM_LOCAL_HIT_LOG_TIMESTAMPS.get(logKey);
        if (lastAt != null && now - lastAt < ITEM_LOCAL_HIT_LOG_THROTTLE_MILLIS) {
            return;
        }

        ITEM_LOCAL_HIT_LOG_TIMESTAMPS.put(logKey, now);
        Translate_AllinOne.LOGGER.info(
                "[ItemTranslate] {}_local_{} source={} match={} original=\"{}\" lookupText=\"{}\" translation=\"{}\" reason=\"{}\"",
                resolvedDictionaryId,
                resolvedStatus,
                lookupSource == null ? "" : lookupSource,
                match == null ? "" : match,
                truncateForLog(originalText, 220),
                truncateForLog(normalizedLookup, 220),
                truncateForLog(translation, 220),
                truncateForLog(rejectionReason, 220)
        );
    }

    private static String resolveItemLocalLookupLogDictionaryId(ItemTranslateConfig config, String dictionaryId) {
        if (dictionaryId != null && !dictionaryId.isBlank()) {
            return dictionaryId;
        }
        if (config == null || config.debug == null) {
            return "item_skill";
        }
        if (config.debug.log_items_local_hits && !config.debug.log_skills_local_hits) {
            return "items";
        }
        if (config.debug.log_skills_local_hits && !config.debug.log_items_local_hits) {
            return "skills";
        }
        return "item_skill";
    }

    private static boolean isItemLocalHitLoggingEnabled(ItemTranslateConfig config, String dictionaryId) {
        if (config == null || config.debug == null) {
            return false;
        }

        String resolvedDictionaryId = dictionaryId == null ? "" : dictionaryId.trim().toLowerCase(Locale.ROOT);
        return switch (resolvedDictionaryId) {
            case "item", "items" -> config.debug.log_items_local_hits;
            case "skill", "skills" -> config.debug.log_skills_local_hits;
            case "item_skill" -> config.debug.log_items_local_hits || config.debug.log_skills_local_hits;
            default -> config.debug.log_items_local_hits || config.debug.log_skills_local_hits;
        };
    }

    private static ItemTranslateConfig currentItemTranslateConfig() {
        try {
            return Translate_AllinOne.getConfig().itemTranslate;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    static Text renderLocalDictionaryTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String translation
    ) {
        if (translation == null || translation.isBlank()) {
            return Text.empty();
        }

        if (containsLegacyFormattingCode(translation)) {
            Text legacyRendered = renderLegacyFormattedLocalDictionaryTranslation(preparedTemplate, translation);
            if (legacyRendered != null) {
                return legacyRendered;
            }
            return StylePreserver.fromLegacyText(translation);
        }

        Style inheritedStyle = StylePreserver.sanitizeStyleForComparison(
                resolveLeadingVisibleStyle(renderOriginalPreparedLine(preparedTemplate)),
                true
        );
        return Text.literal(translation).setStyle(inheritedStyle);
    }

    private static Text renderLegacyFormattedLocalDictionaryTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String translation
    ) {
        Text parsedLegacy = StylePreserver.fromLegacyText(translation);
        if (parsedLegacy == null) {
            return null;
        }

        Style decorativeSourceStyle = resolveDecorativeGlyphSourceStyle(preparedTemplate);
        if (!hasCustomFont(decorativeSourceStyle)) {
            return normalizeDecorativePassthroughText(parsedLegacy);
        }

        MutableText rebuilt = Text.empty();
        parsedLegacy.visit((style, string) -> {
            appendLegacyLocalDictionarySegment(rebuilt, string, style, decorativeSourceStyle);
            return Optional.empty();
        }, Style.EMPTY);
        return rebuilt;
    }

    private static boolean containsLegacyFormattingCode(String value) {
        return value != null && value.indexOf('§') >= 0;
    }

    private static Style resolveLeadingVisibleStyle(Text text) {
        if (text == null) {
            return Style.EMPTY;
        }

        final Style[] resolved = {Style.EMPTY};
        text.visit((style, string) -> {
            if (string == null || string.isBlank()) {
                return Optional.empty();
            }
            resolved[0] = style == null ? Style.EMPTY : style;
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return resolved[0] == null ? Style.EMPTY : resolved[0];
    }

    private static Style resolveDecorativeGlyphSourceStyle(PreparedTooltipTemplate preparedTemplate) {
        if (preparedTemplate == null) {
            return Style.EMPTY;
        }

        Text sourceLine = preparedTemplate.sourceLine();
        if (sourceLine != null) {
            final Style[] sourceResolved = {Style.EMPTY};
            sourceLine.visit((style, string) -> {
                if (hasCustomFont(style)
                        && string != null
                        && containsDecorativeGlyph(string)) {
                    sourceResolved[0] = style;
                    return Optional.of(Boolean.TRUE);
                }
                return Optional.empty();
            }, Style.EMPTY);
            if (hasCustomFont(sourceResolved[0])) {
                return sourceResolved[0];
            }
        }

        if (preparedTemplate.styleResult() != null && preparedTemplate.styleResult().styleMap != null) {
            for (Style style : preparedTemplate.styleResult().styleMap.values()) {
                if (hasCustomFont(style)) {
                    return style;
                }
            }
        }

        Text originalText = renderOriginalPreparedLine(preparedTemplate);
        if (originalText == null) {
            return Style.EMPTY;
        }

        final Style[] resolved = {Style.EMPTY};
        originalText.visit((style, string) -> {
            if (hasCustomFont(style)
                    && string != null
                    && containsDecorativeGlyph(string)) {
                resolved[0] = style;
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (hasCustomFont(resolved[0])) {
            return resolved[0];
        }

        return sourceLine != null && containsDecorativeGlyph(sourceLine.getString())
                ? Style.EMPTY.withFont(WYNNCRAFT_TOOLTIP_FONT)
                : Style.EMPTY;
    }

    private static void appendLegacyLocalDictionarySegment(
            MutableText target,
            String content,
            Style legacyStyle,
            Style decorativeSourceStyle
    ) {
        if (target == null || content == null || content.isEmpty()) {
            return;
        }

        String normalized = TemplateProcessor.collapseWynnInlineSpacerGlyphs(content);
        if (normalized == null || normalized.isEmpty()) {
            return;
        }

        Style readableStyle = StylePreserver.sanitizeStyleForComparison(legacyStyle, true);
        Style decorativeStyle = readableStyle.withFont(decorativeSourceStyle.getFont());
        StringBuilder run = new StringBuilder();
        Boolean decorativeRun = null;

        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            boolean decorativeGlyph = isDecorativeGlyphCodePoint(codePoint);

            if (decorativeRun != null && decorativeRun != decorativeGlyph && run.length() > 0) {
                target.append(Text.literal(run.toString()).setStyle(Boolean.TRUE.equals(decorativeRun) ? decorativeStyle : readableStyle));
                run.setLength(0);
            }

            decorativeRun = decorativeGlyph;
            run.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }

        if (run.length() > 0) {
            target.append(Text.literal(run.toString()).setStyle(Boolean.TRUE.equals(decorativeRun) ? decorativeStyle : readableStyle));
        }
    }

    private static List<CompatibilityTemplateKey> collectCompatibilityKeys(PreparedTooltipTemplate preparedTemplate) {
        if (!preparedTemplate.useTagStylePreservation()) {
            return List.of();
        }

        List<CompatibilityTemplateKey> compatibilityKeys = new ArrayList<>(2);
        addCompatibilityKey(
                compatibilityKeys,
                TemplateProcessor.normalizeWynnInlineSpacerGlyphsInTaggedText(
                        TemplateProcessor.extractDecorativeGlyphTags(preparedTemplate.unicodeTemplate()).template()
                ),
                CachedTranslationFormat.TAGGED
        );
        addCompatibilityKey(
                compatibilityKeys,
                buildLegacyCompatibilityKey(preparedTemplate.sourceLine()),
                CachedTranslationFormat.LEGACY
        );
        return compatibilityKeys;
    }

    private static void addCompatibilityKey(
            List<CompatibilityTemplateKey> compatibilityKeys,
            String key,
            CachedTranslationFormat format
    ) {
        if (key == null || key.isBlank()) {
            return;
        }

        for (CompatibilityTemplateKey existing : compatibilityKeys) {
            if (existing.key().equals(key)) {
                return;
            }
        }

        compatibilityKeys.add(new CompatibilityTemplateKey(key, format));
    }

    private static AdaptedCachedTranslation adaptCachedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        if (cachedTranslation == null || cachedTranslation.isBlank()) {
            return null;
        }

        if (format == CachedTranslationFormat.LEGACY && preparedTemplate.useTagStylePreservation()) {
            String converted = StylePreserver.convertLegacyTranslationToTaggedTemplate(
                    cachedTranslation,
                    preparedTemplate.styleResult().styleMap
            );
            if (converted == null || converted.isBlank()) {
                return null;
            }
            return new AdaptedCachedTranslation(converted, CachedTranslationFormat.TAGGED);
        }

        return new AdaptedCachedTranslation(cachedTranslation, format);
    }

    private static DecodedStoredTranslation decodeStoredTranslation(String storedTranslation, CachedTranslationFormat defaultFormat) {
        if (storedTranslation == null) {
            return new DecodedStoredTranslation("", defaultFormat);
        }

        if (storedTranslation.startsWith(STORED_LEGACY_PREFIX)) {
            return new DecodedStoredTranslation(
                    storedTranslation.substring(STORED_LEGACY_PREFIX.length()),
                    CachedTranslationFormat.LEGACY
            );
        }

        return new DecodedStoredTranslation(storedTranslation, defaultFormat);
    }

    private static String encodeStoredTranslation(String translation, CachedTranslationFormat format) {
        if (translation == null || translation.isBlank()) {
            return translation;
        }

        if (format == CachedTranslationFormat.LEGACY && !translation.startsWith(STORED_LEGACY_PREFIX)) {
            return STORED_LEGACY_PREFIX + translation;
        }

        return translation;
    }

    private static Text renderCompatibilityText(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        if (cachedTranslation == null || cachedTranslation.isBlank()) {
            return null;
        }

        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(cachedTranslation, preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values(),
                true
        );
        return format == CachedTranslationFormat.TAGGED
                ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, preparedTemplate.styleResult().styleMap, true)
                : StylePreserver.fromLegacyText(reassembledTranslated);
    }

    private static boolean isSafeAdaptedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            AdaptedCachedTranslation adaptedTranslation,
            Text compatibilityRenderedText
    ) {
        if (adaptedTranslation == null
                || adaptedTranslation.format() != CachedTranslationFormat.TAGGED
                || adaptedTranslation.translation() == null
                || adaptedTranslation.translation().isBlank()) {
            return false;
        }

        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(adaptedTranslation.translation(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values(),
                true
        );
        if (containsNumericPlaceholder(reassembledTranslated)) {
            return false;
        }

        Text adaptedRenderedText = StylePreserver.reapplyStylesFromTags(
                reassembledTranslated,
                preparedTemplate.styleResult().styleMap,
                true
        );
        if (adaptedRenderedText == null) {
            return false;
        }
        if (compatibilityRenderedText == null) {
            return true;
        }
        return adaptedRenderedText.getString().equals(compatibilityRenderedText.getString());
    }

    private static boolean isUsableCachedTranslation(
            PreparedTooltipTemplate preparedTemplate,
            String cachedTranslation,
            CachedTranslationFormat format
    ) {
        Text renderedText = renderCompatibilityText(preparedTemplate, cachedTranslation, format);
        return renderedText != null && !containsNumericPlaceholder(renderedText.getString());
    }

    private static void logCacheMigrationIfDev(
            ItemTranslateConfig config,
            String phase,
            String newKey,
            String compatibilityKey,
            CachedTranslationFormat compatibilityFormat,
            boolean promoted,
            String detail
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemCacheMigration(config)) {
            return;
        }

        int suppressedCount = acquireCacheMigrationLogSlot(phase, compatibilityFormat, newKey, compatibilityKey);
        if (suppressedCount < 0) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-migration] phase={} promoted={} format={} repeatsSuppressed={} newKey=\"{}\" compatibilityKey=\"{}\" detail=\"{}\"",
                phase,
                promoted,
                compatibilityFormat == null ? "" : compatibilityFormat.name(),
                suppressedCount,
                truncateForLog(newKey, 220),
                truncateForLog(compatibilityKey, 220),
                truncateForLog(detail, 220)
        );
    }

    private static void logCacheMigrationTimingIfDev(
            ItemTranslateConfig config,
            String phase,
            String newKey,
            String compatibilityKey,
            CachedTranslationFormat compatibilityFormat,
            long collectCompatibilityKeysElapsedNanos,
            long compatibilityScanElapsedNanos,
            long promoteElapsedNanos,
            long totalElapsedNanos,
            String detail
    ) {
        if (!TooltipTextMatcherSupport.shouldLogItemCacheMigration(config)) {
            return;
        }

        LOGGER.info(
                "[ItemDev:cache-hotspot] phase={} thread=\"{}\" renderThread={} format={} totalMs={} collectKeysMs={} scanMs={} promoteMs={} newKey=\"{}\" compatibilityKey=\"{}\" detail=\"{}\"",
                phase,
                Thread.currentThread().getName(),
                Thread.currentThread().getName().contains("Render thread"),
                compatibilityFormat == null ? "" : compatibilityFormat.name(),
                formatDevDurationMillis(totalElapsedNanos),
                formatDevDurationMillis(collectCompatibilityKeysElapsedNanos),
                formatDevDurationMillis(compatibilityScanElapsedNanos),
                formatDevDurationMillis(promoteElapsedNanos),
                truncateForLog(newKey, 220),
                truncateForLog(compatibilityKey, 220),
                truncateForLog(detail, 220)
        );
    }

    static String buildLegacyCompatibilityKey(Text line) {
        if (line == null) {
            return null;
        }

        StylePreserver.ExtractionResult legacyStyleResult = StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult legacyTemplateResult = TemplateProcessor.extract(legacyStyleResult.markedText);
        return StylePreserver.toLegacyTemplate(legacyTemplateResult.template(), legacyStyleResult.styleMap);
    }

    private static boolean shouldUseTagStylePreservation(Text line, boolean useTagStylePreservation) {
        return useTagStylePreservation || requiresRichStylePreservation(line);
    }

    private static boolean requiresRichStylePreservation(Text line) {
        if (line == null) {
            return false;
        }

        for (FlatNode node : FlatNode.flatten(line)) {
            if (hasCustomFont(node.style())) {
                return true;
            }

            String extracted = node.extractString();
            if (containsDecorativeGlyph(extracted)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasCustomFontOrDecorativeGlyph(Text line) {
        return requiresRichStylePreservation(line);
    }

    private static boolean hasCustomFont(Style style) {
        return style != null
                && style.getFont() != null
                && !StyleSpriteSource.DEFAULT.equals(style.getFont());
    }

    static boolean hasUnsafeMixedDecorativeLiteral(Text line) {
        if (line == null) {
            return false;
        }
        for (FlatNode node : FlatNode.flatten(line)) {
            String text = node.extractString();
            if (text == null || text.isEmpty() || !containsDecorativeGlyph(text)) {
                continue;
            }
            if (text.codePoints().anyMatch(Character::isLetter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        return unicodeType == Character.PRIVATE_USE
                || unicodeType == Character.UNASSIGNED
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    private static String remapParagraphTemplateIds(
            String template,
            int styleOffset,
            int numericOffset,
            int glyphOffset
    ) {
        String remapped = remapPatternIds(template, STYLE_TAG_ID_PATTERN, "s", styleOffset, true);
        remapped = remapPatternIds(remapped, NUMERIC_PLACEHOLDER_ID_PATTERN, "d", numericOffset, false);
        return remapPatternIds(remapped, GLYPH_PLACEHOLDER_ID_PATTERN, "g", glyphOffset, false);
    }

    private static void appendParagraphSemanticBoundary(StringBuilder combined, String nextTemplate) {
        if (combined == null || combined.isEmpty() || nextTemplate == null || nextTemplate.isEmpty()) {
            return;
        }
        String previousVisible = STYLE_TAG_ID_PATTERN.matcher(combined).replaceAll("");
        String nextVisible = STYLE_TAG_ID_PATTERN.matcher(nextTemplate).replaceAll("");
        if (previousVisible.isEmpty() || nextVisible.isEmpty()) {
            return;
        }
        int previousCodePoint = previousVisible.codePointBefore(previousVisible.length());
        int nextCodePoint = nextVisible.codePointAt(0);
        if (Character.isWhitespace(previousCodePoint)
                || Character.isWhitespace(nextCodePoint)
                || doesNotNeedSemanticBoundaryAfter(previousCodePoint)
                || doesNotNeedSemanticBoundaryBefore(nextCodePoint)
                || isCjkCodePoint(previousCodePoint)
                || isCjkCodePoint(nextCodePoint)) {
            return;
        }
        combined.append(' ');
    }

    private static boolean doesNotNeedSemanticBoundaryAfter(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.START_PUNCTUATION
                || codePoint == '/'
                || codePoint == '-'
                || codePoint == '+';
    }

    private static boolean doesNotNeedSemanticBoundaryBefore(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.END_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || codePoint == '%'
                || codePoint == ':'
                || codePoint == ';';
    }

    private static boolean isCjkCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static String canonicalizeParagraphStyleIds(String template, Map<Integer, Style> styleMap) {
        if (template == null || template.isBlank() || styleMap == null || styleMap.isEmpty()) {
            return template;
        }

        Map<Style, Integer> canonicalIdByStyle = new LinkedHashMap<>();
        Map<Integer, Integer> canonicalIdByOriginalId = new HashMap<>();
        Matcher matcher = STYLE_TAG_ID_PATTERN.matcher(template);
        while (matcher.find()) {
            int originalId = Integer.parseInt(matcher.group(1));
            Style style = styleMap.get(originalId);
            if (style == null) {
                canonicalIdByOriginalId.putIfAbsent(originalId, originalId);
                continue;
            }
            int canonicalId = canonicalIdByStyle.computeIfAbsent(style, ignored -> originalId);
            canonicalIdByOriginalId.putIfAbsent(originalId, canonicalId);
        }

        matcher.reset();
        StringBuilder canonical = new StringBuilder(template.length());
        while (matcher.find()) {
            int originalId = Integer.parseInt(matcher.group(1));
            int canonicalId = canonicalIdByOriginalId.getOrDefault(originalId, originalId);
            boolean closingTag = template.charAt(matcher.start() + 1) == '/';
            String replacement = closingTag
                    ? "</s" + canonicalId + ">"
                    : "<s" + canonicalId + ">";
            matcher.appendReplacement(canonical, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(canonical);
        return canonical.toString();
    }

    private static String canonicalizeParagraphGlyphStyles(
            String glyphValue,
            String canonicalTemplate,
            Map<Integer, Style> styleMap
    ) {
        if (glyphValue == null || glyphValue.isEmpty() || styleMap == null || styleMap.isEmpty()) {
            return glyphValue;
        }
        Map<Style, Integer> canonicalIdByStyle = new LinkedHashMap<>();
        Matcher templateMatcher = STYLE_TAG_ID_PATTERN.matcher(canonicalTemplate == null ? "" : canonicalTemplate);
        while (templateMatcher.find()) {
            int styleId = Integer.parseInt(templateMatcher.group(1));
            Style style = styleMap.get(styleId);
            if (style != null) {
                canonicalIdByStyle.putIfAbsent(style, styleId);
            }
        }
        Matcher glyphMatcher = STYLE_TAG_ID_PATTERN.matcher(glyphValue);
        StringBuilder canonical = new StringBuilder(glyphValue.length());
        while (glyphMatcher.find()) {
            int originalId = Integer.parseInt(glyphMatcher.group(1));
            Style style = styleMap.get(originalId);
            int canonicalId = style == null
                    ? originalId
                    : canonicalIdByStyle.getOrDefault(style, originalId);
            boolean closing = glyphValue.charAt(glyphMatcher.start() + 1) == '/';
            String replacement = closing ? "</s" + canonicalId + ">" : "<s" + canonicalId + ">";
            glyphMatcher.appendReplacement(canonical, Matcher.quoteReplacement(replacement));
        }
        glyphMatcher.appendTail(canonical);
        return canonical.toString();
    }

    private static String remapPatternIds(
            String input,
            Pattern pattern,
            String prefix,
            int offset,
            boolean styleTag
    ) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            int currentId = Integer.parseInt(matcher.group(1));
            int remappedId = currentId + offset;
            String replacement;
            if (styleTag) {
                boolean closingTag = input.charAt(matcher.start() + 1) == '/';
                replacement = closingTag
                        ? "</" + prefix + remappedId + ">"
                        : "<" + prefix + remappedId + ">";
            } else {
                replacement = "{" + prefix + remappedId + "}";
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static int countStyleIds(Map<Integer, Style> styleMap) {
        if (styleMap == null || styleMap.isEmpty()) {
            return 0;
        }

        int maxStyleId = -1;
        for (Integer styleId : styleMap.keySet()) {
            if (styleId != null && styleId > maxStyleId) {
                maxStyleId = styleId;
            }
        }
        return maxStyleId + 1;
    }

    private static int computeParagraphWrapWidth(List<PreparedTooltipTemplate> preparedLines) {
        TextRenderer textRenderer = getTooltipTextRenderer();
        if (textRenderer == null || preparedLines == null || preparedLines.isEmpty()) {
            return -1;
        }

        int maxWidth = 0;
        for (PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(preparedLine.sourceLine()));
        }
        return maxWidth;
    }

    private static TextRenderer getTooltipTextRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.textRenderer;
    }

    static boolean shouldBypassCompatibilityFallback(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return false;
        }

        Long expiresAtMillis = FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.get(translationTemplateKey);
        if (expiresAtMillis == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (expiresAtMillis <= now) {
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(translationTemplateKey, expiresAtMillis);
            return false;
        }
        return true;
    }

    static void clearForceRefreshCompatBypass(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return;
        }
        FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(translationTemplateKey);
    }

    private static void cleanupForceRefreshCompatBypassState() {
        if (FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (var entry : FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.entrySet()) {
            Long expiresAtMillis = entry.getValue();
            if (expiresAtMillis == null || expiresAtMillis <= now) {
                FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.remove(entry.getKey(), expiresAtMillis);
            }
        }

        if (FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.size() > FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT) {
            FORCE_REFRESH_COMPAT_BYPASS_UNTIL_BY_KEY.clear();
        }
    }

    private static String formatDevDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
    }

    private static int acquireCacheMigrationLogSlot(
            String phase,
            CachedTranslationFormat compatibilityFormat,
            String newKey,
            String compatibilityKey
    ) {
        if ("promote".equals(phase)) {
            return 0;
        }

        if (CACHE_MIGRATION_LOG_THROTTLE.size() > CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT) {
            CACHE_MIGRATION_LOG_THROTTLE.clear();
        }

        CacheMigrationLogKey logKey = new CacheMigrationLogKey(phase, compatibilityFormat, newKey, compatibilityKey);
        CacheMigrationLogThrottleState state = CACHE_MIGRATION_LOG_THROTTLE.computeIfAbsent(
                logKey,
                unused -> new CacheMigrationLogThrottleState()
        );
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (state.lastLoggedAtMillis > 0
                    && now - state.lastLoggedAtMillis < CACHE_MIGRATION_LOG_THROTTLE_WINDOW_MILLIS) {
                state.suppressedCount++;
                return -1;
            }

            int suppressedCount = state.suppressedCount;
            state.suppressedCount = 0;
            state.lastLoggedAtMillis = now;
            return suppressedCount;
        }
    }

    static String truncateForLog(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
