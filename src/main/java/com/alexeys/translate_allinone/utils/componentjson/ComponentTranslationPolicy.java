package com.alexeys.translate_allinone.utils.componentjson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComponentTranslationPolicy {
    public static final int CURRENT_VERSION = 1;
    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile("</?s\\d+>");
    private static final List<Pattern> PROTECTED_TOKEN_PATTERNS = List.of(
            Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[sdf]"),
            Pattern.compile("\\{[A-Za-z][A-Za-z0-9_.:-]*}"),
            Pattern.compile("(?i)\\bhttps?://[^\\s<>{}\\[\\]]+"),
            Pattern.compile("\\x{00A7}[0-9A-FK-ORa-fk-or]"),
            Pattern.compile("<taio-player-name>"),
            STYLE_TAG_PATTERN,
            Pattern.compile("(?<![\\x{00A7}\\p{L}\\p{N}_])-?\\d+(?:[.,]\\d+)?(?:\\s?(?:%|ms|s|min|h|d|px|秒|分钟|小时|天|格|级|点|次|个|块|米))?")
    );

    private final ComponentTranslationRoute route;
    private final Set<String> privateTokens;
    private final String context;
    private final Map<String, String> semanticOverrides;

    private ComponentTranslationPolicy(
            ComponentTranslationRoute route,
            Set<String> privateTokens,
            String context,
            Map<String, String> semanticOverrides
    ) {
        this.route = route;
        this.privateTokens = Set.copyOf(privateTokens);
        this.context = context;
        this.semanticOverrides = Map.copyOf(semanticOverrides);
    }

    public static ComponentTranslationPolicy forRoute(ComponentTranslationRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("Component translation route is required.");
        }
        return new ComponentTranslationPolicy(route, Set.of(), route.wireName(), Map.of());
    }

    public ComponentTranslationPolicy withPrivateTokens(Set<String> tokens) {
        return new ComponentTranslationPolicy(
                route,
                tokens == null ? Set.of() : tokens,
                context,
                semanticOverrides
        );
    }

    public ComponentTranslationPolicy withContext(String value) {
        String resolved = value == null || value.isBlank() ? route.wireName() : value.trim();
        return new ComponentTranslationPolicy(route, privateTokens, resolved, semanticOverrides);
    }

    public ComponentTranslationPolicy withSemanticSetting(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            throw new IllegalArgumentException("Component policy semantic setting is incomplete.");
        }
        Map<String, String> settings = new TreeMap<>(semanticOverrides);
        settings.put(key.trim(), value);
        return new ComponentTranslationPolicy(route, privateTokens, context, settings);
    }

    public ComponentTranslationRoute route() {
        return route;
    }

    public int version() {
        return CURRENT_VERSION;
    }

    public String context() {
        return context;
    }

    public boolean allowsLiteral(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.codePoints().noneMatch(ComponentTranslationPolicy::isPrivateUseCodePoint);
    }

    public Map<String, Integer> protectedTokenMultiset(String text) {
        if (text == null || text.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> tokens = new LinkedHashMap<>();
        for (Pattern pattern : PROTECTED_TOKEN_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                tokens.merge(matcher.group(), 1, Integer::sum);
            }
        }
        for (String token : privateTokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            int offset = 0;
            while ((offset = text.indexOf(token, offset)) >= 0) {
                tokens.merge(token, 1, Integer::sum);
                offset += token.length();
            }
        }
        return Collections.unmodifiableMap(new TreeMap<>(tokens));
    }

    static List<ProtectedTokenSpan> protectedTokenSpans(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<ProtectedTokenCandidate> candidates = new ArrayList<>();
        for (int patternIndex = 0; patternIndex < PROTECTED_TOKEN_PATTERNS.size(); patternIndex++) {
            Pattern pattern = PROTECTED_TOKEN_PATTERNS.get(patternIndex);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                candidates.add(new ProtectedTokenCandidate(
                        matcher.start(),
                        matcher.end(),
                        patternIndex,
                        pattern == STYLE_TAG_PATTERN
                ));
            }
        }
        candidates.sort(Comparator
                .comparingInt(ProtectedTokenCandidate::start)
                .thenComparingInt(ProtectedTokenCandidate::patternIndex)
                .thenComparingInt(ProtectedTokenCandidate::end));

        List<ProtectedTokenSpan> spans = new ArrayList<>();
        int consumedUntil = 0;
        for (ProtectedTokenCandidate candidate : candidates) {
            if (candidate.start() < consumedUntil) {
                continue;
            }
            spans.add(new ProtectedTokenSpan(candidate.start(), candidate.end(), candidate.styleTag()));
            consumedUntil = candidate.end();
        }
        return List.copyOf(spans);
    }

    public Map<String, String> semanticSettings() {
        Map<String, String> settings = new TreeMap<>();
        settings.put("literal_scope", "root_and_extra");
        settings.put("private_use", "exclude");
        settings.put("token_order", "movable");
        if (!privateTokens.isEmpty()) {
            List<String> sortedTokens = new ArrayList<>(privateTokens);
            Collections.sort(sortedTokens);
            settings.put("private_tokens", String.join("\u001f", sortedTokens));
        }
        settings.put("context", context);
        settings.putAll(semanticOverrides);
        return Collections.unmodifiableMap(settings);
    }

    private static boolean isPrivateUseCodePoint(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE
                || (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    static record ProtectedTokenSpan(int start, int end, boolean styleTag) {
    }

    private record ProtectedTokenCandidate(int start, int end, int patternIndex, boolean styleTag) {
    }
}
