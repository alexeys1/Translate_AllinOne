package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.AnimationManager;
import com.cedarxuesong.translate_allinone.utils.cache.ItemTemplateCache;
import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.input.KeybindingManager;
import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import com.cedarxuesong.translate_allinone.utils.text.TemplateProcessor;
import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TooltipTranslationSupport {
    private static final String MISSING_KEY_HINT = "missing key";
    private static final String KEY_MISMATCH_HINT = "key mismatch";
    private static final String STORED_LEGACY_PREFIX = "[taio:legacy]";
    private static final String TOOLTIP_REFRESH_NOTICE_KEY = "text.translate_allinone.item.tooltip_refresh_forced";
    private static final long REFRESH_NOTICE_DURATION_MILLIS = 1500L;
    private static final long CACHE_MIGRATION_LOG_THROTTLE_WINDOW_MILLIS = 5000L;
    private static final int CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT = 4096;
    private static final long FORCE_REFRESH_COMPAT_BYPASS_MILLIS = 300_000L;
    private static final int FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT = 4096;
    private static final Pattern STYLE_TAG_ID_PATTERN = Pattern.compile("</?s(\\d+)>");
    private static final Pattern NUMERIC_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{d(\\d+)}");
    private static final Pattern GLYPH_PLACEHOLDER_ID_PATTERN = Pattern.compile("\\{g(\\d+)}");
    private static final Pattern ENGLISH_CONNECTOR_PATTERN =
            Pattern.compile("(?i)\\b(by|to|and|of|for|in|on|from|with|the|a|an)\\b");
    private static final int MIN_PARAGRAPH_BODY_STYLE_DOMINANT_SCORE = 5;
    private static final int MIN_PARAGRAPH_BODY_RUN_SCORE = 1;
    private static final int MIN_PARAGRAPH_BODY_STYLE_DOMINANCE_PERCENT = 55;
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTranslationSupport");
    private static final Set<Integer> refreshedTooltipSignaturesThisHold = new HashSet<>();
    private static final ConcurrentHashMap<CacheMigrationLogKey, CacheMigrationLogThrottleState> cacheMigrationLogThrottle =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> forceRefreshCompatBypassUntilByKey =
            new ConcurrentHashMap<>();
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

    private enum CachedTranslationFormat {
        TAGGED,
        LEGACY
    }

    private record PreparedTooltipTemplate(
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

    private record CompatibilityTemplateKey(String key, CachedTranslationFormat format) {
    }

    private record AdaptedCachedTranslation(String translation, CachedTranslationFormat format) {
    }

    private record DecodedStoredTranslation(String translation, CachedTranslationFormat format) {
    }

    private record ResolvedTemplateLookup(
            ItemTemplateCache.LookupResult lookupResult,
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

    private record TooltipLineCandidate(
            int lineIndex,
            Text line,
            boolean firstContentLine,
            TooltipTextMatcherSupport.TooltipLineDecision decision
    ) {
    }

    private record PreparedParagraphTemplate(
            String translationTemplateKey,
            Map<Integer, Style> styleMap,
            List<String> templateValues,
            List<String> glyphValues,
            Integer bodyStyleId,
            int wrapWidth
    ) {
    }

    private record TooltipParagraphBlock(
            int startIndex,
            int endExclusive,
            int startLineIndex,
            int endLineIndex,
            List<PreparedTooltipTemplate> preparedLines,
            PreparedParagraphTemplate paragraphTemplate
    ) {
    }

    private record TooltipBlockTranslationAttempt(
            List<TooltipLineResult> lineResults,
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
        return translatePreparedTemplate(prepareTemplate(line, useTagStylePreservation));
    }

    private static TooltipLineResult translatePreparedTemplate(PreparedTooltipTemplate preparedTemplate) {
        ResolvedTemplateLookup resolvedLookup = resolveLookup(preparedTemplate);
        ItemTemplateCache.LookupResult lookupResult = resolvedLookup.lookupResult();
        ItemTemplateCache.TranslationStatus status = lookupResult.status();
        boolean pending = status == ItemTemplateCache.TranslationStatus.PENDING || status == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
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
        if (status == ItemTemplateCache.TranslationStatus.TRANSLATED && resolvedLookup.renderedLineOverride() != null) {
            finalTooltipLine = resolvedLookup.renderedLineOverride();
        } else if (status == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                    TemplateProcessor.reassemble(translatedTemplate, preparedTemplate.templateResult().values()),
                    preparedTemplate.glyphResult().values(),
                    true
            );
            finalTooltipLine = resolvedLookup.format() == CachedTranslationFormat.TAGGED
                    ? StylePreserver.reapplyStylesFromTags(reassembledTranslated, preparedTemplate.styleResult().styleMap, true)
                    : StylePreserver.fromLegacyText(reassembledTranslated);
        } else if (status == ItemTemplateCache.TranslationStatus.ERROR) {
            String errorMessage = lookupResult.errorMessage();
            if (isMissingKeyIssue(errorMessage)) {
                pending = true;
                missingKeyIssue = true;
                finalTooltipLine = originalTextObject;
            } else {
                finalTooltipLine = Text.literal("Error: " + errorMessage).formatted(Formatting.RED);
            }
        } else {
            finalTooltipLine = AnimationManager.getAnimatedStyledText(originalTextObject, preparedTemplate.translationTemplateKey(), false);
        }

        return new TooltipLineResult(finalTooltipLine, pending, missingKeyIssue);
    }

    public static void maybeForceRefreshCurrentTooltip(List<Text> tooltip, ItemTranslateConfig config) {
        boolean isRefreshPressed = config != null
                && config.keybinding != null
                && KeybindingManager.isPressed(config.keybinding.refreshBinding);
        if (!isRefreshPressed) {
            synchronized (refreshedTooltipSignaturesThisHold) {
                refreshedTooltipSignaturesThisHold.clear();
            }
            return;
        }

        Set<String> keysToRefresh = collectTranslatableTemplateKeys(tooltip, config);
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
            registerForceRefreshCompatBypass(keysToRefresh);
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

        maybeForceRefreshCurrentTooltip(tooltip, config);
        boolean showRefreshNotice = shouldShowRefreshNotice(tooltip, config);

        boolean isKeyPressed = KeybindingManager.isPressed(config.keybinding.binding);
        if (shouldShowOriginal(config.keybinding.mode, isKeyPressed)) {
            return appendRefreshNoticeLine(tooltip, showRefreshNotice);
        }

        boolean emitDevLog = TooltipTextMatcherSupport.beginTooltipDevPass(config, "screen-mirror", tooltip);
        long tooltipStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;

        try {
            TooltipProcessingResult processedTooltip = processTooltipLines(
                    tooltip,
                    config,
                    config.wynn_item_compatibility,
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
        if (tooltip == null || tooltip.isEmpty()) {
            return new TooltipProcessingResult(List.of(), 0, false, false);
        }

        List<TooltipLineCandidate> candidates = evaluateTooltipLines(tooltip, config, emitDevLog, devSource);
        List<Text> translatedLines = new ArrayList<>(tooltip.size());
        int translatableLines = 0;
        boolean hasPending = false;
        boolean hasMissingKeyIssue = false;

        for (int index = 0; index < candidates.size(); ) {
            TooltipLineCandidate candidate = candidates.get(index);
            if (candidate.decision() == null || !candidate.decision().shouldTranslate()) {
                translatedLines.add(candidate.line());
                index++;
                continue;
            }

            TooltipParagraphBlock paragraphBlock = buildParagraphBlock(candidates, index, useTagStylePreservation);
            if (paragraphBlock != null) {
                long blockStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
                TooltipBlockTranslationAttempt blockAttempt = translateParagraphBlock(
                        paragraphBlock,
                        config,
                        emitDevLog,
                        devSource
                );
                translatableLines += paragraphBlock.preparedLines().size();
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
                    TooltipLineCandidate blockCandidate = candidates.get(index + offset);
                    TooltipTextMatcherSupport.logLineTranslationIfDev(
                            config,
                            emitDevLog,
                            devSource,
                            blockCandidate.lineIndex(),
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
                index = paragraphBlock.endExclusive();
                continue;
            }

            translatableLines++;
            long lineStartedAtNanos = emitDevLog ? System.nanoTime() : 0L;
            TooltipStructuredCaptureSupport.StructuredTooltipLineResult structuredLineResult =
                    TooltipStructuredCaptureSupport.tryTranslateStructuredLine(candidate.line(), useTagStylePreservation);
            String lineTemplateKey = structuredLineResult == null
                    ? extractTemplateKey(candidate.line(), useTagStylePreservation)
                    : null;
            TooltipLineResult lineResult = structuredLineResult != null
                    ? structuredLineResult.lineResult()
                    : translateLine(candidate.line(), useTagStylePreservation);
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
                    candidate.lineIndex(),
                    lineResult,
                    structuredLineResult != null ? "capture" : "line-template",
                    structuredLineResult != null
                            ? structuredLineResult.debugSummary()
                            : "templateKey=" + (lineTemplateKey == null ? "" : lineTemplateKey),
                    lineStartedAtNanos
            );
            index++;
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

    public static boolean shouldShowRefreshNotice(List<Text> tooltip, ItemTranslateConfig config) {
        long expiresAt = refreshNoticeExpiresAtMillis;
        if (expiresAt <= 0L || System.currentTimeMillis() > expiresAt) {
            return false;
        }

        Set<String> keys = collectTranslatableTemplateKeys(tooltip, config);
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

    private static List<TooltipLineCandidate> evaluateTooltipLines(
            List<Text> tooltip,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        List<TooltipLineCandidate> candidates = new ArrayList<>(tooltip.size());
        boolean isFirstLine = true;

        for (int lineIndex = 0; lineIndex < tooltip.size(); lineIndex++) {
            Text line = tooltip.get(lineIndex);
            if (line == null || line.getString().trim().isEmpty() || isInternalGeneratedLine(line)) {
                candidates.add(new TooltipLineCandidate(
                        lineIndex,
                        line,
                        isFirstLine,
                        null
                ));
                continue;
            }

            boolean firstContentLine = isFirstLine;
            isFirstLine = false;
            TooltipTextMatcherSupport.TooltipLineDecision decision =
                    TooltipTextMatcherSupport.evaluateTooltipLine(line, firstContentLine, config);
            TooltipTextMatcherSupport.logLineDecisionIfDev(config, emitDevLog, devSource, lineIndex, decision, line);
            candidates.add(new TooltipLineCandidate(
                    lineIndex,
                    line,
                    firstContentLine,
                    decision
            ));
        }

        return candidates;
    }

    private static Set<String> collectTranslatableTemplateKeys(List<Text> tooltip, ItemTranslateConfig config) {
        Set<String> keys = new LinkedHashSet<>();
        if (tooltip == null || tooltip.isEmpty() || config == null) {
            return keys;
        }

        List<TooltipLineCandidate> candidates = evaluateTooltipLines(tooltip, config, false, "");
        for (int index = 0; index < candidates.size(); ) {
            TooltipLineCandidate candidate = candidates.get(index);
            if (candidate.decision() == null || !candidate.decision().shouldTranslate()) {
                index++;
                continue;
            }

            TooltipParagraphBlock paragraphBlock = buildParagraphBlock(candidates, index, config.wynn_item_compatibility);
            if (paragraphBlock != null) {
                keys.add(paragraphBlock.paragraphTemplate().translationTemplateKey());
                index = paragraphBlock.endExclusive();
                continue;
            }

            Set<String> structuredKeys =
                    TooltipStructuredCaptureSupport.collectStructuredTemplateKeys(candidate.line(), config.wynn_item_compatibility);
            if (!structuredKeys.isEmpty()) {
                keys.addAll(structuredKeys);
                index++;
                continue;
            }

            String translationTemplateKey = extractTemplateKey(candidate.line(), config.wynn_item_compatibility);
            if (translationTemplateKey != null && !translationTemplateKey.isBlank()) {
                keys.add(translationTemplateKey);
            }
            index++;
        }
        return keys;
    }

    private static TooltipParagraphBlock buildParagraphBlock(
            List<TooltipLineCandidate> candidates,
            int startIndex,
            boolean useTagStylePreservation
    ) {
        if (!canStartParagraphBlock(candidates, startIndex)) {
            return null;
        }

        List<PreparedTooltipTemplate> preparedLines = new ArrayList<>();
        preparedLines.add(prepareTemplate(candidates.get(startIndex).line(), true));
        int endExclusive = startIndex + 1;
        while (endExclusive < candidates.size()
                && canExtendParagraphBlock(candidates.get(endExclusive - 1), candidates.get(endExclusive))) {
            preparedLines.add(prepareTemplate(candidates.get(endExclusive).line(), true));
            endExclusive++;
        }

        if (preparedLines.size() <= 1) {
            return null;
        }

        PreparedParagraphTemplate paragraphTemplate = prepareParagraphTemplate(preparedLines);
        if (paragraphTemplate == null || paragraphTemplate.translationTemplateKey() == null
                || paragraphTemplate.translationTemplateKey().isBlank()) {
            return null;
        }

        return new TooltipParagraphBlock(
                startIndex,
                endExclusive,
                candidates.get(startIndex).lineIndex(),
                candidates.get(endExclusive - 1).lineIndex(),
                preparedLines,
                paragraphTemplate
        );
    }

    private static boolean canStartParagraphBlock(List<TooltipLineCandidate> candidates, int startIndex) {
        if (candidates == null || startIndex < 0 || startIndex + 1 >= candidates.size()) {
            return false;
        }

        TooltipLineCandidate current = candidates.get(startIndex);
        TooltipLineCandidate next = candidates.get(startIndex + 1);
        return canExtendParagraphBlock(current, next);
    }

    private static boolean canExtendParagraphBlock(TooltipLineCandidate previous, TooltipLineCandidate next) {
        return isParagraphLikeLine(previous)
                && isParagraphLikeLine(next)
                && !endsWithStrongTerminalPunctuation(previous.decision().rawText());
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

    private static String normalizeTooltipText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
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

        return Character.isDigit(first)
                && countWordTokens(trimmed) <= 6
                && (containsSymbolicMarker(trimmed) || trimmed.indexOf('%') >= 0);
    }

    private static boolean looksLikeDecoratedStructuredTooltipLine(String raw) {
        if (raw == null || raw.isBlank() || !containsDecorativeGlyph(raw)) {
            return false;
        }

        String visible = normalizeTooltipText(stripDecorativeGlyphsForHeuristics(raw));
        if (visible.isEmpty()
                || !containsLetterContent(visible)
                || containsSentencePunctuation(visible)) {
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

        String visible = normalizeTooltipText(stripDecorativeGlyphsForHeuristics(raw));
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
        if (tokens.length < 2 || tokens.length > 4) {
            return false;
        }

        String lastToken = trimEdgePunctuation(tokens[tokens.length - 1]);
        boolean levelLike = isIntegerToken(lastToken) || isRomanNumeralToken(lastToken);
        boolean bonusLike = tokens.length == 2 && "bonus".equalsIgnoreCase(lastToken);
        if (!levelLike && !bonusLike) {
            return false;
        }

        int limit = levelLike ? tokens.length - 1 : tokens.length;
        int titleWordCount = 0;
        for (int index = 0; index < limit; index++) {
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
        for (int i = 0; i < tokens.length - 1; i++) {
            String token = trimEdgePunctuation(tokens[i]);
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

        String normalized = raw.trim();
        char last = normalized.charAt(normalized.length() - 1);
        return last == '.'
                || last == '!'
                || last == '?'
                || last == '。'
                || last == '！'
                || last == '？';
    }

    private static TooltipBlockTranslationAttempt translateParagraphBlock(
            TooltipParagraphBlock block,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        ItemTemplateCache.LookupResult blockLookup = cache.lookupOrQueue(block.paragraphTemplate().translationTemplateKey());
        if (blockLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            List<TooltipLineResult> blockResults = renderTranslatedParagraphBlock(
                    block,
                    blockLookup.translation(),
                    config,
                    emitDevLog,
                    devSource
            );
            if (blockResults != null) {
                return new TooltipBlockTranslationAttempt(blockResults, false, false);
            }
            return translateParagraphBlockLineByLine(block);
        }

        boolean pending = blockLookup.status() == ItemTemplateCache.TranslationStatus.PENDING
                || blockLookup.status() == ItemTemplateCache.TranslationStatus.IN_PROGRESS;
        boolean missingKeyIssue = blockLookup.status() == ItemTemplateCache.TranslationStatus.ERROR
                && isMissingKeyIssue(blockLookup.errorMessage());
        if (blockLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
            pending = true;
        }

        List<TooltipLineResult> fallbackResults = new ArrayList<>(block.preparedLines().size());
        for (int i = 0; i < block.preparedLines().size(); i++) {
            PreparedTooltipTemplate preparedLine = block.preparedLines().get(i);
            Text originalLine = renderOriginalPreparedLine(preparedLine);
            if (pending) {
                originalLine = AnimationManager.getAnimatedStyledText(
                        originalLine,
                        block.paragraphTemplate().translationTemplateKey() + "#" + i,
                        false
                );
            }
            fallbackResults.add(new TooltipLineResult(originalLine, pending, missingKeyIssue));
        }
        return new TooltipBlockTranslationAttempt(fallbackResults, pending, missingKeyIssue);
    }

    private static TooltipBlockTranslationAttempt translateParagraphBlockLineByLine(TooltipParagraphBlock block) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty()) {
            return new TooltipBlockTranslationAttempt(List.of(), false, false);
        }

        List<TooltipLineResult> lineResults = new ArrayList<>(block.preparedLines().size());
        boolean pending = false;
        boolean missingKeyIssue = false;

        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            TooltipLineResult lineResult = translatePreparedTemplate(preparedLine);
            if (lineResult.pending()) {
                pending = true;
            }
            if (lineResult.missingKeyIssue()) {
                missingKeyIssue = true;
            }
            lineResults.add(lineResult);
        }

        return new TooltipBlockTranslationAttempt(lineResults, pending, missingKeyIssue);
    }

    private static List<TooltipLineResult> renderTranslatedParagraphBlock(
            TooltipParagraphBlock block,
            String translatedBlockTemplate,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
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
                devSource
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

        List<TooltipLineResult> results = new ArrayList<>(wrappedLines.size());
        for (Text wrappedLine : wrappedLines) {
            if (wrappedLine == null) {
                continue;
            }
            results.add(new TooltipLineResult(wrappedLine, false, false));
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

    private static Text renderOriginalPreparedLine(PreparedTooltipTemplate preparedTemplate) {
        String reassembledOriginal = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(preparedTemplate.normalizedTemplate(), preparedTemplate.templateResult().values()),
                preparedTemplate.glyphResult().values()
        );
        return preparedTemplate.useTagStylePreservation()
                ? StylePreserver.reapplyStylesFromTags(reassembledOriginal, preparedTemplate.styleResult().styleMap)
                : StylePreserver.reapplyStyles(reassembledOriginal, preparedTemplate.styleResult().styleMap);
    }

    private static PreparedParagraphTemplate prepareParagraphTemplate(List<PreparedTooltipTemplate> preparedLines) {
        if (preparedLines == null || preparedLines.isEmpty()) {
            return null;
        }

        Map<Integer, Style> combinedStyleMap = new HashMap<>();
        List<String> combinedTemplateValues = new ArrayList<>();
        List<String> combinedGlyphValues = new ArrayList<>();
        StringBuilder combinedTemplateKey = new StringBuilder();
        int nextStyleId = 0;
        int nextNumericId = 0;
        int nextGlyphId = 0;

        for (PreparedTooltipTemplate preparedLine : preparedLines) {
            if (preparedLine == null || preparedLine.normalizedTemplate() == null || preparedLine.normalizedTemplate().isBlank()) {
                continue;
            }

            if (combinedTemplateKey.length() > 0) {
                combinedTemplateKey.append(' ');
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
            combinedGlyphValues.addAll(preparedLine.glyphResult().values());

            nextStyleId += countStyleIds(preparedLine.styleResult().styleMap);
            nextNumericId += preparedLine.templateResult().values().size();
            nextGlyphId += preparedLine.glyphResult().values().size();
        }

        if (combinedTemplateKey.isEmpty()) {
            return null;
        }

        return new PreparedParagraphTemplate(
                combinedTemplateKey.toString(),
                combinedStyleMap,
                combinedTemplateValues,
                combinedGlyphValues,
                findDominantParagraphBodyStyleId(combinedTemplateKey.toString(), combinedStyleMap),
                computeParagraphWrapWidth(preparedLines)
        );
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

    private static Text renderTranslatedParagraphText(
            TooltipParagraphBlock block,
            String normalizedTemplate,
            ItemTranslateConfig config,
            boolean emitDevLog,
            String devSource
    ) {
        PreparedParagraphTemplate paragraphTemplate = block.paragraphTemplate();
        String reassembledTranslated = TemplateProcessor.reassembleDecorativeGlyphs(
                TemplateProcessor.reassemble(normalizedTemplate, paragraphTemplate.templateValues()),
                paragraphTemplate.glyphValues(),
                true
        );
        reassembledTranslated = postProcessTranslatedParagraphText(
                reassembledTranslated,
                paragraphTemplate.styleMap(),
                paragraphTemplate.bodyStyleId()
        );
        logParagraphStyleMapIfDev(
                config,
                emitDevLog,
                devSource,
                block,
                reassembledTranslated
        );
        if (containsNumericPlaceholder(reassembledTranslated)) {
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

        String paragraphQualityIssue = describeParagraphQualityIssue(block, reassembledTranslated);
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

    private static String postProcessTranslatedParagraphText(
            String translatedText,
            Map<Integer, Style> styleMap,
            Integer bodyStyleId
    ) {
        if (translatedText == null || translatedText.isBlank()) {
            return translatedText;
        }

        boolean applyChineseHeuristics = shouldApplyChineseParagraphQualityHeuristics();
        String normalized = translatedText;
        if (applyChineseHeuristics) {
            normalized = normalizeChineseTaggedWhitespace(normalized);
        }
        normalized = normalizeParagraphTaggedStyles(normalized, styleMap, bodyStyleId);
        return applyChineseHeuristics ? normalizeChineseTaggedWhitespace(normalized) : normalized;
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
                while (nextIndex < text.length()) {
                    int nextStyleTagEnd = findStyleTagEnd(text, nextIndex);
                    if (nextStyleTagEnd >= nextIndex) {
                        nextIndex = nextStyleTagEnd + 1;
                        continue;
                    }

                    int nextCodePoint = text.codePointAt(nextIndex);
                    if (!Character.isWhitespace(nextCodePoint)) {
                        break;
                    }
                    nextIndex += Character.charCount(nextCodePoint);
                }

                int nextVisibleCodePoint = findNextVisibleCodePoint(text, nextIndex);
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

        canonicalizeEquivalentParagraphStyles(runs, styleMap);
        absorbParagraphBodyStyle(runs, styleMap, bodyStyleId);
        return serializeParagraphTaggedRuns(runs);
    }

    private static List<ParagraphTaggedRun> parseParagraphTaggedRuns(String text) {
        List<ParagraphTaggedRun> runs = new ArrayList<>();
        Matcher matcher = STYLE_TAG_ID_PATTERN.matcher(text);
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
            if (!shouldAbsorbPlainRunIntoParagraphBody(runs, index, dominantStyleId)) {
                continue;
            }
            run.styleId = dominantStyleId;
        }
    }

    private static Integer findDominantParagraphBodyStyleId(String taggedText, Map<Integer, Style> styleMap) {
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

    private static boolean shouldAbsorbPlainRunIntoParagraphBody(
            List<ParagraphTaggedRun> runs,
            int index,
            int dominantStyleId
    ) {
        ParagraphTaggedRun run = runs.get(index);
        if (run == null || run.styleId != null) {
            return false;
        }
        return scoreParagraphBodyText(run.content) >= MIN_PARAGRAPH_BODY_RUN_SCORE;
    }

    private static Integer findAdjacentStyledRunStyleId(List<ParagraphTaggedRun> runs, int startIndex, int direction) {
        for (int index = startIndex + direction; index >= 0 && index < runs.size(); index += direction) {
            ParagraphTaggedRun run = runs.get(index);
            if (run == null || run.content == null || run.content.isBlank()) {
                continue;
            }
            if (run.styleId != null) {
                return run.styleId;
            }
        }
        return null;
    }

    private static int scoreParagraphBodyText(String text) {
        if (text == null || text.isBlank() || containsDigit(text) || containsDecorativeGlyph(text)) {
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
                    serialized.append("</s").append(activeStyleId).append(">");
                }
                if (run.styleId != null) {
                    serialized.append("<s").append(run.styleId).append(">");
                }
                activeStyleId = run.styleId;
            }
            serialized.append(run.content);
        }

        if (activeStyleId != null) {
            serialized.append("</s").append(activeStyleId).append(">");
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
        return STYLE_TAG_ID_PATTERN.matcher(candidate).matches() ? endIndex : -1;
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

    private static String describeParagraphQualityIssue(
            TooltipParagraphBlock block,
            String translatedTemplate
    ) {
        if (!shouldApplyChineseParagraphQualityHeuristics() || translatedTemplate == null || translatedTemplate.isBlank()) {
            return null;
        }

        String normalized = normalizeTooltipText(translatedTemplate);
        if (normalized.isEmpty() || !containsCjk(normalized)) {
            return null;
        }

        String connector = findStandaloneEnglishConnector(normalized);
        if (connector != null) {
            return "Rendered paragraph still contains standalone English connector: " + connector;
        }

        String styleCoverageIssue = describeParagraphStyleCoverageIssue(block, translatedTemplate);
        if (styleCoverageIssue != null) {
            return styleCoverageIssue;
        }

        int sourceLetterCount = 0;
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }
            sourceLetterCount += countAsciiLetters(preparedLine.sourceLine().getString());
        }

        int translatedCjkCount = countCjk(normalized);
        if (sourceLetterCount >= 18 && translatedCjkCount > 0 && translatedCjkCount * 4 < sourceLetterCount) {
            return "Rendered paragraph looks too short for Chinese output (sourceLetters="
                    + sourceLetterCount
                    + ", translatedCjk="
                    + translatedCjkCount
                    + ").";
        }
        return null;
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

        return "Rendered paragraph dropped significant visual style(s). missing="
                + summarizeParagraphVisualStyles(missingStyles)
                + " source="
                + summarizeParagraphVisualStyles(sourceScores.keySet())
                + " translated="
                + summarizeParagraphVisualStyles(translatedScores.keySet());
    }

    private static boolean shouldApplyChineseParagraphQualityHeuristics() {
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (config == null || config.target_language == null) {
            return false;
        }

        String language = config.target_language.toLowerCase(Locale.ROOT);
        return language.contains("chinese") || language.contains("中文") || language.startsWith("zh");
    }

    private static boolean containsStandaloneEnglishConnector(String text) {
        return text != null && ENGLISH_CONNECTOR_PATTERN.matcher(text).find();
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
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
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

            if (Character.isWhitespace(codePoint) || containsDecorativeGlyph(Character.toString(codePoint))) {
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

    private static void registerForceRefreshCompatBypass(Iterable<String> translationTemplateKeys) {
        if (translationTemplateKeys == null) {
            return;
        }

        cleanupForceRefreshCompatBypassState();
        long expiresAtMillis = System.currentTimeMillis() + FORCE_REFRESH_COMPAT_BYPASS_MILLIS;
        for (String translationTemplateKey : translationTemplateKeys) {
            if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
                continue;
            }
            forceRefreshCompatBypassUntilByKey.put(translationTemplateKey, expiresAtMillis);
        }
    }

    private static boolean shouldBypassCompatibilityFallback(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return false;
        }

        Long expiresAtMillis = forceRefreshCompatBypassUntilByKey.get(translationTemplateKey);
        if (expiresAtMillis == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (expiresAtMillis <= now) {
            forceRefreshCompatBypassUntilByKey.remove(translationTemplateKey, expiresAtMillis);
            return false;
        }
        return true;
    }

    private static void clearForceRefreshCompatBypass(String translationTemplateKey) {
        if (translationTemplateKey == null || translationTemplateKey.isBlank()) {
            return;
        }
        forceRefreshCompatBypassUntilByKey.remove(translationTemplateKey);
    }

    private static void cleanupForceRefreshCompatBypassState() {
        if (forceRefreshCompatBypassUntilByKey.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (var entry : forceRefreshCompatBypassUntilByKey.entrySet()) {
            Long expiresAtMillis = entry.getValue();
            if (expiresAtMillis == null || expiresAtMillis <= now) {
                forceRefreshCompatBypassUntilByKey.remove(entry.getKey(), expiresAtMillis);
            }
        }

        if (forceRefreshCompatBypassUntilByKey.size() > FORCE_REFRESH_COMPAT_BYPASS_STATE_LIMIT) {
            forceRefreshCompatBypassUntilByKey.clear();
        }
    }

    static String extractTemplateKeyForLine(Text line, boolean useTagStylePreservation) {
        return extractTemplateKey(line, useTagStylePreservation);
    }

    private static String extractTemplateKey(Text line, boolean useTagStylePreservation) {
        return prepareTemplate(line, useTagStylePreservation).translationTemplateKey();
    }

    private static PreparedTooltipTemplate prepareTemplate(Text line, boolean useTagStylePreservation) {
        boolean resolvedUseTagStylePreservation = shouldUseTagStylePreservation(line, useTagStylePreservation);
        StylePreserver.ExtractionResult styleResult = resolvedUseTagStylePreservation
                ? StylePreserver.extractAndMarkWithTags(line)
                : StylePreserver.extractAndMark(line);
        TemplateProcessor.TemplateExtractionResult templateResult = TemplateProcessor.extract(styleResult.markedText);
        String unicodeTemplate = templateResult.template();
        TemplateProcessor.DecorativeGlyphExtractionResult glyphResult = resolvedUseTagStylePreservation
                ? TemplateProcessor.extractDecorativeGlyphTags(
                unicodeTemplate,
                styleId -> {
                    net.minecraft.text.Style style = styleResult.styleMap.get(styleId);
                    return style != null && style.getFont() != null;
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

    private static ResolvedTemplateLookup resolveLookup(PreparedTooltipTemplate preparedTemplate) {
        long resolveStartedAtNanos = System.nanoTime();
        ItemTemplateCache cache = ItemTemplateCache.getInstance();
        CachedTranslationFormat currentFormat = preparedTemplate.useTagStylePreservation()
                ? CachedTranslationFormat.TAGGED
                : CachedTranslationFormat.LEGACY;
        boolean invalidCurrentTranslation = false;
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;

        ItemTemplateCache.LookupResult currentLookup = cache.peek(preparedTemplate.translationTemplateKey());
        DecodedStoredTranslation decodedCurrentTranslation = decodeStoredTranslation(currentLookup.translation(), currentFormat);
        if (currentLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED
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
        } else if (currentLookup.status() == ItemTemplateCache.TranslationStatus.TRANSLATED) {
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
                cache.forceRefresh(List.of(preparedTemplate.translationTemplateKey()));
                registerForceRefreshCompatBypass(List.of(preparedTemplate.translationTemplateKey()));
                return new ResolvedTemplateLookup(
                        new ItemTemplateCache.LookupResult(ItemTemplateCache.TranslationStatus.PENDING, "", null),
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
            ItemTemplateCache.LookupResult compatibilityLookup = cache.peek(compatibilityKey.key());
            if (compatibilityLookup.status() != ItemTemplateCache.TranslationStatus.TRANSLATED) {
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
            cache.forceRefresh(List.of(preparedTemplate.translationTemplateKey()));
            return new ResolvedTemplateLookup(
                    new ItemTemplateCache.LookupResult(ItemTemplateCache.TranslationStatus.PENDING, "", null),
                    currentFormat,
                    null
            );
        }

        return new ResolvedTemplateLookup(cache.lookupOrQueue(preparedTemplate.translationTemplateKey()), currentFormat, null);
    }

    private static ItemTemplateCache.LookupResult translatedLookup(String translation) {
        return new ItemTemplateCache.LookupResult(
                ItemTemplateCache.TranslationStatus.TRANSLATED,
                translation,
                null
        );
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

    private static boolean containsNumericPlaceholder(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("{d1}") || text.matches(".*\\{d\\d+}.*");
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

    private static String formatDevDurationMillis(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0);
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
                truncateForLog(block.paragraphTemplate() == null ? "" : block.paragraphTemplate().translationTemplateKey(), 220),
                truncateForLog(summarizeParagraphSource(block), 220),
                truncateForLog(normalizedTemplate, 220),
                truncateForLog(renderedText, 220),
                wrappedLines == null ? 0 : wrappedLines.size(),
                truncateForLog(summarizeWrappedLines(wrappedLines), 220),
                truncateForLog(detail, 220)
        );
    }

    private static void logParagraphStyleMapIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            TooltipParagraphBlock block,
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
                "[TooltipDev:{}:style-map] lines={}-{} styleCount={} usedStyleIds={} styles=\"{}\" tagged=\"{}\"",
                source,
                block.startLineIndex() + 1,
                block.endLineIndex() + 1,
                styleMap == null ? 0 : styleMap.size(),
                summarizeStyleIdSet(usedStyleIds),
                truncateForLog(summarizeStyleMapForLog(styleMap, usedStyleIds), 700),
                truncateForLog(taggedText, 320)
        );
    }

    private static LinkedHashSet<Integer> collectUsedStyleIdsFromTaggedText(String taggedText) {
        LinkedHashSet<Integer> usedStyleIds = new LinkedHashSet<>();
        if (taggedText == null || taggedText.isBlank()) {
            return usedStyleIds;
        }

        Matcher matcher = STYLE_TAG_ID_PATTERN.matcher(taggedText);
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

    private static int acquireCacheMigrationLogSlot(
            String phase,
            CachedTranslationFormat compatibilityFormat,
            String newKey,
            String compatibilityKey
    ) {
        if ("promote".equals(phase)) {
            return 0;
        }

        if (cacheMigrationLogThrottle.size() > CACHE_MIGRATION_LOG_THROTTLE_STATE_LIMIT) {
            cacheMigrationLogThrottle.clear();
        }

        CacheMigrationLogKey logKey = new CacheMigrationLogKey(phase, compatibilityFormat, newKey, compatibilityKey);
        CacheMigrationLogThrottleState state = cacheMigrationLogThrottle.computeIfAbsent(
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

    private static String truncateForLog(String value, int maxLength) {
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

    private static String summarizeParagraphSource(TooltipParagraphBlock block) {
        if (block == null || block.preparedLines() == null || block.preparedLines().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (PreparedTooltipTemplate preparedLine : block.preparedLines()) {
            if (preparedLine == null || preparedLine.sourceLine() == null) {
                continue;
            }

            String raw = normalizeTooltipText(preparedLine.sourceLine().getString());
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

            String raw = normalizeTooltipText(wrappedLine.getString());
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

    private static String buildLegacyCompatibilityKey(Text line) {
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
            if (node.style() != null && node.style().getFont() != null) {
                return true;
            }

            String extracted = node.extractString();
            if (containsDecorativeGlyph(extracted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDecorativeGlyph(String value) {
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

    private static String stripDecorativeGlyphsForHeuristics(String raw) {
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

    private static boolean isDecorativeGlyphCodePoint(int codePoint) {
        int unicodeType = Character.getType(codePoint);
        return unicodeType == Character.PRIVATE_USE
                || unicodeType == Character.UNASSIGNED
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    private static int computeTooltipSignature(Set<String> keys) {
        int hash = 1;
        for (String key : keys) {
            hash = 31 * hash + key.hashCode();
        }
        return 31 * hash + keys.size();
    }
}
