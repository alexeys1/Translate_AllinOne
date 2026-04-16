package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.cedarxuesong.translate_allinone.utils.textmatcher.ContentMatcher;
import com.cedarxuesong.translate_allinone.utils.textmatcher.FlatNode;
import com.cedarxuesong.translate_allinone.utils.textmatcher.NodePredicateBuilder;
import com.cedarxuesong.translate_allinone.utils.textmatcher.TextMatchResult;
import com.cedarxuesong.translate_allinone.utils.textmatcher.TextPattern;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class TooltipTextMatcherSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("Translate_AllinOne/TooltipTextMatcherSupport");
    private static final long DEV_LOG_REPEAT_WINDOW_MILLIS = 1200L;
    private static final Pattern NAMESPACED_IDENTIFIER_PATTERN = Pattern.compile("^\\[?#?[A-Za-z0-9_.-]+:[A-Za-z0-9_/.-]+\\]?$");
    private static final Pattern GENERIC_IDENTIFIER_PATTERN = Pattern.compile("^(?=.*[A-Za-z])[A-Za-z0-9]+(?:[._/][A-Za-z0-9]+)+$");
    private static final Pattern BARE_INTERNAL_TOKEN_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{9,}$");
    private static final TextPattern TEXTUAL_CONTENT_PATTERN = TextPattern.builder()
            .either(either -> {
                either.one(
                        "translatable-default-font",
                        ContentMatcher.translatable(),
                        styleBlock(style -> style.defaultFont())
                );
                either.one(
                        "translatable-styled",
                        ContentMatcher.translatable(),
                        styleBlock(style -> style.not(inner -> inner.defaultFont()))
                );
                either.one(
                        "plain-letter-default-font",
                        ContentMatcher.plainText(),
                        styleBlock(style -> style.defaultFont().node(TooltipTextMatcherSupport::hasLetterContentNode))
                );
                either.one(
                        "plain-letter-styled",
                        ContentMatcher.plainText(),
                        styleBlock(style -> style.not(inner -> inner.defaultFont()).node(TooltipTextMatcherSupport::hasLetterContentNode))
                );
            })
            .build();
    private static final TextPattern NON_TRANSLATABLE_NODE_PATTERN = TextPattern.builder()
            .either(either -> {
                either.oneOrMore(
                        "decorative-only",
                        ContentMatcher.any(),
                        styleBlock(style -> style.node(TooltipTextMatcherSupport::isDecorativeNode))
                );
                either.oneOrMore(
                        "numeric-symbolic-only",
                        ContentMatcher.any(),
                        styleBlock(style -> style.node(TooltipTextMatcherSupport::isNumericOrSymbolicNode))
                );
            })
            .build();
    private static final Map<String, DevTooltipLogState> DEV_TOOLTIP_LOG_STATE_BY_SOURCE = new ConcurrentHashMap<>();

    private TooltipTextMatcherSupport() {
    }

    public static boolean shouldTranslateTooltipLine(Text line, boolean isFirstContentLine, ItemTranslateConfig config) {
        return evaluateTooltipLine(line, isFirstContentLine, config).shouldTranslate();
    }

    public static TooltipLineDecision evaluateTooltipLine(Text line, boolean isFirstContentLine, ItemTranslateConfig config) {
        if (line == null || config == null || TooltipTranslationSupport.isInternalGeneratedLine(line)) {
            return new TooltipLineDecision(
                    TooltipLineKind.INTERNAL_OR_NULL,
                    false,
                    false,
                    isFirstContentLine,
                    "",
                    "Line is null or generated internally",
                    "",
                    ""
            );
        }

        String rawText = line.getString();
        if (rawText == null || rawText.trim().isEmpty()) {
            return new TooltipLineDecision(
                    TooltipLineKind.EMPTY,
                    false,
                    false,
                    isFirstContentLine,
                    "",
                    "Line is blank after string extraction",
                    "",
                    ""
            );
        }

        boolean enabledForSlot = isFirstContentLine
                ? config.enabled_translate_item_custom_name
                : config.enabled_translate_item_lore;
        if (!enabledForSlot) {
            return new TooltipLineDecision(
                    TooltipLineKind.SLOT_DISABLED,
                    false,
                    false,
                    isFirstContentLine,
                    rawText,
                    "Translation is disabled for this tooltip slot",
                    "",
                    ""
            );
        }

        boolean wynnCompatibilityEnabled = config.wynn_item_compatibility;

        if (!wynnCompatibilityEnabled && isNamespacedIdentifier(rawText)) {
            return new TooltipLineDecision(
                    TooltipLineKind.NAMESPACE_IDENTIFIER,
                    false,
                    true,
                    isFirstContentLine,
                    rawText,
                    "Full line looks like a namespaced identifier",
                    "identifier-guard",
                    "namespaced"
            );
        }

        if (!wynnCompatibilityEnabled && isGenericIdentifier(rawText)) {
            return new TooltipLineDecision(
                    TooltipLineKind.IDENTIFIER_LIKE,
                    false,
                    true,
                    isFirstContentLine,
                    rawText,
                    "Full line looks like an internal identifier or path",
                    "identifier-guard",
                    "generic"
            );
        }

        if (!wynnCompatibilityEnabled && isBareInternalIdentifier(rawText)) {
            return new TooltipLineDecision(
                    TooltipLineKind.IDENTIFIER_LIKE,
                    false,
                    true,
                    isFirstContentLine,
                    rawText,
                    "Full line looks like a bare internal identifier token",
                    "identifier-guard",
                    "bare-token"
            );
        }

        TextMatchResult textualContentMatch = TEXTUAL_CONTENT_PATTERN.find(line);
        if (textualContentMatch.success()) {
            String firstBranch = firstMatchedBranch(textualContentMatch);
            if (firstBranch.startsWith("translatable-")) {
                return new TooltipLineDecision(
                        TooltipLineKind.TRANSLATABLE_CONTENT,
                        true,
                        true,
                        isFirstContentLine,
                        rawText,
                        "Matched at least one translatable text node",
                        "textual-content",
                        summarizeMatchedBranches(textualContentMatch)
                );
            }
            return new TooltipLineDecision(
                    TooltipLineKind.HUMAN_READABLE_TEXT,
                    true,
                    true,
                    isFirstContentLine,
                    rawText,
                    wynnCompatibilityEnabled
                            ? "Matched letter-bearing plain text node under Wynn compatibility"
                            : "Matched human-readable plain text node",
                    "textual-content",
                    summarizeMatchedBranches(textualContentMatch)
            );
        }

        TextMatchResult nonTranslatableNodeMatch = NON_TRANSLATABLE_NODE_PATTERN.match(line);
        if (nonTranslatableNodeMatch.success()) {
            String firstBranch = firstMatchedBranch(nonTranslatableNodeMatch);
            if ("decorative-only".equals(firstBranch)) {
                return new TooltipLineDecision(
                        TooltipLineKind.DECORATIVE_ONLY,
                        false,
                        true,
                        isFirstContentLine,
                        rawText,
                        "Matched only decorative glyph or punctuation nodes",
                        "non-translatable-node",
                        summarizeMatchedBranches(nonTranslatableNodeMatch)
                );
            }
            return new TooltipLineDecision(
                    TooltipLineKind.NUMERIC_OR_SYMBOLIC_ONLY,
                    false,
                    true,
                    isFirstContentLine,
                    rawText,
                    "Matched only numeric or symbolic nodes",
                    "non-translatable-node",
                    summarizeMatchedBranches(nonTranslatableNodeMatch)
            );
        }

        return new TooltipLineDecision(
                TooltipLineKind.UNCLASSIFIED,
                false,
                true,
                isFirstContentLine,
                rawText,
                "No text-matcher rule classified this line as translatable",
                "",
                ""
        );
    }

    public static boolean hasMeaningfulContent(Text line) {
        ItemTranslateConfig config = new ItemTranslateConfig();
        config.enabled_translate_item_custom_name = true;
        TooltipLineDecision decision = evaluateTooltipLine(line, true, config);
        return decision.kind() == TooltipLineKind.TRANSLATABLE_CONTENT
                || decision.kind() == TooltipLineKind.HUMAN_READABLE_TEXT;
    }

    public static boolean isDevEnabled(ItemTranslateConfig config) {
        return config != null
                && config.debug != null
                && config.debug.enabled;
    }

    public static boolean isDevModeEnabled(ItemTranslateConfig config) {
        return isDevEnabled(config);
    }

    public static boolean shouldLogTooltipFilterResult(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_tooltip_filter_result;
    }

    public static boolean shouldLogTooltipNodeSummary(ItemTranslateConfig config) {
        return shouldLogTooltipFilterResult(config)
                && config.debug.log_tooltip_node_summary;
    }

    public static boolean shouldLogTooltipTiming(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_tooltip_timing;
    }

    public static boolean shouldLogTooltipParagraphResult(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_tooltip_paragraph_result;
    }

    public static boolean shouldLogTooltipStyleMap(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_tooltip_style_map;
    }

    public static boolean shouldLogItemBatchTiming(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_item_batch_timing;
    }

    public static boolean shouldLogItemCacheMigration(ItemTranslateConfig config) {
        return isDevEnabled(config)
                && config.debug.log_cache_migration;
    }

    private static boolean shouldLogAnyTooltipDev(ItemTranslateConfig config) {
        return shouldLogTooltipFilterResult(config)
                || shouldLogTooltipTiming(config)
                || shouldLogTooltipParagraphResult(config)
                || shouldLogTooltipStyleMap(config);
    }

    public static boolean beginTooltipDevPass(ItemTranslateConfig config, String source, List<Text> tooltipLines) {
        if (!shouldLogAnyTooltipDev(config) || source == null || source.isBlank() || tooltipLines == null || tooltipLines.isEmpty()) {
            return false;
        }

        int signature = computeTooltipSignature(tooltipLines);
        long nowMillis = System.currentTimeMillis();
        DevTooltipLogState previous = DEV_TOOLTIP_LOG_STATE_BY_SOURCE.get(source);
        if (previous != null
                && previous.signature() == signature
                && nowMillis - previous.loggedAtMillis() < DEV_LOG_REPEAT_WINDOW_MILLIS) {
            return false;
        }

        DEV_TOOLTIP_LOG_STATE_BY_SOURCE.put(source, new DevTooltipLogState(signature, nowMillis));
        return true;
    }

    public static void logLineDecisionIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            int lineIndex,
            TooltipLineDecision decision,
            Text line
    ) {
        if (!emitDevLog || !shouldLogTooltipFilterResult(config) || decision == null) {
            return;
        }

        if (shouldLogTooltipNodeSummary(config)) {
            LOGGER.info(
                    "[TooltipDev:{}] line={} kind={} matcher={} branches={} translate={} firstContentLine={} reason={} text=\"{}\" nodes={}",
                    source,
                    lineIndex + 1,
                    decision.kind(),
                    emptyIfBlank(decision.matcherRule()),
                    emptyIfBlank(decision.matcherBranches()),
                    decision.shouldTranslate(),
                    decision.firstContentLine(),
                    decision.reason(),
                    truncate(decision.rawText(), 180),
                    summarizeNodes(line)
            );
            return;
        }

        LOGGER.info(
                "[TooltipDev:{}] line={} kind={} matcher={} branches={} translate={} firstContentLine={} reason={} text=\"{}\"",
                source,
                lineIndex + 1,
                decision.kind(),
                emptyIfBlank(decision.matcherRule()),
                emptyIfBlank(decision.matcherBranches()),
                decision.shouldTranslate(),
                decision.firstContentLine(),
                decision.reason(),
                truncate(decision.rawText(), 180)
        );
    }

    public static void logLineTranslationIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            int lineIndex,
            TooltipTranslationSupport.TooltipLineResult lineResult,
            long startedAtNanos
    ) {
        if (!emitDevLog || !shouldLogTooltipTiming(config) || lineResult == null) {
            return;
        }

        LOGGER.info(
                "[TooltipDev:{}] line={} translateLine={}ms pending={} missingKeyIssue={} result=\"{}\"",
                source,
                lineIndex + 1,
                formatDurationMillis(startedAtNanos),
                lineResult.pending(),
                lineResult.missingKeyIssue(),
                truncate(lineResult.translatedLine() == null ? "" : lineResult.translatedLine().getString(), 180)
        );
    }

    public static void logTooltipPassIfDev(
            ItemTranslateConfig config,
            boolean emitDevLog,
            String source,
            int visibleLineCount,
            int translatedLineCount,
            long startedAtNanos
    ) {
        if (!emitDevLog || !shouldLogTooltipTiming(config)) {
            return;
        }

        LOGGER.info(
                "[TooltipDev:{}] tooltipPass={}ms translatedLines={}/{}",
                source,
                formatDurationMillis(startedAtNanos),
                translatedLineCount,
                visibleLineCount
        );
    }

    public enum TooltipLineKind {
        INTERNAL_OR_NULL,
        EMPTY,
        SLOT_DISABLED,
        TRANSLATABLE_CONTENT,
        HUMAN_READABLE_TEXT,
        NAMESPACE_IDENTIFIER,
        IDENTIFIER_LIKE,
        DECORATIVE_ONLY,
        NUMERIC_OR_SYMBOLIC_ONLY,
        UNCLASSIFIED
    }

    public record TooltipLineDecision(
            TooltipLineKind kind,
            boolean shouldTranslate,
            boolean enabledForSlot,
            boolean firstContentLine,
            String rawText,
            String reason,
            String matcherRule,
            String matcherBranches
    ) {
    }

    private record DevTooltipLogState(int signature, long loggedAtMillis) {
    }

    private static boolean hasLetterContentNode(FlatNode node) {
        TextContent content = node.content();
        return content instanceof PlainTextContent plainTextContent
                && containsLetter(plainTextContent.string());
    }

    private static String firstMatchedBranch(TextMatchResult result) {
        if (result == null || result.matchedBranches() == null || result.matchedBranches().isEmpty()) {
            return "";
        }
        return result.matchedBranches().get(0);
    }

    private static String summarizeMatchedBranches(TextMatchResult result) {
        if (result == null || result.matchedBranches() == null || result.matchedBranches().isEmpty()) {
            return "";
        }
        return String.join(">", result.matchedBranches());
    }

    private static boolean containsLetter(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }

        return false;
    }

    private static boolean isNamespacedIdentifier(String rawText) {
        return rawText != null && NAMESPACED_IDENTIFIER_PATTERN.matcher(rawText.trim()).matches();
    }

    private static boolean isGenericIdentifier(String rawText) {
        if (rawText == null) {
            return false;
        }

        String normalized = stripIdentifierWrapper(rawText);
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0 || containsWhitespace(normalized)) {
            return false;
        }

        return GENERIC_IDENTIFIER_PATTERN.matcher(normalized).matches();
    }

    private static boolean isBareInternalIdentifier(String rawText) {
        if (rawText == null) {
            return false;
        }

        String normalized = stripIdentifierWrapper(rawText);
        if (normalized.isEmpty() || containsWhitespace(normalized)) {
            return false;
        }
        if (!BARE_INTERNAL_TOKEN_PATTERN.matcher(normalized).matches()) {
            return false;
        }

        return normalized.startsWith("skyblock")
                || normalized.startsWith("minecraft")
                || normalized.startsWith("wynntils")
                || normalized.startsWith("wynn")
                || normalized.endsWith("item")
                || normalized.endsWith("entity")
                || normalized.endsWith("recipe");
    }

    private static String stripIdentifierWrapper(String rawText) {
        if (rawText == null) {
            return "";
        }

        String normalized = rawText.trim();
        if (normalized.length() >= 2 && normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean containsWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumericOrSymbolicNode(FlatNode node) {
        TextContent content = node.content();
        if (content instanceof TranslatableTextContent) {
            return false;
        }

        String extracted = node.extractString();
        if (extracted == null || extracted.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < extracted.length(); ) {
            int codePoint = extracted.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return false;
            }
            if (!(Character.isWhitespace(codePoint)
                    || Character.isDigit(codePoint)
                    || isSymbolicCodePoint(codePoint)
                    || isPrivateUseCodePoint(codePoint))) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }

        return true;
    }

    private static boolean isDecorativeNode(FlatNode node) {
        TextContent content = node.content();
        if (content instanceof TranslatableTextContent) {
            return false;
        }

        String extracted = node.extractString();
        if (extracted == null || extracted.isEmpty()) {
            return false;
        }

        boolean sawNonWhitespace = false;
        for (int offset = 0; offset < extracted.length(); ) {
            int codePoint = extracted.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            sawNonWhitespace = true;
            if (Character.isLetterOrDigit(codePoint) || !isDecorativeCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }

        return sawNonWhitespace;
    }

    private static boolean isDecorativeCodePoint(int codePoint) {
        return isPrivateUseCodePoint(codePoint) || isSymbolicCodePoint(codePoint);
    }

    private static boolean isPrivateUseCodePoint(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE;
    }

    private static boolean isSymbolicCodePoint(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static int computeTooltipSignature(List<Text> tooltipLines) {
        int hash = 1;
        for (Text line : tooltipLines) {
            String value = line == null ? "" : line.getString();
            hash = 31 * hash + value.hashCode();
        }
        return 31 * hash + tooltipLines.size();
    }

    private static String summarizeNodes(Text line) {
        if (line == null) {
            return "[]";
        }

        List<FlatNode> nodes = FlatNode.compact(FlatNode.flatten(line));
        if (nodes.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            FlatNode node = nodes.get(i);
            TextContent content = node.content();
            if (content instanceof TranslatableTextContent translatableTextContent) {
                builder.append("translatable(")
                        .append(translatableTextContent.getKey())
                        .append(")");
            } else if (content instanceof PlainTextContent plainTextContent) {
                builder.append("plain(\"")
                        .append(truncate(plainTextContent.string(), 48))
                        .append("\")");
            } else {
                builder.append(content == null ? "null" : content.getClass().getSimpleName())
                        .append("(\"")
                        .append(truncate(FlatNode.extractString(content), 48))
                        .append("\")");
            }
        }
        return builder.append(']').toString();
    }

    private static String truncate(String value, int maxLength) {
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

    private static String emptyIfBlank(String value) {
        return value == null ? "" : value;
    }

    private static Consumer<NodePredicateBuilder> styleBlock(Consumer<NodePredicateBuilder> block) {
        return block;
    }

    private static String formatDurationMillis(long startedAtNanos) {
        double elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000.0;
        return String.format(Locale.ROOT, "%.2f", elapsedMillis);
    }
}
