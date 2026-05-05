package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipRoutePlanner.TooltipParagraphBlock;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedParagraphTemplate;
import com.cedarxuesong.translate_allinone.utils.translate.TooltipTemplateRuntime.PreparedTooltipTemplate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TooltipParagraphSupport {
    private static final Pattern ENGLISH_CONNECTOR_PATTERN =
            Pattern.compile("(?i)\\b(by|to|and|of|for|in|on|from|with|the|a|an)\\b");
    private static final Pattern CHINESE_BLOCK_DISTANCE_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)个?方块");
    private static final int MIN_PARAGRAPH_BODY_STYLE_DOMINANT_SCORE = 5;
    private static final int MIN_PARAGRAPH_BODY_RUN_SCORE = 1;
    private static final int MIN_PARAGRAPH_BODY_STYLE_DOMINANCE_PERCENT = 55;
    private static final int MAX_NON_WYNN_MISSING_STYLE_SCORE = 8;
    private static final int MAX_NON_WYNN_TOTAL_MISSING_STYLE_SCORE = 24;
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");

    private TooltipParagraphSupport() {
    }

    record ParagraphTranslationAttempt(
            List<TooltipTranslationSupport.TooltipLineResult> lineResults,
            boolean pending,
            boolean missingKeyIssue
    ) {
    }

    private static final class ParagraphTaggedRun {
        private Integer styleId;
        private final String content;

        private ParagraphTaggedRun(Integer styleId, String content) {
            this.styleId = styleId;
            this.content = content;
        }
    }

    static ParagraphTranslationAttempt translateParagraphBlock(
            TooltipParagraphBlock block,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        logParagraphPlanIfDev(config, emitDevLog, devSource, block);
        logParagraphStyleMapIfDev(
                config,
                emitDevLog,
                devSource,
                block,
                "source",
                block == null || block.paragraphTemplate() == null
                        ? ""
                        : block.paragraphTemplate().translationTemplateKey()
        );
        WynnSharedDictionaryService.LookupResult localLookup = lookupAcceptedLocalDictionaryTranslation(block);
        if (localLookup != null && localLookup.hit()) {
            TooltipTemplateRuntime.logAcceptedLocalDictionaryHit(
                    config,
                    localLookup,
                    buildParagraphLocalDictionaryLookupSource(block)
            );
            List<TooltipTranslationSupport.TooltipLineResult> localResults = renderTranslatedParagraphBlock(
                    block,
                    localLookup.translation(),
                    config,
                    emitDevLog,
                    devSource,
                    true
            );
            if (localResults != null) {
                return new ParagraphTranslationAttempt(localResults, false, false);
            }
        }

        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        ItemTemplateCache.LookupResult blockLookup = cache.lookupOrQueue(block.paragraphTemplate().translationTemplateKey());
        if (blockLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            List<TooltipTranslationSupport.TooltipLineResult> blockResults = renderTranslatedParagraphBlock(
                    block,
                    blockLookup.translation(),
                    config,
                    emitDevLog,
                    devSource,
                    false
            );
            if (blockResults != null) {
                return new ParagraphTranslationAttempt(blockResults, false, false);
            }
            return translateParagraphBlockLineByLine(block);
        }

        boolean pending = blockLookup.status() == ItemTemplateCache.TranslationStatus.PENDING
                || blockLookup.status() == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = blockLookup.status() == ItemTemplateCache.TranslationStatus.ERROR
                && TooltipInternalLineSupport.isMissingKeyIssue(blockLookup.errorMessage());
        if (blockLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            pending = true;
        }

        List<TooltipTranslationSupport.TooltipLineResult> fallbackResults = new ArrayList<>(block.preparedLines().size());
        for (int i = 0; i < block.preparedLines().size(); i++) {
            PreparedTooltipTemplate preparedLine = block.preparedLines().get(i);
            Text originalLine = TooltipTemplateRuntime.renderOriginalPreparedLine(preparedLine);
            if (pending) {
                originalLine = AnimationManager.getAnimatedStyledText(
                        originalLine,
                        block.paragraphTemplate().translationTemplateKey() + "#" + i,
                        false
                );
            }
            fallbackResults.add(new TooltipTranslationSupport.TooltipLineResult(originalLine, pending, missingKeyIssue));
        }
        return new ParagraphTranslationAttempt(fallbackResults, pending, missingKeyIssue);
    }

    static WynnSharedDictionaryService.LookupResult lookupAcceptedLocalDictionaryTranslation(TooltipParagraphBlock block) {
        return lookupAcceptedLocalDictionaryTranslation(
                block,
                TooltipTemplateRuntime::lookupAcceptedLocalDictionaryTranslation
        );
    }

    static boolean hasAcceptedLocalDictionaryTranslation(TooltipParagraphBlock block) {
        String sourceText = buildParagraphLocalDictionaryLookupSource(block);
        return TooltipTemplateRuntime.hasLocalDictionaryTranslation(sourceText);
    }

    static WynnSharedDictionaryService.LookupResult lookupAcceptedLocalDictionaryTranslation(
            TooltipParagraphBlock block,
            java.util.function.Function<String, WynnSharedDictionaryService.LookupResult> lookupFunction
    ) {
        if (lookupFunction == null) {
            return null;
        }

        String sourceText = buildParagraphLocalDictionaryLookupSource(block);
        if (sourceText.isBlank()) {
            return null;
        }

        WynnSharedDictionaryService.LookupResult lookupResult = lookupFunction.apply(sourceText);
        return TooltipTemplateRuntime.shouldAcceptLocalDictionaryTranslation(sourceText, lookupResult)
                ? lookupResult
                : null;
    }

    static String buildParagraphLocalDictionaryLookupSource(TooltipParagraphBlock block) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }

            String normalizedLine = TooltipTemplateRuntime.normalizeLocalDictionaryLookupSourceText(
                    preparedLine.sourceLine().getString()
            );
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(normalizedLine);
        }
        return builder.toString();
    }

    static boolean paragraphRenderDropsTooManyLines(
            int sourceLineCount,
            int renderedLineCount,
            boolean allowCompressedLineCount
    ) {
        if (sourceLineCount <= 0 || renderedLineCount < 0) {
            return false;
        }
        return renderedLineCount < minimumAcceptedRenderedLineCount(sourceLineCount, allowCompressedLineCount);
    }

    static Integer findDominantParagraphBodyStyleId(String taggedText, Map<Integer, Style> styleMap) {
        if (taggedText == null || taggedText.isBlank() || styleMap == null || styleMap.isEmpty()) {
            return null;
        }

        List<ParagraphTaggedRun> runs = parseParagraphTaggedRuns(taggedText);
        if (runs.isEmpty()) {
            return null;
        }
        canonicalizeEquivalentParagraphStyles(runs, styleMap);
        return findDominantParagraphBodyStyleId(runs, styleMap);
    }

    static boolean paragraphLooksTooShortForChineseOutput(int sourceLetterCount, int translatedSignalCount) {
        return sourceLetterCount >= 18
                && translatedSignalCount > 0
                && translatedSignalCount * 7 < sourceLetterCount * 2;
    }

    static boolean allowsRelaxedParagraphStyleCoverage(
            boolean strictParagraphStyleCoverage,
            boolean translatedHasBodyStyle,
            int sourceAccentStyleCount,
            int translatedAccentStyleCount
    ) {
        if (strictParagraphStyleCoverage || !translatedHasBodyStyle) {
            return false;
        }
        if (sourceAccentStyleCount <= 0) {
            return true;
        }
        return translatedAccentStyleCount > 0;
    }

    static boolean allowsRelaxedCompleteAccentLoss(
            boolean strictParagraphStyleCoverage,
            boolean translatedHasBodyStyle,
            int translatedAccentStyleCount,
            int maxMissingAccentScore,
            int totalMissingAccentScore
    ) {
        if (strictParagraphStyleCoverage || !translatedHasBodyStyle || translatedAccentStyleCount > 0) {
            return false;
        }
        return maxMissingAccentScore > 0
                && maxMissingAccentScore <= MAX_NON_WYNN_MISSING_STYLE_SCORE
                && totalMissingAccentScore <= MAX_NON_WYNN_TOTAL_MISSING_STYLE_SCORE;
    }

    static boolean allowsRelaxedBodyOnlyParagraphStyleFallback(
            boolean strictParagraphStyleCoverage,
            boolean chineseTargetLanguage,
            int sourceLineCount,
            boolean translatedHasBodyStyle,
            int translatedAccentStyleCount
    ) {
        return !strictParagraphStyleCoverage
                && chineseTargetLanguage
                && sourceLineCount > 1
                && translatedHasBodyStyle
                && translatedAccentStyleCount == 0;
    }

    static int countTranslatedSignalUnits(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (isMeaningfulTranslatedSignalCodePoint(codePoint)) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static ParagraphTranslationAttempt translateParagraphBlockLineByLine(TooltipParagraphBlock block) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty()) {
            return new ParagraphTranslationAttempt(List.of(), false, false);
        }

        List<TooltipTranslationSupport.TooltipLineResult> lineResults = new ArrayList<>(block.preparedLines().size());
        boolean pending = false;
        boolean missingKeyIssue = false;

        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            TooltipTranslationSupport.TooltipLineResult lineResult = TooltipTemplateRuntime.translatePreparedTemplate(preparedLine);
            if (lineResult.pending()) {
                pending = true;
            }
            if (lineResult.missingKeyIssue()) {
                missingKeyIssue = true;
            }
            lineResults.add(lineResult);
        }

        return new ParagraphTranslationAttempt(lineResults, pending, missingKeyIssue);
    }

    private static List<TooltipTranslationSupport.TooltipLineResult> renderTranslatedParagraphBlock(
            TooltipParagraphBlock block,
            String translatedBlockTemplate,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        return renderTranslatedParagraphBlock(
                block,
                translatedBlockTemplate,
                config,
                emitDevLog,
                devSource,
                false
        );
    }

    private static List<TooltipTranslationSupport.TooltipLineResult> renderTranslatedParagraphBlock(
            TooltipParagraphBlock block,
            String translatedBlockTemplate,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource,
            boolean trustedLocalDictionary
    ) {
        if (translatedBlockTemplate == null || translatedBlockTemplate.isBlank()) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-empty-provider-template",
                    true,
                    "Cached paragraph template was blank before normalization.",
                    translatedBlockTemplate,
                    "",
                    List.of()
            );
            return null;
        }

        String normalizedTemplate = normalizeParagraphTranslatedTemplate(translatedBlockTemplate);
        if (normalizedTemplate.isBlank()) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-empty-normalized-template",
                    true,
                    "Cached paragraph template became blank after whitespace normalization.",
                    normalizedTemplate,
                    "",
                    List.of()
            );
            return null;
        }

        Text renderedParagraphText = renderTranslatedParagraphText(
                block,
                normalizedTemplate,
                config,
                emitDevLog,
                devSource,
                trustedLocalDictionary
        );
        if (renderedParagraphText == null) {
            return null;
        }

        List<Text> wrappedLines = wrapParagraphText(renderedParagraphText, block.paragraphTemplate().wrapWidth());
        if (wrappedLines.isEmpty()) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-empty-wrap",
                    true,
                    "Local tooltip rewrap returned no visible lines.",
                    normalizedTemplate,
                    renderedParagraphText.getString(),
                    List.of()
            );
            return null;
        }

        List<TooltipTranslationSupport.TooltipLineResult> results = new ArrayList<>(wrappedLines.size());
        for (Text wrappedLine : wrappedLines) {
            if (wrappedLine == null) {
                continue;
            }
            results.add(new TooltipTranslationSupport.TooltipLineResult(wrappedLine, false, false));
        }
        if (results.isEmpty()) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-empty-wrap-result",
                    true,
                    "Wrapped paragraph lines were filtered out after reconstruction.",
                    normalizedTemplate,
                    renderedParagraphText.getString(),
                    wrappedLines
            );
            return null;
        }
        boolean allowCompressedLineCount = shouldAllowCompressedParagraphLineCount(config);
        if (paragraphRenderDropsTooManyLines(block.preparedLines().size(), results.size(), allowCompressedLineCount)) {
            int minimumAcceptedLines = minimumAcceptedRenderedLineCount(
                    block.preparedLines().size(),
                    allowCompressedLineCount
            );
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-line-loss",
                    true,
                    "Paragraph rewrap would compress too aggressively. sourceLines="
                            + block.preparedLines().size()
                            + ", renderedLines="
                            + results.size()
                            + ", minimumAcceptedLines="
                            + minimumAcceptedLines,
                    normalizedTemplate,
                    renderedParagraphText.getString(),
                    wrappedLines
            );
            return null;
        }

        logParagraphRenderIfDev(
                config,
                emitDevLog,
                devSource,
                block,
                "accept",
                false,
                "Rendered paragraph block successfully.",
                normalizedTemplate,
                renderedParagraphText.getString(),
                wrappedLines
        );
        return results;
    }

    private static int minimumAcceptedRenderedLineCount(int sourceLineCount, boolean allowCompressedLineCount) {
        if (sourceLineCount <= 0) {
            return 0;
        }
        if (!allowCompressedLineCount) {
            return sourceLineCount;
        }

        // CJK output often preserves meaning while wrapping into about half as many lines.
        return Math.max(1, (sourceLineCount + 1) / 2);
    }

    private static boolean shouldAllowCompressedParagraphLineCount(ItemTranslateConfig config) {
        if (config == null || config.target_language == null) {
            return false;
        }

        String language = config.target_language.toLowerCase(Locale.ROOT);
        return language.contains("chinese") || language.contains("中文") || language.startsWith("zh");
    }

    private static Text renderTranslatedParagraphText(
            TooltipParagraphBlock block,
            String normalizedTemplate,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource,
            boolean trustedLocalDictionary
    ) {
        PreparedParagraphTemplate paragraphTemplate = block.paragraphTemplate();
        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(normalizedTemplate, paragraphTemplate.templateValues()),
                paragraphTemplate.glyphValues(),
                true
        );
        reassembledTranslated = postProcessTranslatedParagraphText(
                reassembledTranslated,
                config,
                paragraphTemplate.styleMap(),
                paragraphTemplate.bodyStyleId()
        );
        logParagraphStyleMapIfDev(
                config,
                emitDevLog,
                devSource,
                block,
                "rendered",
                reassembledTranslated
        );
        if (TooltipTemplateRuntime.containsNumericPlaceholder(reassembledTranslated)) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-unresolved-placeholder",
                    true,
                    "Rendered paragraph still contains unresolved numeric placeholders.",
                    normalizedTemplate,
                    reassembledTranslated,
                    List.of()
            );
            return null;
        }

        if (trustedLocalDictionary && !containsParagraphStyleTags(reassembledTranslated)) {
            Text localRenderedText = renderTrustedPlainOrLegacyLocalParagraphText(block, reassembledTranslated);
            if (localRenderedText == null || localRenderedText.getString().isBlank()) {
                logParagraphRenderIfDev(
                        config,
                        emitDevLog,
                        devSource,
                        block,
                        "reject-empty-local-styled-text",
                        true,
                        "Local dictionary paragraph rendering produced a blank text.",
                        normalizedTemplate,
                        reassembledTranslated,
                        List.of()
                );
                return null;
            }
            return localRenderedText;
        }

        if (!trustedLocalDictionary) {
            String paragraphQualityIssue = describeParagraphQualityIssue(block, reassembledTranslated, config);
            if (paragraphQualityIssue != null) {
                logParagraphRenderIfDev(
                        config,
                        emitDevLog,
                        devSource,
                        block,
                        "reject-low-quality",
                        true,
                        paragraphQualityIssue,
                        normalizedTemplate,
                        reassembledTranslated,
                        List.of()
                );
                return null;
            }
        }

        Text renderedText = StylePreserver.reapplyStylesFromTags(
                reassembledTranslated,
                paragraphTemplate.styleMap(),
                true
        );
        if (renderedText == null || renderedText.getString().isBlank()) {
            logParagraphRenderIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    block,
                    "reject-empty-styled-text",
                    true,
                    "Style reapplication produced a blank paragraph text.",
                    normalizedTemplate,
                    reassembledTranslated,
                    List.of()
            );
            return null;
        }
        return renderedText;
    }

    private static Text renderTrustedPlainOrLegacyLocalParagraphText(
            TooltipParagraphBlock block,
            String reassembledTranslated
    ) {
        if (reassembledTranslated == null || reassembledTranslated.isBlank()) {
            return Text.empty();
        }

        if (containsLegacyFormattingCode(reassembledTranslated)) {
            return StylePreserver.fromLegacyText(reassembledTranslated);
        }

        Style inheritedStyle = resolveParagraphBodyVisualStyle(block);
        return Text.literal(reassembledTranslated).setStyle(inheritedStyle == null ? Style.EMPTY : inheritedStyle);
    }

    private static boolean containsLegacyFormattingCode(String text) {
        return text != null && text.indexOf('§') >= 0;
    }

    private static boolean containsParagraphStyleTags(String text) {
        return text != null && TooltipTemplateRuntime.STYLE_TAG_ID_PATTERN.matcher(text).find();
    }

    private static String postProcessTranslatedParagraphText(
            String translatedText,
            ItemTranslateConfig config,
            Map<Integer, Style> styleMap,
            Integer bodyStyleId
    ) {
        if (translatedText == null || translatedText.isBlank()) {
            return translatedText;
        }

        boolean applyChineseHeuristics = shouldApplyChineseParagraphQualityHeuristics(config);
        String normalized = translatedText;
        if (applyChineseHeuristics) {
            normalized = normalizeChineseTaggedWhitespace(normalized);
        }
        normalized = normalizeParagraphTaggedStyles(normalized, styleMap, bodyStyleId);
        if (applyChineseHeuristics) {
            normalized = normalizeChineseMeasurementUnits(normalized);
            normalized = normalizeChineseNumericHighlightRuns(normalized, styleMap, bodyStyleId);
            normalized = normalizeChineseTaggedWhitespace(normalized);
        }
        return normalized;
    }

    static String normalizeTranslatedParagraphTextForTest(
            String translatedText,
            ItemTranslateConfig config,
            Map<Integer, Style> styleMap,
            Integer bodyStyleId
    ) {
        return postProcessTranslatedParagraphText(translatedText, config, styleMap, bodyStyleId);
    }

    static List<TooltipTranslationSupport.TooltipLineResult> renderTrustedLocalDictionaryParagraphBlockForTest(
            TooltipParagraphBlock block,
            String translatedBlockTemplate,
            ItemTranslateConfig config
    ) {
        return renderTranslatedParagraphBlock(
                block,
                translatedBlockTemplate,
                config,
                false,
                "test",
                true
        );
    }

    private static String normalizeParagraphTranslatedTemplate(String translatedBlockTemplate) {
        if (translatedBlockTemplate == null || translatedBlockTemplate.isBlank()) {
            return "";
        }

        String normalized = translatedBlockTemplate
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return normalized.replaceAll("\\s{2,}", " ");
    }

    private static String normalizeChineseTaggedWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder normalized = new StringBuilder(text.length());
        int lastVisibleCodePoint = -1;
        for (int index = 0; index < text.length(); ) {
            int styleTagEnd = findStyleTagEnd(text, index);
            if (styleTagEnd >= index) {
                normalized.append(text, index, styleTagEnd + 1);
                index = styleTagEnd + 1;
                continue;
            }

            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                int nextIndex = index + charCount;
                int peekIndex = nextIndex;
                while (peekIndex < text.length()) {
                    int nextStyleTagEnd = findStyleTagEnd(text, peekIndex);
                    if (nextStyleTagEnd >= peekIndex) {
                        peekIndex = nextStyleTagEnd + 1;
                        continue;
                    }

                    int nextCodePoint = text.codePointAt(peekIndex);
                    if (!Character.isWhitespace(nextCodePoint)) {
                        break;
                    }
                    peekIndex += Character.charCount(nextCodePoint);
                }

                int nextVisibleCodePoint = findNextVisibleCodePoint(text, peekIndex);
                if (!shouldStripChineseWhitespace(lastVisibleCodePoint, nextVisibleCodePoint)
                        && normalized.length() > 0
                        && normalized.charAt(normalized.length() - 1) != ' ') {
                    normalized.append(' ');
                }
                index = nextIndex;
                continue;
            }

            normalized.appendCodePoint(codePoint);
            lastVisibleCodePoint = codePoint;
            index += charCount;
        }

        return normalized.toString().trim();
    }

    private static String normalizeParagraphTaggedStyles(String text, Map<Integer, Style> styleMap, Integer bodyStyleId) {
        if (text == null
                || text.isBlank()
                || styleMap == null
                || styleMap.isEmpty()
                || text.indexOf('<') < 0) {
            return text;
        }

        List<ParagraphTaggedRun> runs = parseParagraphTaggedRuns(text);
        if (runs.size() <= 1) {
            return text;
        }

        if (TooltipTranslationContext.shouldRequireStrictParagraphStyleCoverage()) {
            canonicalizeEquivalentParagraphStyles(runs, styleMap);
        }
        absorbParagraphBodyStyle(runs, styleMap, bodyStyleId);
        return serializeParagraphTaggedRuns(runs);
    }

    private static String normalizeChineseNumericHighlightRuns(
            String text,
            Map<Integer, Style> styleMap,
            Integer preferredBodyStyleId
    ) {
        if (text == null
                || text.isBlank()
                || styleMap == null
                || styleMap.isEmpty()
                || text.indexOf('<') < 0) {
            return text;
        }

        List<ParagraphTaggedRun> runs = parseParagraphTaggedRuns(text);
        if (runs.isEmpty()) {
            return text;
        }

        Integer bodyStyleId = preferredBodyStyleId == null
                ? findDominantParagraphBodyStyleId(runs, styleMap)
                : findCanonicalParagraphStyleId(preferredBodyStyleId, runs, styleMap);
        if (bodyStyleId == null) {
            return text;
        }

        List<ParagraphTaggedRun> normalizedRuns = new ArrayList<>(runs.size());
        for (ParagraphTaggedRun run : runs) {
            if (!shouldSplitChineseNumericHighlightRun(run, bodyStyleId)) {
                addParagraphTaggedRun(normalizedRuns, run == null ? null : run.styleId, run == null ? null : run.content);
                continue;
            }

            int digitIndex = findFirstDigitCharIndex(run.content);
            int coreEndIndex = findChineseNumericHighlightCoreEnd(run.content, digitIndex);
            if (digitIndex < 0 || coreEndIndex <= digitIndex) {
                addParagraphTaggedRun(normalizedRuns, run.styleId, run.content);
                continue;
            }

            String prefix = run.content.substring(0, digitIndex);
            String core = run.content.substring(digitIndex, coreEndIndex);
            String suffix = run.content.substring(coreEndIndex);

            boolean prefixLooksLikeBodyText = containsLetterLikeText(prefix);
            boolean suffixLooksLikeBodyText = containsLetterLikeText(suffix);
            if (!prefixLooksLikeBodyText && !suffixLooksLikeBodyText) {
                addParagraphTaggedRun(normalizedRuns, run.styleId, run.content);
                continue;
            }

            if (prefixLooksLikeBodyText) {
                addParagraphTaggedRun(normalizedRuns, bodyStyleId, prefix);
            } else if (!prefix.isEmpty()) {
                core = prefix + core;
            }

            addParagraphTaggedRun(normalizedRuns, run.styleId, core);

            if (suffixLooksLikeBodyText) {
                addParagraphTaggedRun(normalizedRuns, bodyStyleId, suffix);
            } else if (!suffix.isEmpty()) {
                addParagraphTaggedRun(normalizedRuns, run.styleId, suffix);
            }
        }

        return serializeParagraphTaggedRuns(normalizedRuns);
    }

    private static String normalizeChineseMeasurementUnits(String text) {
        if (text == null || text.isBlank() || text.indexOf('<') < 0) {
            return text;
        }

        List<ParagraphTaggedRun> runs = parseParagraphTaggedRuns(text);
        if (runs.isEmpty()) {
            return text;
        }

        for (int index = 0; index < runs.size(); index++) {
            ParagraphTaggedRun run = runs.get(index);
            if (run == null || run.content == null || run.content.isBlank()) {
                continue;
            }

            String normalizedContent = CHINESE_BLOCK_DISTANCE_PATTERN.matcher(run.content).replaceAll("$1格");
            if (!Objects.equals(normalizedContent, run.content)) {
                runs.set(index, new ParagraphTaggedRun(run.styleId, normalizedContent));
            }
        }
        return serializeParagraphTaggedRuns(runs);
    }

    private static List<ParagraphTaggedRun> parseParagraphTaggedRuns(String text) {
        List<ParagraphTaggedRun> runs = new ArrayList<>();
        Matcher matcher = TooltipTemplateRuntime.STYLE_TAG_ID_PATTERN.matcher(text);
        int lastEnd = 0;
        Integer activeStyleId = null;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                addParagraphTaggedRun(runs, activeStyleId, text.substring(lastEnd, matcher.start()));
            }

            int styleId = Integer.parseInt(matcher.group(1));
            boolean closingTag = text.charAt(matcher.start() + 1) == '/';
            activeStyleId = closingTag ? null : styleId;
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            addParagraphTaggedRun(runs, activeStyleId, text.substring(lastEnd));
        }
        return runs;
    }

    private static void addParagraphTaggedRun(List<ParagraphTaggedRun> runs, Integer styleId, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        if (!runs.isEmpty()) {
            int lastIndex = runs.size() - 1;
            ParagraphTaggedRun previous = runs.get(lastIndex);
            if (Objects.equals(previous.styleId, styleId)) {
                runs.set(lastIndex, new ParagraphTaggedRun(styleId, previous.content + content));
                return;
            }
        }

        runs.add(new ParagraphTaggedRun(styleId, content));
    }

    private static boolean shouldSplitChineseNumericHighlightRun(ParagraphTaggedRun run, Integer bodyStyleId) {
        if (run == null
                || run.styleId == null
                || Objects.equals(run.styleId, bodyStyleId)
                || run.content == null
                || run.content.isBlank()
                || !containsDigit(run.content)
                || TooltipTemplateRuntime.containsDecorativeGlyph(run.content)
                || containsNonPunctuationSymbolCodePoint(run.content)
                || !containsCjkText(run.content)) {
            return false;
        }

        int digitIndex = findFirstDigitCharIndex(run.content);
        if (digitIndex < 0) {
            return false;
        }

        int coreEndIndex = findChineseNumericHighlightCoreEnd(run.content, digitIndex);
        if (coreEndIndex <= digitIndex) {
            return false;
        }

        String prefix = run.content.substring(0, digitIndex);
        String suffix = run.content.substring(coreEndIndex);
        return containsLetterLikeText(prefix) || containsLetterLikeText(suffix);
    }

    private static int findFirstDigitCharIndex(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (Character.isDigit(codePoint)) {
                return index;
            }
            index += Character.charCount(codePoint);
        }
        return -1;
    }

    private static int findChineseNumericHighlightCoreEnd(String text, int digitIndex) {
        if (text == null || text.isEmpty() || digitIndex < 0 || digitIndex >= text.length()) {
            return digitIndex;
        }

        boolean consumedCjkUnit = false;
        int index = digitIndex;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (Character.isDigit(codePoint)
                    || Character.isWhitespace(codePoint)
                    || isAsciiLetter(codePoint)
                    || isNumericHighlightPunctuation(codePoint)) {
                index += Character.charCount(codePoint);
                continue;
            }
            if (!consumedCjkUnit && isChineseNumericHighlightUnit(codePoint)) {
                consumedCjkUnit = true;
                index += Character.charCount(codePoint);
                continue;
            }
            break;
        }
        return index;
    }

    private static boolean isAsciiLetter(int codePoint) {
        return codePoint <= 0x7F && Character.isLetter(codePoint);
    }

    private static boolean isNumericHighlightPunctuation(int codePoint) {
        return codePoint == '+'
                || codePoint == '-'
                || codePoint == '.'
                || codePoint == ','
                || codePoint == '%'
                || codePoint == '/'
                || codePoint == ':'
                || codePoint == '：'
                || codePoint == '('
                || codePoint == ')'
                || codePoint == '（'
                || codePoint == '）'
                || codePoint == '['
                || codePoint == ']';
    }

    private static boolean isChineseNumericHighlightUnit(int codePoint) {
        return codePoint == '格'
                || codePoint == '秒'
                || codePoint == '级'
                || codePoint == '层'
                || codePoint == '次'
                || codePoint == '倍'
                || codePoint == '点'
                || codePoint == '米'
                || codePoint == '天'
                || codePoint == '周'
                || codePoint == '月'
                || codePoint == '年';
    }

    private static boolean containsLetterLikeText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (Character.isLetter(codePoint) || isCjkCodePoint(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsCjkText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (isCjkCodePoint(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsNonPunctuationSymbolCodePoint(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            int type = Character.getType(codePoint);
            if (type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL
                    || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static void canonicalizeEquivalentParagraphStyles(
            List<ParagraphTaggedRun> runs,
            Map<Integer, Style> styleMap
    ) {
        Map<Style, Integer> canonicalStyleIdsByVisualStyle = new HashMap<>();
        for (ParagraphTaggedRun run : runs) {
            if (run == null || run.styleId == null) {
                continue;
            }

            Style visualStyle = StylePreserver.sanitizeStyleForComparison(
                    styleMap.getOrDefault(run.styleId, Style.EMPTY),
                    true
            );
            Integer canonicalStyleId = canonicalStyleIdsByVisualStyle.putIfAbsent(visualStyle, run.styleId);
            if (canonicalStyleId != null) {
                run.styleId = canonicalStyleId;
            }
        }
    }

    private static void absorbParagraphBodyStyle(
            List<ParagraphTaggedRun> runs,
            Map<Integer, Style> styleMap,
            Integer preferredBodyStyleId
    ) {
        Integer dominantStyleId = preferredBodyStyleId == null
                ? findDominantParagraphBodyStyleId(runs, styleMap)
                : findCanonicalParagraphStyleId(preferredBodyStyleId, runs, styleMap);
        if (dominantStyleId == null) {
            return;
        }

        for (int index = 0; index < runs.size(); index++) {
            ParagraphTaggedRun run = runs.get(index);
            if (run == null || run.styleId != null) {
                continue;
            }
            if (!shouldAbsorbPlainRunIntoParagraphBody(run)) {
                continue;
            }
            run.styleId = dominantStyleId;
        }
    }

    private static Integer findDominantParagraphBodyStyleId(List<ParagraphTaggedRun> runs, Map<Integer, Style> styleMap) {
        Map<Integer, Integer> bodyScoreByStyleId = new HashMap<>();
        int totalBodyScore = 0;
        for (ParagraphTaggedRun run : runs) {
            if (run == null || run.styleId == null) {
                continue;
            }

            int bodyScore = scoreParagraphBodyText(run.content);
            if (bodyScore <= 0) {
                continue;
            }
            bodyScoreByStyleId.merge(run.styleId, bodyScore, Integer::sum);
            totalBodyScore += bodyScore;
        }
        if (totalBodyScore <= 0 || bodyScoreByStyleId.isEmpty()) {
            return null;
        }

        int dominantStyleId = -1;
        int dominantStyleScore = 0;
        for (Map.Entry<Integer, Integer> entry : bodyScoreByStyleId.entrySet()) {
            if (entry.getValue() > dominantStyleScore) {
                dominantStyleId = entry.getKey();
                dominantStyleScore = entry.getValue();
            }
        }
        if (dominantStyleId < 0
                || dominantStyleScore < MIN_PARAGRAPH_BODY_STYLE_DOMINANT_SCORE
                || dominantStyleScore * 100 < totalBodyScore * MIN_PARAGRAPH_BODY_STYLE_DOMINANCE_PERCENT) {
            return null;
        }
        return dominantStyleId;
    }

    private static Integer findCanonicalParagraphStyleId(
            Integer preferredStyleId,
            List<ParagraphTaggedRun> runs,
            Map<Integer, Style> styleMap
    ) {
        if (preferredStyleId == null || runs == null || runs.isEmpty() || styleMap == null || styleMap.isEmpty()) {
            return preferredStyleId;
        }

        Style preferredVisualStyle = StylePreserver.sanitizeStyleForComparison(
                styleMap.getOrDefault(preferredStyleId, Style.EMPTY),
                true
        );
        for (ParagraphTaggedRun run : runs) {
            if (run == null || run.styleId == null) {
                continue;
            }
            Style runVisualStyle = StylePreserver.sanitizeStyleForComparison(
                    styleMap.getOrDefault(run.styleId, Style.EMPTY),
                    true
            );
            if (preferredVisualStyle.equals(runVisualStyle)) {
                return run.styleId;
            }
        }
        return preferredStyleId;
    }

    private static boolean shouldAbsorbPlainRunIntoParagraphBody(ParagraphTaggedRun run) {
        return run != null
                && run.styleId == null
                && scoreParagraphBodyText(run.content) >= MIN_PARAGRAPH_BODY_RUN_SCORE;
    }

    private static int scoreParagraphBodyText(String text) {
        if (text == null || text.isBlank() || containsDigit(text) || TooltipTemplateRuntime.containsDecorativeGlyph(text)) {
            return 0;
        }

        int letterCount = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (Character.isLetter(codePoint)) {
                letterCount++;
                continue;
            }
            if (!isParagraphBodyPunctuation(codePoint)) {
                return 0;
            }
        }
        return letterCount;
    }

    private static boolean isParagraphBodyPunctuation(int codePoint) {
        return codePoint == '.'
                || codePoint == ','
                || codePoint == '!'
                || codePoint == '?'
                || codePoint == ';'
                || codePoint == ':'
                || codePoint == '。'
                || codePoint == '，'
                || codePoint == '！'
                || codePoint == '？'
                || codePoint == '；'
                || codePoint == '：'
                || codePoint == '\''
                || codePoint == '"'
                || codePoint == '‘'
                || codePoint == '’'
                || codePoint == '“'
                || codePoint == '”'
                || codePoint == '-'
                || codePoint == '–'
                || codePoint == '—'
                || codePoint == '('
                || codePoint == ')'
                || codePoint == '（'
                || codePoint == '）'
                || codePoint == '['
                || codePoint == ']'
                || codePoint == '【'
                || codePoint == '】'
                || codePoint == '<'
                || codePoint == '>'
                || codePoint == '《'
                || codePoint == '》'
                || codePoint == '、';
    }

    private static String serializeParagraphTaggedRuns(List<ParagraphTaggedRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return "";
        }

        StringBuilder serialized = new StringBuilder();
        Integer activeStyleId = null;
        for (ParagraphTaggedRun run : runs) {
            if (run == null || run.content == null || run.content.isEmpty()) {
                continue;
            }

            if (!Objects.equals(activeStyleId, run.styleId)) {
                if (activeStyleId != null) {
                    serialized.append("</s").append(activeStyleId).append('>');
                }
                if (run.styleId != null) {
                    serialized.append("<s").append(run.styleId).append('>');
                }
                activeStyleId = run.styleId;
            }
            serialized.append(run.content);
        }

        if (activeStyleId != null) {
            serialized.append("</s").append(activeStyleId).append('>');
        }
        return serialized.toString();
    }

    private static int findStyleTagEnd(String text, int startIndex) {
        if (text == null
                || startIndex < 0
                || startIndex >= text.length()
                || text.charAt(startIndex) != '<') {
            return -1;
        }

        int endIndex = text.indexOf('>', startIndex);
        if (endIndex < 0) {
            return -1;
        }

        String candidate = text.substring(startIndex, endIndex + 1);
        return TooltipTemplateRuntime.STYLE_TAG_ID_PATTERN.matcher(candidate).matches() ? endIndex : -1;
    }

    private static String stripParagraphStyleTags(String text) {
        if (text == null || text.isEmpty() || text.indexOf('<') < 0) {
            return text;
        }

        StringBuilder stripped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); ) {
            int styleTagEnd = findStyleTagEnd(text, index);
            if (styleTagEnd >= index) {
                index = styleTagEnd + 1;
                continue;
            }

            int codePoint = text.codePointAt(index);
            stripped.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        return stripped.toString();
    }

    private static int findNextVisibleCodePoint(String text, int startIndex) {
        if (text == null || startIndex < 0) {
            return -1;
        }

        for (int index = startIndex; index < text.length(); ) {
            int styleTagEnd = findStyleTagEnd(text, index);
            if (styleTagEnd >= index) {
                index = styleTagEnd + 1;
                continue;
            }

            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                return codePoint;
            }
            index += Character.charCount(codePoint);
        }
        return -1;
    }

    private static boolean shouldStripChineseWhitespace(int previousCodePoint, int nextCodePoint) {
        if (previousCodePoint < 0 || nextCodePoint < 0) {
            return false;
        }

        return isChineseTightPunctuation(previousCodePoint)
                || isChineseTightPunctuation(nextCodePoint)
                || (isCjkCodePoint(previousCodePoint) && isCjkCodePoint(nextCodePoint))
                || (isCjkCodePoint(previousCodePoint) && isChineseNumericLikeCodePoint(nextCodePoint))
                || (isChineseNumericLikeCodePoint(previousCodePoint) && isCjkCodePoint(nextCodePoint));
    }

    private static boolean isChineseNumericLikeCodePoint(int codePoint) {
        return Character.isDigit(codePoint)
                || codePoint == '+'
                || codePoint == '-'
                || codePoint == '.'
                || codePoint == ','
                || codePoint == '%';
    }

    private static boolean isChineseTightPunctuation(int codePoint) {
        return codePoint == '。'
                || codePoint == '，'
                || codePoint == '、'
                || codePoint == '：'
                || codePoint == '；'
                || codePoint == '！'
                || codePoint == '？'
                || codePoint == ')'
                || codePoint == '）'
                || codePoint == ']'
                || codePoint == '】'
                || codePoint == '}'
                || codePoint == '》'
                || codePoint == '>'
                || codePoint == '＜'
                || codePoint == '《'
                || codePoint == '('
                || codePoint == '（'
                || codePoint == '['
                || codePoint == '【';
    }

    private static boolean isCjkCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static List<Text> wrapParagraphText(Text paragraphText, int wrapWidth) {
        if (paragraphText == null || paragraphText.getString().isBlank()) {
            return List.of();
        }

        TextRenderer textRenderer = getTooltipTextRenderer();
        if (textRenderer == null || wrapWidth <= 0) {
            return List.of(paragraphText);
        }

        List<OrderedText> wrappedOrderedLines = textRenderer.wrapLines(paragraphText, wrapWidth);
        if (wrappedOrderedLines == null || wrappedOrderedLines.isEmpty()) {
            return List.of(paragraphText);
        }

        List<Text> wrappedLines = new ArrayList<>(wrappedOrderedLines.size());
        for (OrderedText orderedLine : wrappedOrderedLines) {
            Text wrappedLine = orderedTextToText(orderedLine);
            if (wrappedLine != null) {
                wrappedLines.add(wrappedLine);
            }
        }
        return wrappedLines.isEmpty() ? List.of(paragraphText) : wrappedLines;
    }

    private static TextRenderer getTooltipTextRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.textRenderer;
    }

    private static Text orderedTextToText(OrderedText orderedText) {
        if (orderedText == null) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        StringBuilder currentSegment = new StringBuilder();
        Style[] currentStyle = {Style.EMPTY};

        orderedText.accept((index, style, codePoint) -> {
            if (!style.equals(currentStyle[0]) && currentSegment.length() > 0) {
                result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle[0]));
                currentSegment.setLength(0);
            }
            currentStyle[0] = style;
            currentSegment.appendCodePoint(codePoint);
            return true;
        });

        if (currentSegment.length() > 0) {
            result.append(Text.literal(currentSegment.toString()).setStyle(currentStyle[0]));
        }
        return result;
    }

    static String describeParagraphQualityIssue(
            TooltipParagraphBlock block,
            String translatedTemplate,
            ItemTranslateConfig config
    ) {
        boolean chineseTargetLanguage = shouldApplyChineseParagraphQualityHeuristics(config);
        if (!chineseTargetLanguage
                || translatedTemplate == null
                || translatedTemplate.isBlank()) {
            return null;
        }

        String visibleText = TooltipRoutePlanner.normalizeTooltipText(stripParagraphStyleTags(translatedTemplate));
        if (visibleText.isEmpty() || !containsCjk(visibleText)) {
            return null;
        }

        String connector = findStandaloneEnglishConnector(visibleText);
        if (connector != null) {
            return "Rendered paragraph still contains standalone English connector: " + connector;
        }

        String styleCoverageIssue = describeParagraphStyleCoverageIssue(block, translatedTemplate);
        if (styleCoverageIssue != null && !shouldAllowBodyOnlyParagraphStyleFallback(
                block,
                translatedTemplate,
                chineseTargetLanguage
        )) {
            return styleCoverageIssue;
        }

        int sourceLetterCount = 0;
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }
            sourceLetterCount += countAsciiLetters(preparedLine.sourceLine().getString());
        }

        int translatedCjkCount = countCjk(visibleText);
        int translatedSignalCount = countTranslatedSignalUnits(visibleText);
        if (paragraphLooksTooShortForChineseOutput(sourceLetterCount, translatedSignalCount)) {
            return "Rendered paragraph looks too short for Chinese output (sourceLetters="
                    + sourceLetterCount
                    + ", translatedSignal="
                    + translatedSignalCount
                    + ", translatedCjk="
                    + translatedCjkCount
                    + ").";
        }
        return null;
    }

    private static boolean shouldAllowBodyOnlyParagraphStyleFallback(
            TooltipParagraphBlock block,
            String translatedTemplate,
            boolean chineseTargetLanguage
    ) {
        if (block == null
                || block.preparedLines() == null
                || block.preparedLines().isEmpty()
                || block.paragraphTemplate() == null
                || translatedTemplate == null
                || translatedTemplate.isBlank()) {
            return false;
        }

        LinkedHashMap<Style, Integer> translatedScores = collectTaggedParagraphVisualStyleScores(
                translatedTemplate,
                block.paragraphTemplate().styleMap()
        );
        if (translatedScores.isEmpty()) {
            return false;
        }

        Style bodyStyle = resolveParagraphBodyVisualStyle(block);
        boolean translatedHasBodyStyle = bodyStyle == null || translatedScores.containsKey(bodyStyle);
        int translatedAccentStyleCount = countNonBodyParagraphStyles(translatedScores.keySet(), bodyStyle);
        return allowsRelaxedBodyOnlyParagraphStyleFallback(
                TooltipTranslationContext.shouldRequireStrictParagraphStyleCoverage(),
                chineseTargetLanguage,
                block.preparedLines().size(),
                translatedHasBodyStyle,
                translatedAccentStyleCount
        );
    }

    private static String describeParagraphStyleCoverageIssue(
            TooltipParagraphBlock block,
            String translatedTemplate
    ) {
        if (block == null
                || block.preparedLines() == null
                || block.preparedLines().isEmpty()
                || block.paragraphTemplate() == null) {
            return null;
        }

        LinkedHashMap<Style, Integer> sourceScores = collectSourceParagraphVisualStyleScores(block);
        if (sourceScores.isEmpty()) {
            return null;
        }

        LinkedHashMap<Style, Integer> translatedScores = collectTaggedParagraphVisualStyleScores(
                translatedTemplate,
                block.paragraphTemplate().styleMap()
        );
        List<Style> missingStyles = new ArrayList<>();
        for (Style sourceStyle : sourceScores.keySet()) {
            if (!translatedScores.containsKey(sourceStyle)) {
                missingStyles.add(sourceStyle);
            }
        }
        if (missingStyles.isEmpty()) {
            return null;
        }
        if (shouldAllowRelaxedParagraphStyleCoverage(block, sourceScores, translatedScores)) {
            return null;
        }

        return "Rendered paragraph dropped significant visual style(s). missing="
                + summarizeParagraphVisualStyles(missingStyles)
                + " source="
                + summarizeParagraphVisualStyles(sourceScores.keySet())
                + " translated="
                + summarizeParagraphVisualStyles(translatedScores.keySet());
    }

    private static boolean shouldAllowRelaxedParagraphStyleCoverage(
            TooltipParagraphBlock block,
            LinkedHashMap<Style, Integer> sourceScores,
            LinkedHashMap<Style, Integer> translatedScores
    ) {
        if (TooltipTranslationContext.shouldRequireStrictParagraphStyleCoverage()
                || sourceScores == null
                || sourceScores.isEmpty()
                || translatedScores == null
                || translatedScores.isEmpty()) {
            return false;
        }

        Style bodyStyle = resolveParagraphBodyVisualStyle(block);
        boolean translatedHasBodyStyle = bodyStyle == null || translatedScores.containsKey(bodyStyle);
        int sourceAccentStyleCount = countNonBodyParagraphStyles(sourceScores.keySet(), bodyStyle);
        int translatedAccentStyleCount = countNonBodyParagraphStyles(translatedScores.keySet(), bodyStyle);
        if (allowsRelaxedParagraphStyleCoverage(
                false,
                translatedHasBodyStyle,
                sourceAccentStyleCount,
                translatedAccentStyleCount
        )) {
            return true;
        }

        int maxMissingAccentScore = 0;
        int totalMissingAccentScore = 0;
        for (Map.Entry<Style, Integer> entry : sourceScores.entrySet()) {
            Style sourceStyle = entry.getKey();
            if (sourceStyle == null
                    || (bodyStyle != null && bodyStyle.equals(sourceStyle))
                    || translatedScores.containsKey(sourceStyle)) {
                continue;
            }

            int score = entry.getValue() == null ? 0 : entry.getValue();
            if (score <= 0) {
                continue;
            }
            totalMissingAccentScore += score;
            if (score > maxMissingAccentScore) {
                maxMissingAccentScore = score;
            }
        }
        return allowsRelaxedCompleteAccentLoss(
                false,
                translatedHasBodyStyle,
                translatedAccentStyleCount,
                maxMissingAccentScore,
                totalMissingAccentScore
        );
    }

    static boolean shouldApplyChineseParagraphQualityHeuristics(ItemTranslateConfig config) {
        if (config == null || config.target_language == null) {
            return false;
        }

        String language = config.target_language.toLowerCase(Locale.ROOT);
        return language.contains("chinese") || language.contains("中文") || language.startsWith("zh");
    }

    private static String findStandaloneEnglishConnector(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = ENGLISH_CONNECTOR_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static int countAsciiLetters(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if ((current >= 'A' && current <= 'Z') || (current >= 'a' && current <= 'z')) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsCjk(String text) {
        return countCjk(text) > 0;
    }

    private static int countCjk(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static boolean isMeaningfulTranslatedSignalCodePoint(int codePoint) {
        if (isCjkCodePoint(codePoint)
                || Character.isDigit(codePoint)
                || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')) {
            return true;
        }

        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || codePoint == '%';
    }

    private static Style resolveParagraphBodyVisualStyle(TooltipParagraphBlock block) {
        if (block == null || block.paragraphTemplate() == null || block.paragraphTemplate().bodyStyleId() == null) {
            return null;
        }

        Map<Integer, Style> styleMap = block.paragraphTemplate().styleMap();
        if (styleMap == null || styleMap.isEmpty()) {
            return null;
        }

        return StylePreserver.sanitizeStyleForComparison(
                styleMap.getOrDefault(block.paragraphTemplate().bodyStyleId(), Style.EMPTY),
                true
        );
    }

    private static int countNonBodyParagraphStyles(Iterable<Style> styles, Style bodyStyle) {
        if (styles == null) {
            return 0;
        }

        int count = 0;
        for (Style style : styles) {
            if (style == null || (bodyStyle != null && bodyStyle.equals(style))) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static LinkedHashMap<Style, Integer> collectSourceParagraphVisualStyleScores(TooltipParagraphBlock block) {
        LinkedHashMap<Style, Integer> scores = new LinkedHashMap<>();
        if (block == null || block.preparedLines() == null) {
            return scores;
        }

        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.styleResult() == null) {
                continue;
            }
            mergeTaggedParagraphVisualStyleScores(
                    scores,
                    preparedLine.styleResult().markedText,
                    preparedLine.styleResult().styleMap
            );
        }
        return scores;
    }

    private static LinkedHashMap<Style, Integer> collectTaggedParagraphVisualStyleScores(
            String taggedText,
            Map<Integer, Style> styleMap
    ) {
        LinkedHashMap<Style, Integer> scores = new LinkedHashMap<>();
        mergeTaggedParagraphVisualStyleScores(scores, taggedText, styleMap);
        return scores;
    }

    private static void mergeTaggedParagraphVisualStyleScores(
            Map<Style, Integer> scores,
            String taggedText,
            Map<Integer, Style> styleMap
    ) {
        if (scores == null
                || taggedText == null
                || taggedText.isBlank()
                || styleMap == null
                || styleMap.isEmpty()) {
            return;
        }

        for (ParagraphTaggedRun run : parseParagraphTaggedRuns(taggedText)) {
            if (run == null || run.styleId == null || run.content == null || run.content.isBlank()) {
                continue;
            }

            int score = scoreSignificantStyledParagraphContent(run.content);
            if (score <= 0) {
                continue;
            }

            Style visualStyle = StylePreserver.sanitizeStyleForComparison(
                    styleMap.getOrDefault(run.styleId, Style.EMPTY),
                    true
            );
            if (visualStyle.isEmpty()) {
                continue;
            }
            scores.merge(visualStyle, score, Integer::sum);
        }
    }

    private static int scoreSignificantStyledParagraphContent(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int score = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || TooltipTemplateRuntime.containsDecorativeGlyph(Character.toString(codePoint))) {
                continue;
            }
            if (Character.isLetterOrDigit(codePoint) || isCjkCodePoint(codePoint)) {
                score++;
            }
        }
        return score;
    }

    private static String summarizeParagraphVisualStyles(Iterable<Style> styles) {
        if (styles == null) {
            return "[]";
        }

        StringBuilder summary = new StringBuilder("[");
        boolean first = true;
        for (Style style : styles) {
            if (style == null) {
                continue;
            }
            if (!first) {
                summary.append("; ");
            }
            summary.append(describeStyleForLog(style, false));
            first = false;
        }
        summary.append(']');
        return summary.toString();
    }

    private static void logParagraphRenderIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            TooltipParagraphBlock block,
            String phase,
            boolean forceRefresh,
            String detail,
            String normalizedTemplate,
            String renderedText,
            List<Text> wrappedLines
    ) {
        if (!emitDevLog || !TooltipTextMatcherSupport.shouldLogTooltipParagraphResult(config) || block == null) {
            return;
        }

        LOGGER.info(
                "[TooltipDev:{}:paragraph] lines={}-{} phase={} forceRefresh={} key=\"{}\" source=\"{}\" provider=\"{}\" rendered=\"{}\" wrappedLines={} wrapped=\"{}\" detail=\"{}\"",
                source,
                block.startLineIndex() + 1,
                block.endLineIndex() + 1,
                phase,
                forceRefresh,
                TooltipTemplateRuntime.truncateForLog(block.paragraphTemplate() == null ? "" : block.paragraphTemplate().translationTemplateKey(), 220),
                TooltipTemplateRuntime.truncateForLog(summarizeParagraphSource(block), 220),
                TooltipTemplateRuntime.truncateForLog(normalizedTemplate, 220),
                TooltipTemplateRuntime.truncateForLog(renderedText, 220),
                wrappedLines == null ? 0 : wrappedLines.size(),
                TooltipTemplateRuntime.truncateForLog(summarizeWrappedLines(wrappedLines), 220),
                TooltipTemplateRuntime.truncateForLog(detail, 220)
        );
    }

    private static void logParagraphPlanIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            TooltipParagraphBlock block
    ) {
        if (!emitDevLog || !TooltipTextMatcherSupport.shouldLogTooltipParagraphResult(config) || block == null) {
            return;
        }

        LOGGER.info(
                "[TooltipDev:{}:paragraph] lines={}-{} phase=plan key=\"{}\" source=\"{}\" lineCount={} wrapWidth={} bodyStyleId={} detail=\"{}\"",
                source,
                block.startLineIndex() + 1,
                block.endLineIndex() + 1,
                TooltipTemplateRuntime.truncateForLog(block.paragraphTemplate() == null ? "" : block.paragraphTemplate().translationTemplateKey(), 220),
                TooltipTemplateRuntime.truncateForLog(summarizeParagraphSource(block), 220),
                block.preparedLines() == null ? 0 : block.preparedLines().size(),
                block.paragraphTemplate() == null ? -1 : block.paragraphTemplate().wrapWidth(),
                block.paragraphTemplate() == null ? "null" : String.valueOf(block.paragraphTemplate().bodyStyleId()),
                "Paragraph block planned before cache/local-dictionary resolution."
        );
    }

    private static void logParagraphStyleMapIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            TooltipParagraphBlock block,
            String phase,
            String taggedText
    ) {
        if (!emitDevLog
                || !TooltipTextMatcherSupport.shouldLogTooltipStyleMap(config)
                || block == null
                || block.paragraphTemplate() == null) {
            return;
        }

        Map<Integer, Style> styleMap = block.paragraphTemplate().styleMap();
        LinkedHashSet<Integer> usedStyleIds = collectUsedStyleIdsFromTaggedText(taggedText);
        LOGGER.info(
                "[TooltipDev:{}:style-map] lines={}-{} phase={} styleCount={} usedStyleIds={} styles=\"{}\" tagged=\"{}\"",
                source,
                block.startLineIndex() + 1,
                block.endLineIndex() + 1,
                phase == null || phase.isBlank() ? "unknown" : phase,
                styleMap == null ? 0 : styleMap.size(),
                summarizeStyleIdSet(usedStyleIds),
                TooltipTemplateRuntime.truncateForLog(summarizeStyleMapForLog(styleMap, usedStyleIds), 700),
                TooltipTemplateRuntime.truncateForLog(taggedText, 320)
        );
    }

    static void logLineStyleMapIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            int lineIndex,
            String route,
            PreparedTooltipTemplate preparedTemplate
    ) {
        if (!emitDevLog
                || !TooltipTextMatcherSupport.shouldLogTooltipStyleMap(config)
                || preparedTemplate == null
                || preparedTemplate.styleResult() == null) {
            return;
        }

        Map<Integer, Style> styleMap = preparedTemplate.styleResult().styleMap;
        String taggedText = preparedTemplate.styleResult().markedText;
        LinkedHashSet<Integer> usedStyleIds = collectUsedStyleIdsFromTaggedText(taggedText);
        LOGGER.info(
                "[TooltipDev:{}:style-map] line={} route={} phase=source styleCount={} usedStyleIds={} styles=\"{}\" tagged=\"{}\"",
                source,
                lineIndex + 1,
                route == null || route.isBlank() ? "line-template" : route,
                styleMap == null ? 0 : styleMap.size(),
                summarizeStyleIdSet(usedStyleIds),
                TooltipTemplateRuntime.truncateForLog(summarizeStyleMapForLog(styleMap, usedStyleIds), 700),
                TooltipTemplateRuntime.truncateForLog(taggedText, 320)
        );
    }

    private static LinkedHashSet<Integer> collectUsedStyleIdsFromTaggedText(String taggedText) {
        LinkedHashSet<Integer> usedStyleIds = new LinkedHashSet<>();
        if (taggedText == null || taggedText.isBlank()) {
            return usedStyleIds;
        }

        Matcher matcher = TooltipTemplateRuntime.STYLE_TAG_ID_PATTERN.matcher(taggedText);
        while (matcher.find()) {
            usedStyleIds.add(Integer.parseInt(matcher.group(1)));
        }
        return usedStyleIds;
    }

    private static String summarizeStyleIdSet(Set<Integer> styleIds) {
        if (styleIds == null || styleIds.isEmpty()) {
            return "[]";
        }

        StringBuilder summary = new StringBuilder("[");
        boolean first = true;
        for (Integer styleId : styleIds) {
            if (!first) {
                summary.append(',');
            }
            summary.append(styleId == null ? "null" : styleId);
            first = false;
        }
        summary.append(']');
        return summary.toString();
    }

    private static String summarizeStyleMapForLog(Map<Integer, Style> styleMap, Set<Integer> usedStyleIds) {
        if (styleMap == null || styleMap.isEmpty()) {
            return "";
        }

        List<Integer> orderedStyleIds = new ArrayList<>();
        if (usedStyleIds != null && !usedStyleIds.isEmpty()) {
            orderedStyleIds.addAll(usedStyleIds);
        } else {
            orderedStyleIds.addAll(styleMap.keySet());
            orderedStyleIds.sort(Integer::compareTo);
        }

        StringBuilder summary = new StringBuilder();
        for (Integer styleId : orderedStyleIds) {
            if (styleId == null) {
                continue;
            }

            if (!summary.isEmpty()) {
                summary.append("; ");
            }
            Style rawStyle = styleMap.getOrDefault(styleId, Style.EMPTY);
            Style visualStyle = StylePreserver.sanitizeStyleForComparison(rawStyle, true);
            summary.append(styleId)
                    .append("={raw:")
                    .append(describeStyleForLog(rawStyle, true))
                    .append(",visual:")
                    .append(describeStyleForLog(visualStyle, false))
                    .append('}');
        }
        return summary.toString();
    }

    private static String describeStyleForLog(Style style, boolean includeFont) {
        if (style == null) {
            style = Style.EMPTY;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("color=").append(formatStyleColorForLog(style))
                .append(",b=").append(style.isBold() ? '1' : '0')
                .append(",i=").append(style.isItalic() ? '1' : '0')
                .append(",u=").append(style.isUnderlined() ? '1' : '0')
                .append(",s=").append(style.isStrikethrough() ? '1' : '0')
                .append(",o=").append(style.isObfuscated() ? '1' : '0');
        if (includeFont) {
            summary.append(",font=")
                    .append(style.getFont() == null ? "-" : style.getFont());
        }
        return summary.toString();
    }

    private static String formatStyleColorForLog(Style style) {
        if (style == null || style.getColor() == null) {
            return "-";
        }

        int rgb = style.getColor().getRgb() & 0xFFFFFF;
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static String summarizeParagraphSource(TooltipParagraphBlock block) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }

            String raw = TooltipRoutePlanner.normalizeTooltipText(preparedLine.sourceLine().getString());
            if (raw.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" / ");
            }
            builder.append(raw);
        }
        return builder.toString();
    }

    private static String summarizeWrappedLines(List<Text> wrappedLines) {
        if (wrappedLines == null || wrappedLines.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Text wrappedLine : wrappedLines) {
            if (wrappedLine == null) {
                continue;
            }

            String raw = TooltipRoutePlanner.normalizeTooltipText(wrappedLine.getString());
            if (raw.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(raw);
        }
        return builder.toString();
    }

    private static boolean containsDigit(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (Character.isDigit(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}
