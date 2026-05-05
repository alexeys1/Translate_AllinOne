package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TooltipRoutePlanner {
    private TooltipRoutePlanner() {
    }

    enum TooltipRouteKind {
        PASSTHROUGH,
        PARAGRAPH_BLOCK,
        STRUCTURED_LINE,
        LINE_TEMPLATE
    }

    record TooltipLineCandidate(
            int lineIndex,
            Text line,
            boolean firstContentLine,
            TooltipTextMatcherSupport.TooltipLineDecision decision
    ) {
    }

    record TooltipParagraphBlock(
            int startIndex,
            int endExclusive,
            int startLineIndex,
            int endLineIndex,
            List<TooltipTemplateRuntime.PreparedTooltipTemplate> preparedLines,
            TooltipTemplateRuntime.PreparedParagraphTemplate paragraphTemplate
    ) {
    }

    record TooltipRouteSegment(
            TooltipRouteKind kind,
            int startIndex,
            int endExclusive,
            int translatableLineCount,
            TooltipLineCandidate candidate,
            TooltipParagraphBlock paragraphBlock,
            TooltipTemplateRuntime.PreparedTooltipTemplate preparedTemplate,
            Set<String> translationTemplateKeys
    ) {
    }

    record TooltipPlan(
            List<TooltipLineCandidate> candidates,
            List<TooltipRouteSegment> segments,
            Set<String> translationTemplateKeys
    ) {
    }

    static TooltipPlan planTooltip(List<Text> tooltip, ItemTranslateConfig config, boolean useTagStylePreservation) {
        if (tooltip == null || tooltip.isEmpty()) {
            return new TooltipPlan(List.of(), List.of(), Collections.emptySet());
        }

        boolean decorativeTooltipContext = useTagStylePreservation
                || TooltipDecorativeContextSupport.isDecorativeTooltipContext(tooltip);
        List<TooltipLineCandidate> candidates = evaluateTooltipLines(tooltip, config, decorativeTooltipContext);
        List<TooltipRouteSegment> segments = new ArrayList<>(candidates.size());
        LinkedHashSet<String> allTranslationTemplateKeys = new LinkedHashSet<>();

        for (int index = 0; index < candidates.size(); ) {
            TooltipLineCandidate candidate = candidates.get(index);
            if (candidate.decision() == null || !candidate.decision().shouldTranslate()) {
                segments.add(new TooltipRouteSegment(
                        TooltipRouteKind.PASSTHROUGH,
                        index,
                        index + 1,
                        0,
                        candidate,
                        null,
                        null,
                        Set.of()
                ));
                index++;
                continue;
            }

            TooltipParagraphBlock paragraphBlock = buildParagraphBlock(candidates, index, useTagStylePreservation);
            if (paragraphBlock != null) {
                Set<String> blockKeys = singleKeySet(paragraphBlock.paragraphTemplate().translationTemplateKey());
                allTranslationTemplateKeys.addAll(blockKeys);
                segments.add(new TooltipRouteSegment(
                        TooltipRouteKind.PARAGRAPH_BLOCK,
                        index,
                        paragraphBlock.endExclusive(),
                        paragraphBlock.preparedLines().size(),
                        candidate,
                        paragraphBlock,
                        null,
                        blockKeys
                ));
                index = paragraphBlock.endExclusive();
                continue;
            }

            Set<String> structuredKeys = TooltipStructuredCaptureSupport.collectStructuredTemplateKeys(
                    candidate.line(),
                    useTagStylePreservation
            );
            if (!structuredKeys.isEmpty()) {
                Set<String> copiedKeys = immutableOrderedSet(structuredKeys);
                allTranslationTemplateKeys.addAll(copiedKeys);
                segments.add(new TooltipRouteSegment(
                        TooltipRouteKind.STRUCTURED_LINE,
                        index,
                        index + 1,
                        1,
                        candidate,
                        null,
                        null,
                        copiedKeys
                ));
                index++;
                continue;
            }

            TooltipTemplateRuntime.PreparedTooltipTemplate preparedTemplate =
                    TooltipTemplateRuntime.prepareTemplate(candidate.line(), useTagStylePreservation);
            Set<String> lineKeys = singleKeySet(preparedTemplate.translationTemplateKey());
            allTranslationTemplateKeys.addAll(lineKeys);
            segments.add(new TooltipRouteSegment(
                    TooltipRouteKind.LINE_TEMPLATE,
                    index,
                    index + 1,
                    1,
                    candidate,
                    null,
                    preparedTemplate,
                    lineKeys
            ));
            index++;
        }

        return new TooltipPlan(
                List.copyOf(candidates),
                List.copyOf(segments),
                immutableOrderedSet(allTranslationTemplateKeys)
        );
    }

    static void logLineDecisionsIfDev(
            TooltipPlan tooltipPlan,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        if (tooltipPlan == null || tooltipPlan.candidates() == null) {
            return;
        }

        for (TooltipLineCandidate candidate : tooltipPlan.candidates()) {
            if (candidate == null || candidate.decision() == null) {
                continue;
            }
            TooltipTextMatcherSupport.logLineDecisionIfDev(
                    config,
                    emitDevLog,
                    devSource,
                    candidate.lineIndex(),
                    candidate.decision(),
                    candidate.line()
            );
        }
    }

    static String normalizeTooltipText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
    }

    private static List<TooltipLineCandidate> evaluateTooltipLines(
            List<Text> tooltip,
            ItemTranslateConfig config,
            boolean decorativeTooltipContext
    ) {
        List<TooltipLineCandidate> candidates = new ArrayList<>(tooltip.size());
        boolean nameSlotAvailable = true;
        String firstTitleComparisonText = "";

        for (int lineIndex = 0; lineIndex < tooltip.size(); lineIndex++) {
            Text line = tooltip.get(lineIndex);
            if (line == null || line.getString().trim().isEmpty() || TooltipInternalLineSupport.isInternalGeneratedLine(line)) {
                candidates.add(new TooltipLineCandidate(
                        lineIndex,
                        line,
                        nameSlotAvailable,
                        null
                ));
                continue;
            }

            TooltipTitleLineHeuristics.TitleLineEvaluation titleLineEvaluation =
                    TooltipTitleLineHeuristics.evaluateLine(
                            line,
                            nameSlotAvailable,
                            decorativeTooltipContext,
                            firstTitleComparisonText
                    );
            TooltipTextMatcherSupport.TooltipLineDecision decision =
                    TooltipTextMatcherSupport.evaluateTooltipLine(
                            line,
                            titleLineEvaluation.firstContentLine(),
                            config,
                            decorativeTooltipContext
                    );
            candidates.add(new TooltipLineCandidate(
                    lineIndex,
                    line,
                    titleLineEvaluation.firstContentLine(),
                    decision
            ));

            if (titleLineEvaluation.consumesNameSlot()) {
                nameSlotAvailable = false;
                firstTitleComparisonText = titleLineEvaluation.nextFirstTitleComparisonText();
            }
        }

        return candidates;
    }

    private static TooltipParagraphBlock buildParagraphBlock(
            List<TooltipLineCandidate> candidates,
            int startIndex,
            boolean useTagStylePreservation
    ) {
        if (!canStartParagraphBlock(candidates, startIndex, useTagStylePreservation)) {
            return null;
        }

        List<TooltipTemplateRuntime.PreparedTooltipTemplate> preparedLines = new ArrayList<>();
        preparedLines.add(TooltipTemplateRuntime.prepareTemplate(
                candidates.get(startIndex).line(),
                useTagStylePreservation
        ));
        int endExclusive = startIndex + 1;
        while (endExclusive < candidates.size()
                && canExtendParagraphBlock(
                candidates.get(endExclusive - 1),
                candidates.get(endExclusive),
                useTagStylePreservation
        )) {
            preparedLines.add(TooltipTemplateRuntime.prepareTemplate(
                    candidates.get(endExclusive).line(),
                    useTagStylePreservation
            ));
            endExclusive++;
        }

        if (preparedLines.size() <= 1) {
            return null;
        }

        TooltipTemplateRuntime.PreparedParagraphTemplate paragraphTemplate =
                TooltipTemplateRuntime.prepareParagraphTemplate(preparedLines);
        if (paragraphTemplate == null
                || paragraphTemplate.translationTemplateKey() == null
                || paragraphTemplate.translationTemplateKey().isBlank()) {
            return null;
        }

        return new TooltipParagraphBlock(
                startIndex,
                endExclusive,
                candidates.get(startIndex).lineIndex(),
                candidates.get(endExclusive - 1).lineIndex(),
                List.copyOf(preparedLines),
                paragraphTemplate
        );
    }

    private static boolean canStartParagraphBlock(
            List<TooltipLineCandidate> candidates,
            int startIndex,
            boolean useTagStylePreservation
    ) {
        if (candidates == null || startIndex < 0 || startIndex + 1 >= candidates.size()) {
            return false;
        }

        TooltipLineCandidate current = candidates.get(startIndex);
        TooltipLineCandidate next = candidates.get(startIndex + 1);
        return canExtendParagraphBlock(current, next, useTagStylePreservation);
    }

    private static boolean canExtendParagraphBlock(
            TooltipLineCandidate previous,
            TooltipLineCandidate next,
            boolean useTagStylePreservation
    ) {
        return isParagraphLikeLine(previous)
                && isParagraphLikeLine(next)
                && !hasStructuredCaptureRoute(previous, useTagStylePreservation)
                && !hasStructuredCaptureRoute(next, useTagStylePreservation)
                && !endsWithStrongTerminalPunctuation(previous.decision().rawText());
    }

    private static boolean hasStructuredCaptureRoute(
            TooltipLineCandidate candidate,
            boolean useTagStylePreservation
    ) {
        return candidate != null
                && candidate.line() != null
                && !TooltipStructuredCaptureSupport.collectStructuredTemplateKeys(
                candidate.line(),
                useTagStylePreservation
        ).isEmpty();
    }

    private static boolean isParagraphLikeLine(TooltipLineCandidate candidate) {
        if (candidate == null
                || candidate.line() == null
                || candidate.firstContentLine()
                || candidate.decision() == null
                || !candidate.decision().shouldTranslate()) {
            return false;
        }

        String raw = normalizeTooltipText(candidate.decision().rawText());
        if (raw.isEmpty() || raw.indexOf(':') >= 0 || raw.indexOf('：') >= 0) {
            return false;
        }
        if (TooltipTemplateRuntime.hasLocalDictionaryTranslation(candidate.line())) {
            return false;
        }
        if (!containsLetterContent(raw)) {
            return false;
        }
        if (looksLikeBulletOrListLine(raw)
                || looksLikeStructuredStatLine(raw)
                || looksLikeDecoratedStructuredTooltipLine(raw)
                || looksLikeStandaloneEnchantmentHeader(raw)
                || looksLikeEnchantmentListLine(raw)
                || looksLikeMenuEntryLine(raw)) {
            return false;
        }
        return !looksLikeUppercaseHeader(raw);
    }

    private static boolean containsLetterContent(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean looksLikeUppercaseHeader(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        int letterCount = 0;
        boolean sawUppercase = false;
        boolean sawLowercase = false;
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                letterCount++;
                if (Character.isUpperCase(codePoint)) {
                    sawUppercase = true;
                } else if (Character.isLowerCase(codePoint)) {
                    sawLowercase = true;
                }
            }
            offset += Character.charCount(codePoint);
        }

        if (letterCount >= 3 && sawUppercase && !sawLowercase) {
            return true;
        }

        String upper = raw.toUpperCase(Locale.ROOT);
        return upper.contains("RIGHT CLICK")
                || upper.contains("LEFT CLICK")
                || upper.contains("SNEAK")
                || upper.contains("SHIFT");
    }

    private static boolean looksLikeBulletOrListLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String trimmed = raw.trim();
        int first = trimmed.codePointAt(0);
        return first == '•'
                || first == '*'
                || first == '-'
                || first == '–'
                || first == '—'
                || first == '●'
                || first == '▪'
                || first == '◦'
                || first == '‣';
    }

    private static boolean looksLikeStructuredStatLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String trimmed = raw.trim();
        int first = trimmed.codePointAt(0);
        if ((first == '+' || first == '-') && containsDigit(trimmed)) {
            return true;
        }

        if (Character.isDigit(first)
                && countWordTokens(trimmed) <= 6
                && (containsSymbolicMarker(trimmed) || trimmed.indexOf('%') >= 0)) {
            return true;
        }

        return looksLikeLeadingLabelStructuredStatLine(trimmed);
    }

    private static boolean looksLikeLeadingLabelStructuredStatLine(String raw) {
        if (raw == null
                || raw.isBlank()
                || TooltipTemplateRuntime.containsDecorativeGlyph(raw)
                || !containsDigit(raw)
                || containsSentencePunctuationOutsideNumericContext(raw)) {
            return false;
        }

        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 2 || tokens.length > 8) {
            return false;
        }

        int firstValueTokenIndex = -1;
        for (int index = 0; index < tokens.length; index++) {
            if (looksLikeStructuredStatValueToken(tokens[index])) {
                firstValueTokenIndex = index;
                break;
            }
        }
        if (firstValueTokenIndex <= 0) {
            return false;
        }

        int labelTokenCount = 0;
        for (int index = 0; index < firstValueTokenIndex; index++) {
            if (!isStructuredStatLabelToken(tokens[index])) {
                return false;
            }
            labelTokenCount++;
        }
        if (labelTokenCount == 0 || labelTokenCount > 6) {
            return false;
        }

        for (int index = firstValueTokenIndex; index < tokens.length; index++) {
            if (!looksLikeStructuredStatValueToken(tokens[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeDecoratedStructuredTooltipLine(String raw) {
        if (raw == null || raw.isBlank() || !TooltipTemplateRuntime.containsDecorativeGlyph(raw)) {
            return false;
        }

        String visible = normalizeTooltipText(TooltipTemplateRuntime.stripDecorativeGlyphsForHeuristics(raw));
        if (visible.isEmpty()
                || !containsLetterContent(visible)
                || containsSentencePunctuationOutsideNumericContext(visible)) {
            return false;
        }

        int wordCount = countWordTokens(visible);
        if (wordCount == 0 || wordCount > 8) {
            return false;
        }

        return containsDigit(visible)
                || visible.indexOf('%') >= 0
                || visible.indexOf('/') >= 0;
    }

    private static boolean looksLikeMenuEntryLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String trimmed = raw.trim();
        if (containsDigit(trimmed)
                || containsSentencePunctuation(trimmed)
                || trimmed.indexOf('/') >= 0
                || trimmed.indexOf('\\') >= 0) {
            return false;
        }

        String[] tokens = trimmed.split("\\s+");
        int wordCount = 0;
        for (String token : tokens) {
            String normalized = trimEdgePunctuation(token);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!containsLetterContent(normalized) || !isMenuWordToken(normalized)) {
                return false;
            }
            wordCount++;
        }

        return wordCount >= 1 && wordCount <= 4;
    }

    private static boolean looksLikeEnchantmentListLine(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String trimmed = raw.trim();
        if (trimmed.indexOf(',') < 0 || !containsDigit(trimmed)) {
            return false;
        }

        String[] segments = trimmed.split(",");
        int matchedEntries = 0;
        for (String segment : segments) {
            String normalized = segment == null ? "" : segment.trim();
            if (normalized.isEmpty() || !looksLikeEnchantmentEntry(normalized)) {
                return false;
            }
            matchedEntries++;
        }

        return matchedEntries >= 2;
    }

    private static boolean looksLikeStandaloneEnchantmentHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String visible = normalizeTooltipText(TooltipTemplateRuntime.stripDecorativeGlyphsForHeuristics(raw));
        if (visible.isEmpty()
                || visible.indexOf(':') >= 0
                || visible.indexOf('：') >= 0
                || visible.indexOf('/') >= 0
                || visible.indexOf('\\') >= 0
                || visible.indexOf(',') >= 0
                || containsSentencePunctuation(visible)) {
            return false;
        }

        String[] tokens = visible.split("\\s+");

        String lastToken = trimEdgePunctuation(tokens[tokens.length - 1]);
        boolean levelLike = isIntegerToken(lastToken) || isRomanNumeralToken(lastToken);
        boolean bonusLike = tokens.length == 2 && "bonus".equalsIgnoreCase(lastToken);
        boolean hasDecorativePrefix = TooltipTemplateRuntime.containsDecorativeGlyph(raw)
                || hasNonLatinDecorativePrefix(raw);
        boolean decorativeGlyphHeader = !levelLike
                && !bonusLike
                && !containsDigit(visible)
                && hasDecorativePrefix;
        if (!levelLike && !bonusLike && !decorativeGlyphHeader) {
            return false;
        }

        int tokenStart = 0;
        if (hasDecorativePrefix && tokens.length > 1 && startsWithNonLatinLetter(tokens[0])) {
            tokenStart = 1;
        }
        int effectiveTokenCount = tokens.length - tokenStart;
        if (effectiveTokenCount < (levelLike ? 1 : 2) || effectiveTokenCount > 4) {
            return false;
        }

        int limit = levelLike ? tokens.length - 1 : tokens.length;
        int titleWordCount = 0;
        for (int index = tokenStart; index < limit; index++) {
            String token = trimEdgePunctuation(tokens[index]);
            if (token.isEmpty()) {
                return false;
            }
            if (isEnchantmentConnectorWord(token)) {
                continue;
            }
            if (!isTitleLikeWord(token)) {
                return false;
            }
            titleWordCount++;
        }
        return titleWordCount >= 1;
    }

    private static boolean hasNonLatinDecorativePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                continue;
            }

            return Character.isLetter(codePoint)
                    && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN;
        }

        return false;
    }

    private static boolean startsWithNonLatinLetter(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        int codePoint = token.codePointAt(0);
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN;
    }

    private static boolean looksLikeEnchantmentEntry(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }

        String[] tokens = segment.trim().split("\\s+");
        if (tokens.length < 2) {
            return false;
        }

        String levelToken = trimEdgePunctuation(tokens[tokens.length - 1]);
        if (!isIntegerToken(levelToken)) {
            return false;
        }

        int nameTokenCount = 0;
        for (int index = 0; index < tokens.length - 1; index++) {
            String token = trimEdgePunctuation(tokens[index]);
            if (token.isEmpty() || !isLikelyEnchantmentNameToken(token)) {
                return false;
            }
            nameTokenCount++;
        }

        return nameTokenCount >= 1;
    }

    private static boolean isLikelyEnchantmentNameToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String normalized = token.trim();
        if (isEnchantmentConnectorWord(normalized)) {
            return true;
        }

        String[] parts = normalized.split("[-']");
        boolean sawWordPart = false;
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (!isTitleLikeWord(part)) {
                return false;
            }
            sawWordPart = true;
        }
        return sawWordPart;
    }

    private static boolean isEnchantmentConnectorWord(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String lower = token.toLowerCase(Locale.ROOT);
        return lower.equals("of")
                || lower.equals("the")
                || lower.equals("and")
                || lower.equals("for")
                || lower.equals("to")
                || lower.equals("in")
                || lower.equals("on");
    }

    private static boolean isTitleLikeWord(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        boolean sawLetter = false;
        boolean firstLetterHandled = false;
        for (int offset = 0; offset < token.length(); ) {
            int codePoint = token.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (!Character.isLetter(codePoint)) {
                return false;
            }

            sawLetter = true;
            if (!firstLetterHandled) {
                if (!Character.isUpperCase(codePoint)) {
                    return false;
                }
                firstLetterHandled = true;
            }
        }

        return sawLetter;
    }

    private static boolean isIntegerToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        for (int offset = 0; offset < token.length(); ) {
            int codePoint = token.codePointAt(offset);
            if (!Character.isDigit(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isRomanNumeralToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String upper = token.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty() || upper.length() > 8) {
            return false;
        }
        return upper.matches("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");
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

    private static int countWordTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }

        int count = 0;
        for (String token : raw.trim().split("\\s+")) {
            if (!trimEdgePunctuation(token).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isStructuredStatLabelToken(String token) {
        String normalized = trimEdgePunctuation(token);
        if (normalized.isEmpty() || containsDigit(normalized)) {
            return false;
        }
        if (isEnchantmentConnectorWord(normalized)) {
            return true;
        }

        String[] parts = normalized.split("[-']");
        boolean sawWordPart = false;
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (!isTitleLikeWord(part)) {
                return false;
            }
            sawWordPart = true;
        }
        return sawWordPart;
    }

    private static boolean looksLikeStructuredStatValueToken(String token) {
        String normalized = trimEdgePunctuation(token);
        if (normalized.isEmpty() || !containsDigit(normalized)) {
            return false;
        }

        boolean sawDigit = false;
        int letterCount = 0;
        int firstDigitOffset = -1;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isDigit(codePoint)) {
                sawDigit = true;
                if (firstDigitOffset < 0) {
                    firstDigitOffset = offset;
                }
                offset += Character.charCount(codePoint);
                continue;
            }
            if (codePoint == '.'
                    || codePoint == ','
                    || codePoint == '/'
                    || codePoint == '+'
                    || codePoint == '-'
                    || codePoint == '%') {
                offset += Character.charCount(codePoint);
                continue;
            }
            if (!Character.isLetter(codePoint) || offset < firstDigitOffset) {
                return false;
            }
            letterCount++;
            if (letterCount > 3) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return sawDigit;
    }

    private static boolean containsSymbolicMarker(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (!Character.isLetterOrDigit(codePoint)
                    && !Character.isWhitespace(codePoint)
                    && codePoint != '.'
                    && codePoint != ',') {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsSentencePunctuation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (codePoint == '.'
                    || codePoint == ','
                    || codePoint == '!'
                    || codePoint == '?'
                    || codePoint == ';'
                    || codePoint == ':'
                    || codePoint == '。'
                    || codePoint == '，'
                    || codePoint == '！'
                    || codePoint == '？'
                    || codePoint == '：'
                    || codePoint == '；') {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsSentencePunctuationOutsideNumericContext(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            int charCount = Character.charCount(codePoint);
            if ((codePoint == '.' || codePoint == ',')
                    && Character.isDigit(findPreviousNonWhitespaceCodePoint(raw, offset))
                    && Character.isDigit(findNextNonWhitespaceCodePoint(raw, offset + charCount))) {
                offset += charCount;
                continue;
            }
            if (codePoint == '.'
                    || codePoint == ','
                    || codePoint == '!'
                    || codePoint == '?'
                    || codePoint == ';'
                    || codePoint == ':'
                    || codePoint == '。'
                    || codePoint == '，'
                    || codePoint == '！'
                    || codePoint == '？'
                    || codePoint == '：'
                    || codePoint == '；') {
                return true;
            }
            offset += charCount;
        }
        return false;
    }

    private static int findPreviousNonWhitespaceCodePoint(String raw, int offset) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }

        for (int index = Math.min(offset, raw.length()); index > 0; ) {
            int codePoint = raw.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) {
                return codePoint;
            }
            index -= Character.charCount(codePoint);
        }
        return -1;
    }

    private static int findNextNonWhitespaceCodePoint(String raw, int offset) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }

        for (int index = Math.max(0, offset); index < raw.length(); ) {
            int codePoint = raw.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                return codePoint;
            }
            index += Character.charCount(codePoint);
        }
        return -1;
    }

    private static String trimEdgePunctuation(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }

        int start = 0;
        int end = token.length();
        while (start < end) {
            int codePoint = token.codePointAt(start);
            if (Character.isLetterOrDigit(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = token.codePointBefore(end);
            if (Character.isLetterOrDigit(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return token.substring(start, end);
    }

    private static boolean isMenuWordToken(String token) {
        boolean sawLetter = false;
        boolean firstLetterHandled = false;

        for (int offset = 0; offset < token.length(); ) {
            int codePoint = token.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (!Character.isLetter(codePoint)) {
                if (codePoint == '\'' || codePoint == '-') {
                    continue;
                }
                return false;
            }

            sawLetter = true;
            if (!firstLetterHandled) {
                if (!Character.isUpperCase(codePoint)) {
                    return false;
                }
                firstLetterHandled = true;
                continue;
            }

            if (!Character.isLowerCase(codePoint) && !Character.isUpperCase(codePoint)) {
                return false;
            }
        }

        return sawLetter;
    }

    private static boolean endsWithStrongTerminalPunctuation(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String trimmed = raw.trim();
        int endCodePoint = trimmed.codePointBefore(trimmed.length());
        return endCodePoint == '.'
                || endCodePoint == '!'
                || endCodePoint == '?'
                || endCodePoint == '。'
                || endCodePoint == '！'
                || endCodePoint == '？';
    }

    private static Set<String> singleKeySet(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(translationTemplateKey);
        return Collections.unmodifiableSet(keys);
    }

    private static Set<String> immutableOrderedSet(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }
}
