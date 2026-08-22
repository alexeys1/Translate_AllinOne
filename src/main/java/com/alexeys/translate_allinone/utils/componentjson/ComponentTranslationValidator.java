package com.alexeys.translate_allinone.utils.componentjson;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComponentTranslationValidator {
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("</?s(\\d+)>");
    private static final Pattern LEGACY_FORMATTING_CODE_PATTERN = Pattern.compile("\\x{00A7}[0-9A-FK-ORa-fk-or]");
    private static final Pattern INLINE_HARD_TOKEN_PATTERN = Pattern.compile("\\{(?:value|glyph)\\d+}");
    private static final Pattern INLINE_ANCHOR_TOKEN_PATTERN = Pattern.compile("\\{accent\\d+\\.(?:begin|end)}");
    private static final Pattern TEMPLATE_MARKER_PATTERN = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_.:-]*}");
    private static final Pattern ANGLE_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final int MAX_PARAGRAPH_STYLE_SPLITS_PER_SOURCE_SPAN = 4;
    private final ComponentJsonLimits limits;

    public ComponentTranslationValidator() {
        this(ComponentJsonLimits.DEFAULT);
    }

    public ComponentTranslationValidator(ComponentJsonLimits limits) {
        this.limits = limits == null ? ComponentJsonLimits.DEFAULT : limits;
    }

    public ComponentTranslationResponse validate(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        if (document == null || response == null) {
            throw validationError(
                    "Document and response are required: expected=document+response"
                            + ", actual=[document=" + (document == null ? "null" : "present")
                            + ", response=" + (response == null ? "null" : "present") + "]"
                            + ", missing=" + missingInputs(document, response)
                            + ", unexpected token=[]"
            );
        }
        if (!document.protocol().equals(response.protocol())) {
            throw validationError(
                    "Response protocol mismatch: expected=" + document.protocol()
                            + ", actual=" + response.protocol()
                            + ", missing=" + (response.protocol() == null ? document.protocol() : "")
                            + ", unexpected=" + (document.protocol().equals(response.protocol()) ? "" : response.protocol())
            );
        }

        Set<String> expectedIds = new LinkedHashSet<>();
        for (ComponentTextUnit unit : document.units()) {
            expectedIds.add(unit.id());
        }
        Set<String> actualIds = new LinkedHashSet<>(response.translations().keySet());
        if (!expectedIds.equals(actualIds)) {
            throw validationError(
                    "Response translation ids mismatch: expected=" + formatSet(expectedIds)
                            + ", actual=" + formatSet(actualIds)
                            + ", missing=" + formatSet(difference(expectedIds, actualIds))
                            + ", unexpected=" + formatSet(difference(actualIds, expectedIds))
            );
        }

        ComponentTranslationPolicy policy = ComponentTranslationPolicy.forRoute(document.route());
        int totalChars = 0;
        for (ComponentTextUnit unit : document.units()) {
            String translation = response.translations().get(unit.id());
            if (translation == null) {
                throw validationError(
                        "Missing translation for " + unit.id()
                                + ": expected=STRING, actual=null, missing=" + unit.id()
                                + ", unexpected token=[]"
                );
            }
            validateUnicode(translation, unit.id());
            if (translation.length() > limits.maxTranslationChars()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Translation exceeds the per-unit length limit: " + unit.id()
                                + ": expectedChars<=" + limits.maxTranslationChars()
                                + ", actualChars=" + translation.length()
                                + ", missing=[]"
                                + ", unexpected token=oversize-translation"
                );
            }
            totalChars += translation.length();
            if (totalChars > limits.maxTotalTranslationChars()) {
                throw new ComponentJsonException(
                        ComponentJsonException.Kind.LIMIT,
                        "Translations exceed the total length limit: expectedChars<=" + limits.maxTotalTranslationChars()
                                + ", actualChars=" + totalChars
                                + ", missing=[]"
                                + ", unexpected token=oversize-total"
                );
            }

            Map<String, Integer> actualTokens = new LinkedHashMap<>(policy.protectedTokenMultiset(translation));
            for (String expectedToken : unit.protectedTokens().keySet()) {
                if (!actualTokens.containsKey(expectedToken)) {
                    int count = countOccurrences(translation, expectedToken);
                    if (count > 0) {
                        actualTokens.put(expectedToken, count);
                    }
                }
            }
            Map<String, Integer> expectedTokens = unit.protectedTokens();
            if (isTooltipRoute(document.route())) {
                expectedTokens = withoutLegacyFormattingCodes(expectedTokens);
                actualTokens = withoutLegacyFormattingCodes(actualTokens);
            }
            if (isInlineAnchorParagraph(document)) {
                validateInlineAnchorParagraph(unit, translation);
            } else if (document.route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH) {
                validateParagraphProtectedTokens(unit, expectedTokens, actualTokens);
            } else if (!expectedTokens.equals(actualTokens)) {
                throwProtectedTokenMismatch(unit.id(), expectedTokens, actualTokens);
            }
            if (!isInlineAnchorParagraph(document)) {
                validateFlatStyleTags(unit.sourceText(), translation, unit.id());
            }
            if (document.route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH
                    && !isInlineAnchorParagraph(document)) {
                validateParagraphStyleCoverage(unit.sourceText(), translation, unit.id());
            }
        }
        return response;
    }

    private static boolean isInlineAnchorParagraph(ComponentTranslationDocument document) {
        return document != null
                && document.route() == ComponentTranslationRoute.TOOLTIP_PARAGRAPH
                && "inline-anchor-v1".equals(document.semanticSettings().get("style_binding"));
    }

    private static void validateInlineAnchorParagraph(ComponentTextUnit unit, String translation) {
        Map<String, Integer> expectedHardTokens = collectTokenCounts(INLINE_HARD_TOKEN_PATTERN, unit.sourceText());
        Map<String, Integer> actualHardTokens = collectTokenCounts(INLINE_HARD_TOKEN_PATTERN, translation);
        if (!expectedHardTokens.equals(actualHardTokens)) {
            throwProtectedTokenMismatch(unit.id(), expectedHardTokens, actualHardTokens);
        }

        Set<String> knownMarkers = new LinkedHashSet<>(expectedHardTokens.keySet());
        knownMarkers.addAll(collectTokenCounts(INLINE_ANCHOR_TOKEN_PATTERN, unit.sourceText()).keySet());
        Matcher markerMatcher = TEMPLATE_MARKER_PATTERN.matcher(translation);
        while (markerMatcher.find()) {
            if (!knownMarkers.contains(markerMatcher.group())) {
                throw validationError(
                        "Inline paragraph contains an unknown template marker for " + unit.id()
                                + ": unexpected=" + markerMatcher.group()
                );
            }
        }
        String markerStripped = TEMPLATE_MARKER_PATTERN.matcher(translation).replaceAll("");
        if (markerStripped.indexOf('{') >= 0 || markerStripped.indexOf('}') >= 0) {
            throw validationError(
                    "Inline paragraph contains an unparsed template delimiter for " + unit.id()
            );
        }
        Matcher angleMatcher = ANGLE_TAG_PATTERN.matcher(translation);
        if (angleMatcher.find()) {
            throw validationError(
                    "Inline paragraph contains an unknown tag for " + unit.id()
                            + ": unexpected=" + angleMatcher.group()
            );
        }
        if (translation.indexOf('§') >= 0) {
            throw validationError(
                    "Inline paragraph contains a literal Minecraft formatting code for " + unit.id()
            );
        }
        for (int offset = 0; offset < translation.length(); ) {
            int codePoint = translation.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.PRIVATE_USE || type == Character.UNASSIGNED) {
                throw validationError(
                        "Inline paragraph contains an unextracted decorative glyph for " + unit.id()
                );
            }
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                throw validationError(
                        "Inline paragraph contains an illegal control character for " + unit.id()
                );
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static Map<String, Integer> collectTokenCounts(Pattern pattern, String text) {
        Map<String, Integer> counts = new TreeMap<>();
        if (pattern == null || text == null || text.isEmpty()) {
            return counts;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            counts.merge(matcher.group(), 1, Integer::sum);
        }
        return counts;
    }

    private static void validateParagraphProtectedTokens(
            ComponentTextUnit unit,
            Map<String, Integer> expectedTokens,
            Map<String, Integer> actualTokens
    ) {
        Map<String, Integer> expectedNonStyleTokens = withoutStyleTags(expectedTokens);
        Map<String, Integer> actualNonStyleTokens = withoutStyleTags(actualTokens);
        if (!expectedNonStyleTokens.equals(actualNonStyleTokens)) {
            throwProtectedTokenMismatch(unit.id(), expectedNonStyleTokens, actualNonStyleTokens);
        }
    }

    private static void validateParagraphStyleCoverage(String expectedValue, String actualValue, String id) {
        Map<String, Integer> expectedCounts = styleTagSummary(expectedValue);
        Map<String, Integer> actualCounts = styleTagSummary(actualValue);
        Set<String> expectedIds = expectedCounts.keySet();
        Set<String> actualIds = actualCounts.keySet();
        if (!expectedIds.equals(actualIds)) {
            throw validationError(
                    "Paragraph style ids changed for " + id
                            + ": expected=" + formatSet(expectedIds)
                            + ", actual=" + formatSet(actualIds)
                            + ", missing=" + formatSet(difference(expectedIds, actualIds))
                            + ", unexpected=" + formatSet(difference(actualIds, expectedIds))
            );
        }

        for (String styleId : expectedIds) {
            int expectedSpanCount = Math.max(1, expectedCounts.getOrDefault(styleId, 0));
            int actualSpanCount = actualCounts.getOrDefault(styleId, 0);
            int maxAllowed = expectedSpanCount * MAX_PARAGRAPH_STYLE_SPLITS_PER_SOURCE_SPAN;
            if (actualSpanCount > maxAllowed) {
                throw validationError(
                        "Paragraph style id was split too many times for " + id
                                + ": style=" + styleId
                                + ", expectedSpans<=" + maxAllowed
                                + ", actualSpans=" + actualSpanCount
                                + ", missing=[]"
                                + ", unexpected=" + styleId
                );
            }
        }
    }

    private static Map<String, Integer> withoutStyleTags(Map<String, Integer> tokens) {
        Map<String, Integer> result = new TreeMap<>();
        if (tokens == null || tokens.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
            if (!isStyleTag(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Integer> withoutLegacyFormattingCodes(Map<String, Integer> tokens) {
        Map<String, Integer> result = new TreeMap<>();
        if (tokens == null || tokens.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : tokens.entrySet()) {
            if (entry.getKey() == null || !LEGACY_FORMATTING_CODE_PATTERN.matcher(entry.getKey()).matches()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static boolean isTooltipRoute(ComponentTranslationRoute route) {
        return route == ComponentTranslationRoute.TOOLTIP_LINE
                || route == ComponentTranslationRoute.TOOLTIP_STRUCTURED
                || route == ComponentTranslationRoute.TOOLTIP_PARAGRAPH;
    }

    private static boolean isStyleTag(String token) {
        return token != null && STYLE_TAG_PATTERN.matcher(token).matches();
    }

    private static Map<String, Integer> styleTagSummary(String value) {
        Map<String, Integer> counts = new TreeMap<>();
        if (value == null || value.isEmpty()) {
            return counts;
        }
        Matcher matcher = STYLE_TAG_PATTERN.matcher(value);
        while (matcher.find()) {
            if (value.charAt(matcher.start() + 1) != '/') {
                counts.merge(matcher.group(1), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void validateFlatStyleTags(String expectedValue, String actualValue, String id) {
        List<String> expectedTags = extractStyleTags(expectedValue);
        List<String> actualTags = extractStyleTags(actualValue);
        Matcher matcher = STYLE_TAG_PATTERN.matcher(actualValue);
        String activeStyleId = null;
        while (matcher.find()) {
            String styleId = matcher.group(1);
            boolean closing = actualValue.charAt(matcher.start() + 1) == '/';
            if (!closing) {
                if (activeStyleId != null) {
                    throw styleTagError(id, expectedTags, actualTags, "nested style tags are not supported");
                }
                activeStyleId = styleId;
                continue;
            }
            if (activeStyleId == null || !styleId.equals(activeStyleId)) {
                throw styleTagError(id, expectedTags, actualTags, "style tag close does not match the active style");
            }
            activeStyleId = null;
        }
        if (activeStyleId != null) {
            throw styleTagError(id, expectedTags, actualTags, "style tag is not closed");
        }
    }

    private static List<String> extractStyleTags(String value) {
        List<String> tags = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return tags;
        }
        Matcher matcher = STYLE_TAG_PATTERN.matcher(value);
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    private static ComponentJsonException styleTagError(
            String id,
            List<String> expected,
            List<String> actual,
            String detail
    ) {
        return validationError(
                "Style tags are invalid in translation " + id
                        + ": " + detail
                        + ", expected=" + expected
                        + ", actual=" + actual
                        + ", missing=" + tokenDifference(expected, actual)
                        + ", unexpected token=" + tokenDifference(actual, expected)
        );
    }

    private static List<String> tokenDifference(List<String> left, List<String> right) {
        List<String> remaining = new ArrayList<>(right);
        List<String> difference = new ArrayList<>();
        for (String token : left) {
            if (!remaining.remove(token)) {
                difference.add(token);
            }
        }
        return difference;
    }

    private static int countOccurrences(String text, String token) {
        if (token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static List<String> missingInputs(
            ComponentTranslationDocument document,
            ComponentTranslationResponse response
    ) {
        List<String> missing = new ArrayList<>();
        if (document == null) {
            missing.add("document");
        }
        if (response == null) {
            missing.add("response");
        }
        return missing;
    }

    private static Map<String, Integer> multisetDifference(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            int remaining = entry.getValue() - right.getOrDefault(entry.getKey(), 0);
            if (remaining > 0) {
                result.put(entry.getKey(), remaining);
            }
        }
        return result;
    }

    private static String formatSet(Set<String> values) {
        return values == null || values.isEmpty() ? "[]" : values.toString();
    }

    private static String formatMap(Map<String, Integer> values) {
        return values == null || values.isEmpty() ? "{}" : new TreeMap<>(values).toString();
    }

    private static void throwProtectedTokenMismatch(
            String id,
            Map<String, Integer> expected,
            Map<String, Integer> actual
    ) {
        throw validationError(
                "Protected tokens changed for " + id
                        + ": expected=" + formatMap(expected)
                        + ", actual=" + formatMap(actual)
                        + ", missing=" + formatMap(multisetDifference(expected, actual))
                        + ", unexpected=" + formatMap(multisetDifference(actual, expected))
        );
    }

    private static void validateUnicode(String value, String id) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw validationError(
                            "Invalid Unicode in translation " + id
                                    + ": expected=valid UTF-16, actual=unpaired high surrogate"
                                    + ", missing=[]"
                                    + ", unexpected token=surrogate"
                    );
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw validationError(
                        "Invalid Unicode in translation " + id
                                + ": expected=valid UTF-16, actual=unpaired low surrogate"
                                + ", missing=[]"
                                + ", unexpected token=surrogate"
                );
            }
        }
    }

    private static ComponentJsonException validationError(String message) {
        return new ComponentJsonException(ComponentJsonException.Kind.VALIDATION, message);
    }
}
