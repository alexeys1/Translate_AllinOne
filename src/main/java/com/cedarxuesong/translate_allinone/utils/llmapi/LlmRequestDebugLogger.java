package com.cedarxuesong.translate_allinone.utils.llmapi;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import com.cedarxuesong.translate_allinone.utils.config.ModConfig;
import com.cedarxuesong.translate_allinone.utils.llmapi.openai.OpenAIRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

final class LlmRequestDebugLogger {
    private static final int MESSAGE_PREVIEW_HEAD_CHARS = 220;
    private static final int MESSAGE_PREVIEW_TAIL_CHARS = 140;
    private static final ConcurrentMap<String, AtomicInteger> REQUEST_FINGERPRINT_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, AtomicInteger> ROLE_FINGERPRINT_COUNTS = new ConcurrentHashMap<>();

    private LlmRequestDebugLogger() {
    }

    static void logIfEnabled(
            String api,
            ProviderSettings settings,
            List<OpenAIRequest.Message> messages,
            boolean streaming,
            boolean structuredOutputEnabled,
            String dispatchReason,
            int sendAttempt,
            String requestContext
    ) {
        if (!shouldLogLlmRequestTextStats()) {
            return;
        }

        RequestTextStats stats = summarize(messages);
        SeenCounts seenCounts = registerSeenCounts(stats);
        Translate_AllinOne.LOGGER.info(
                "[LLMDev:request] api={} provider={} model={} streaming={} structuredOutput={} dispatch={} sendAttempt={} messages={} totalChars={} totalCodePoints={} totalUtf8Bytes={} estimatedTokens={} charsByRole={} estimatedTokensByRole={} tokenShareByRole={} roleFingerprints={} roleSeenCounts={} messageStats={} messagePreview={} requestFingerprint={} requestSeenCount={} context={}",
                api,
                providerName(settings),
                modelName(settings),
                streaming,
                structuredOutputEnabled,
                dispatchReason,
                sendAttempt,
                stats.messageCount(),
                stats.totalChars(),
                stats.totalCodePoints(),
                stats.totalUtf8Bytes(),
                stats.estimatedTokens(),
                formatCharsByRole(stats.roleStats()),
                formatEstimatedTokensByRole(stats.roleStats()),
                formatTokenShareByRole(stats.roleStats()),
                formatRoleFingerprints(stats.roleStats()),
                formatRoleSeenCounts(seenCounts.roleSeenCounts()),
                formatMessageStats(stats.messages()),
                formatMessagePreview(stats.messages()),
                stats.requestFingerprint(),
                seenCounts.requestSeenCount(),
                requestContext == null ? "" : requestContext
        );
    }

    static RequestTextStats summarize(List<OpenAIRequest.Message> messages) {
        List<MessageTextStats> messageStats = new ArrayList<>();
        Map<String, RoleAccumulator> roleAccumulators = new LinkedHashMap<>();
        StringBuilder requestFingerprintSource = new StringBuilder();
        int totalChars = 0;
        int totalCodePoints = 0;
        int totalUtf8Bytes = 0;

        if (messages != null) {
            for (OpenAIRequest.Message message : messages) {
                String role = normalizeRole(message == null ? null : message.role);
                String content = message == null || message.content == null ? "" : message.content;
                int charCount = content.length();
                int codePointCount = content.codePointCount(0, content.length());
                int utf8Bytes = utf8Length(content);
                int estimatedTokens = estimateTokens(utf8Bytes);

                messageStats.add(new MessageTextStats(
                        role,
                        charCount,
                        codePointCount,
                        utf8Bytes,
                        estimatedTokens,
                        shortHash(content),
                        buildPreview(content)
                ));
                roleAccumulators.computeIfAbsent(role, RoleAccumulator::new).add(content, charCount, codePointCount, utf8Bytes);
                totalChars += charCount;
                totalCodePoints += codePointCount;
                totalUtf8Bytes += utf8Bytes;
                requestFingerprintSource.append(role).append('\u0000').append(content).append('\u0001');
            }
        }

        Map<String, RoleTextStats> roleStats = buildRoleStats(roleAccumulators, totalUtf8Bytes);
        return new RequestTextStats(
                messageStats.size(),
                totalChars,
                totalCodePoints,
                totalUtf8Bytes,
                estimateTokens(totalUtf8Bytes),
                Collections.unmodifiableMap(roleStats),
                List.copyOf(messageStats),
                shortHash(requestFingerprintSource.toString())
        );
    }

    static SeenCounts registerSeenCounts(RequestTextStats stats) {
        int requestSeenCount = incrementCounter(REQUEST_FINGERPRINT_COUNTS, stats == null ? "" : stats.requestFingerprint());
        Map<String, Integer> roleSeenCounts = new LinkedHashMap<>();
        if (stats != null && stats.roleStats() != null) {
            for (RoleTextStats roleStats : stats.roleStats().values()) {
                roleSeenCounts.put(
                        roleStats.role(),
                        incrementCounter(ROLE_FINGERPRINT_COUNTS, roleCounterKey(roleStats.role(), roleStats.fingerprint()))
                );
            }
        }
        return new SeenCounts(requestSeenCount, Collections.unmodifiableMap(roleSeenCounts));
    }

    static void resetSeenCountsForTest() {
        REQUEST_FINGERPRINT_COUNTS.clear();
        ROLE_FINGERPRINT_COUNTS.clear();
    }

    private static boolean shouldLogLlmRequestTextStats() {
        try {
            ModConfig config = Translate_AllinOne.getConfig();
            return config != null
                    && config.debug != null
                    && config.debug.log_llm_request_text_stats;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String providerName(ProviderSettings settings) {
        if (settings == null) {
            return "";
        }
        if (settings.openAISettings() != null) {
            return settings.openAISettings().providerType().name().toLowerCase(Locale.ROOT);
        }
        if (settings.ollamaSettings() != null) {
            return "ollama";
        }
        return "";
    }

    private static String modelName(ProviderSettings settings) {
        if (settings == null) {
            return "";
        }
        if (settings.openAISettings() != null) {
            return blankToEmpty(settings.openAISettings().modelId());
        }
        if (settings.ollamaSettings() != null) {
            return blankToEmpty(settings.ollamaSettings().modelId());
        }
        return "";
    }

    private static String formatCharsByRole(Map<String, RoleTextStats> roleStats) {
        if (roleStats == null || roleStats.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (RoleTextStats stats : roleStats.values()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(stats.role()).append('=').append(stats.charCount());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String formatEstimatedTokensByRole(Map<String, RoleTextStats> roleStats) {
        if (roleStats == null || roleStats.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (RoleTextStats stats : roleStats.values()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(stats.role()).append('=').append(stats.estimatedTokens());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String formatTokenShareByRole(Map<String, RoleTextStats> roleStats) {
        if (roleStats == null || roleStats.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (RoleTextStats stats : roleStats.values()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(stats.role())
                    .append('=')
                    .append(String.format(Locale.ROOT, "%.2f%%", stats.tokenSharePercent()));
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String formatRoleFingerprints(Map<String, RoleTextStats> roleStats) {
        if (roleStats == null || roleStats.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (RoleTextStats stats : roleStats.values()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(stats.role()).append('=').append(stats.fingerprint());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String formatRoleSeenCounts(Map<String, Integer> roleSeenCounts) {
        if (roleSeenCounts == null || roleSeenCounts.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : roleSeenCounts.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String formatMessageStats(List<MessageTextStats> messageStats) {
        if (messageStats == null || messageStats.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < messageStats.size(); i++) {
            MessageTextStats stats = messageStats.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stats.role())
                    .append(':')
                    .append(stats.charCount())
                    .append('c')
                    .append('/')
                    .append(stats.utf8Bytes())
                    .append('b')
                    .append('/')
                    .append('~')
                    .append(stats.estimatedTokens())
                    .append('t')
                    .append('#')
                    .append(stats.fingerprint());
        }
        return builder.append(']').toString();
    }

    static String formatMessagePreview(List<MessageTextStats> messageStats) {
        if (messageStats == null || messageStats.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < messageStats.size(); i++) {
            MessageTextStats stats = messageStats.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stats.role())
                    .append(":\"")
                    .append(stats.preview())
                    .append('"');
        }
        return builder.append(']').toString();
    }

    private static Map<String, RoleTextStats> buildRoleStats(Map<String, RoleAccumulator> accumulators, int totalUtf8Bytes) {
        Map<String, RoleTextStats> roleStats = new LinkedHashMap<>();
        if (accumulators == null || accumulators.isEmpty()) {
            return roleStats;
        }
        for (Map.Entry<String, RoleAccumulator> entry : accumulators.entrySet()) {
            RoleAccumulator accumulator = entry.getValue();
            int estimatedTokens = estimateTokens(accumulator.utf8Bytes);
            double tokenSharePercent = totalUtf8Bytes <= 0
                    ? 0.0
                    : (accumulator.utf8Bytes * 100.0) / totalUtf8Bytes;
            roleStats.put(
                    entry.getKey(),
                    new RoleTextStats(
                            accumulator.role,
                            accumulator.charCount,
                            accumulator.codePointCount,
                            accumulator.utf8Bytes,
                            estimatedTokens,
                            tokenSharePercent,
                            shortHash(accumulator.fingerprintSource.toString())
                    )
            );
        }
        return roleStats;
    }

    private static String normalizeRole(String role) {
        String normalized = blankToEmpty(role).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static int utf8Length(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int estimateTokens(int utf8Bytes) {
        if (utf8Bytes <= 0) {
            return 0;
        }
        return (utf8Bytes + 3) / 4;
    }

    private static int incrementCounter(ConcurrentMap<String, AtomicInteger> counters, String key) {
        return counters.computeIfAbsent(key == null ? "" : key, ignored -> new AtomicInteger(0)).incrementAndGet();
    }

    private static String roleCounterKey(String role, String fingerprint) {
        return normalizeRole(role) + '\u0000' + (fingerprint == null ? "" : fingerprint);
    }

    private static String buildPreview(String content) {
        String escaped = escapeForInlineLog(content);
        int maxPreviewChars = MESSAGE_PREVIEW_HEAD_CHARS + MESSAGE_PREVIEW_TAIL_CHARS;
        if (escaped.length() <= maxPreviewChars) {
            return escaped;
        }
        int omittedChars = escaped.length() - maxPreviewChars;
        return escaped.substring(0, MESSAGE_PREVIEW_HEAD_CHARS)
                + "...(+" + omittedChars + " chars)..."
                + escaped.substring(escaped.length() - MESSAGE_PREVIEW_TAIL_CHARS);
    }

    private static String escapeForInlineLog(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 6); i++) {
                builder.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    static record RequestTextStats(
            int messageCount,
            int totalChars,
            int totalCodePoints,
            int totalUtf8Bytes,
            int estimatedTokens,
            Map<String, RoleTextStats> roleStats,
            List<MessageTextStats> messages,
            String requestFingerprint
    ) {
    }

    static record RoleTextStats(
            String role,
            int charCount,
            int codePointCount,
            int utf8Bytes,
            int estimatedTokens,
            double tokenSharePercent,
            String fingerprint
    ) {
    }

    static record MessageTextStats(
            String role,
            int charCount,
            int codePointCount,
            int utf8Bytes,
            int estimatedTokens,
            String fingerprint,
            String preview
    ) {
    }

    static record SeenCounts(
            int requestSeenCount,
            Map<String, Integer> roleSeenCounts
    ) {
    }

    private static final class RoleAccumulator {
        private final String role;
        private int charCount;
        private int codePointCount;
        private int utf8Bytes;
        private final StringBuilder fingerprintSource = new StringBuilder();

        private RoleAccumulator(String role) {
            this.role = role;
        }

        private void add(String content, int charCount, int codePointCount, int utf8Bytes) {
            this.charCount += charCount;
            this.codePointCount += codePointCount;
            this.utf8Bytes += utf8Bytes;
            this.fingerprintSource.append(content == null ? "" : content).append('\u0001');
        }
    }
}
