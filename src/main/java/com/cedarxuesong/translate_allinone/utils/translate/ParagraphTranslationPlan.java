package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.text.StylePreserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

record ParagraphTranslationPlan(
        List<Text> sourceLines,
        List<StyledRun> styledRuns,
        String requestText,
        Style bodyStyle,
        Map<String, Text> hardTokens,
        List<AccentAnchor> accentAnchors,
        String stableIdentity,
        String strategy,
        int wrapWidth
) {
    static final String INLINE_ANCHOR_STRATEGY = "inline-anchor-v1";
    private static final Pattern TEMPLATE_MARKER_PATTERN = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_.:-]*}");
    private static final Pattern ANGLE_TAG_PATTERN = Pattern.compile("<[^>]+>");

    ParagraphTranslationPlan {
        sourceLines = copyComponents(sourceLines);
        styledRuns = styledRuns == null ? List.of() : List.copyOf(styledRuns);
        requestText = requestText == null ? "" : requestText;
        bodyStyle = bodyStyle == null ? Style.EMPTY : bodyStyle;
        hardTokens = copyComponentMap(hardTokens);
        accentAnchors = accentAnchors == null ? List.of() : List.copyOf(accentAnchors);
        stableIdentity = stableIdentity == null ? "" : stableIdentity;
        strategy = strategy == null || strategy.isBlank() ? INLINE_ANCHOR_STRATEGY : strategy;
    }

    List<String> hardTokenIds() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (StyledRun run : styledRuns) {
            if (run != null && (run.type() == RunType.DYNAMIC || run.type() == RunType.GLYPH)) {
                ordered.add(run.token());
            }
        }
        return List.copyOf(ordered);
    }

    List<String> accentIds() {
        return accentAnchors.stream().map(AccentAnchor::id).toList();
    }

    String hardTokenTopology() {
        List<String> topology = new ArrayList<>(hardTokens.size());
        for (StyledRun run : styledRuns) {
            if (run != null && (run.type() == RunType.DYNAMIC || run.type() == RunType.GLYPH)) {
                topology.add(run.type().name().toLowerCase(java.util.Locale.ROOT) + ':' + run.token());
            }
        }
        return String.join(",", topology);
    }

    String accentTopology() {
        return String.join(",", accentIds());
    }

    String describeHardTokenIssue(String candidate) {
        if (candidate == null) {
            return "Paragraph candidate is null.";
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String token : hardTokens.keySet()) {
            expected.put(token, 1);
        }
        Map<String, Integer> actual = new LinkedHashMap<>();
        Matcher matcher = TEMPLATE_MARKER_PATTERN.matcher(candidate);
        while (matcher.find()) {
            if (hardTokens.containsKey(matcher.group())) {
                actual.merge(matcher.group(), 1, Integer::sum);
            }
        }
        if (expected.equals(actual)) {
            return null;
        }
        return "Dynamic or decorative placeholder identity changed. expected=" + expected + ", actual=" + actual;
    }

    RenderResult renderStyled(String candidate) {
        return render(candidate, false);
    }

    RenderResult renderSafeBody(String candidate) {
        return render(candidate, true);
    }

    private RenderResult render(String candidate, boolean safeBody) {
        if (candidate == null || candidate.isBlank()) {
            return RenderResult.rejected("Paragraph candidate is empty.");
        }
        String hardTokenIssue = describeHardTokenIssue(candidate);
        if (hardTokenIssue != null) {
            return RenderResult.rejected(hardTokenIssue);
        }
        String literalIssue = describeUnsafeLiteral(candidate);
        if (literalIssue != null) {
            return RenderResult.rejected(literalIssue);
        }
        String markerStripped = TEMPLATE_MARKER_PATTERN.matcher(candidate).replaceAll("");
        if (markerStripped.indexOf('{') >= 0 || markerStripped.indexOf('}') >= 0) {
            return RenderResult.rejected("Paragraph candidate contains an unparsed template delimiter.");
        }

        Map<String, AnchorMarker> anchorMarkers = new LinkedHashMap<>();
        for (AccentAnchor anchor : accentAnchors) {
            anchorMarkers.put(anchor.beginToken(), new AnchorMarker(anchor, true));
            anchorMarkers.put(anchor.endToken(), new AnchorMarker(anchor, false));
        }

        MutableText rendered = Text.empty();
        Map<String, Integer> anchorCounts = new LinkedHashMap<>();
        AccentAnchor activeAnchor = null;
        Matcher markerMatcher = TEMPLATE_MARKER_PATTERN.matcher(candidate);
        int previousEnd = 0;
        while (markerMatcher.find()) {
            appendNaturalText(rendered, candidate.substring(previousEnd, markerMatcher.start()), activeAnchor);
            String marker = markerMatcher.group();
            Text hardToken = hardTokens.get(marker);
            if (hardToken != null) {
                rendered.append(hardToken.copy());
                previousEnd = markerMatcher.end();
                continue;
            }

            AnchorMarker anchorMarker = anchorMarkers.get(marker);
            if (anchorMarker == null) {
                return RenderResult.rejected("Paragraph candidate contains an unknown template marker: " + marker);
            }
            anchorCounts.merge(marker, 1, Integer::sum);
            if (!safeBody) {
                if (anchorMarker.begin()) {
                    if (activeAnchor != null) {
                        return RenderResult.rejected("Paragraph accent anchors are nested.");
                    }
                    activeAnchor = anchorMarker.anchor();
                } else if (!Objects.equals(activeAnchor, anchorMarker.anchor())) {
                    return RenderResult.rejected("Paragraph accent anchor close does not match the active anchor.");
                } else {
                    activeAnchor = null;
                }
            }
            previousEnd = markerMatcher.end();
        }
        appendNaturalText(rendered, candidate.substring(previousEnd), activeAnchor);

        if (!safeBody) {
            if (activeAnchor != null) {
                return RenderResult.rejected("Paragraph accent anchor is not closed: " + activeAnchor.id());
            }
            for (String marker : anchorMarkers.keySet()) {
                if (anchorCounts.getOrDefault(marker, 0) != 1) {
                    return RenderResult.rejected("Paragraph accent anchor count is invalid: " + marker);
                }
            }
        }
        if (rendered.getString().isBlank()) {
            return RenderResult.rejected("Paragraph Component reconstruction is empty.");
        }
        return RenderResult.accepted(rendered);
    }

    private void appendNaturalText(MutableText target, String text, AccentAnchor activeAnchor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Style sourceStyle = activeAnchor == null ? bodyStyle : activeAnchor.style();
        Style targetStyle = StylePreserver.sanitizeStyleForComparison(sourceStyle, true);
        target.append(Text.literal(text).setStyle(targetStyle));
    }

    private static String describeUnsafeLiteral(String candidate) {
        if (candidate.indexOf('§') >= 0) {
            return "Paragraph candidate contains a literal Minecraft formatting code.";
        }
        Matcher angleMatcher = ANGLE_TAG_PATTERN.matcher(candidate);
        if (angleMatcher.find()) {
            return "Paragraph candidate contains an unknown tag: " + angleMatcher.group();
        }
        for (int offset = 0; offset < candidate.length(); ) {
            int codePoint = candidate.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (type == Character.PRIVATE_USE || type == Character.UNASSIGNED) {
                return "Paragraph candidate contains an unextracted decorative glyph.";
            }
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                return "Paragraph candidate contains an illegal control character.";
            }
            offset += Character.charCount(codePoint);
        }
        return null;
    }

    private static List<Text> copyComponents(List<Text> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                .map(component -> (Text) (component == null ? Text.empty() : component.copy()))
                .toList();
    }

    private static Map<String, Text> copyComponentMap(Map<String, Text> components) {
        if (components == null || components.isEmpty()) {
            return Map.of();
        }
        Map<String, Text> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Text> entry : components.entrySet()) {
            copied.put(entry.getKey(), entry.getValue() == null ? Text.empty() : entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copied);
    }

    enum RunType {
        BODY,
        ACCENT,
        DYNAMIC,
        GLYPH,
        CONTROL
    }

    record StyledRun(RunType type, String sourceText, Style style, String token) {
        StyledRun {
            type = type == null ? RunType.BODY : type;
            sourceText = sourceText == null ? "" : sourceText;
            style = style == null ? Style.EMPTY : style;
            token = token == null ? "" : token;
        }
    }

    record AccentAnchor(String id, String sourceText, Style style) {
        AccentAnchor {
            if (id == null || !id.matches("accent\\d+") || sourceText == null || sourceText.isBlank()) {
                throw new IllegalArgumentException("Paragraph accent anchor is invalid.");
            }
            style = style == null ? Style.EMPTY : style;
        }

        String beginToken() {
            return "{" + id + ".begin}";
        }

        String endToken() {
            return "{" + id + ".end}";
        }
    }

    record RenderResult(Text component, String rejectionReason) {
        static RenderResult accepted(Text component) {
            return new RenderResult(component, "");
        }

        static RenderResult rejected(String reason) {
            return new RenderResult(null, reason == null ? "Paragraph reconstruction failed." : reason);
        }

        boolean accepted() {
            return component != null && !component.getString().isBlank();
        }
    }

    private record AnchorMarker(AccentAnchor anchor, boolean begin) {
    }
}
