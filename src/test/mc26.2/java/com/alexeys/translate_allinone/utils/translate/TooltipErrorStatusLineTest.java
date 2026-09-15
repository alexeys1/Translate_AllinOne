package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.CacheStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipErrorStatusLineTest {
    @Test
    void showsGenericTooltipErrors() {
        assertTrue(TooltipInternalLineSupport.shouldShowErrorStatusLine(
                result("generic", "Translation request timed out")
        ));
    }

    @Test
    void ignoresErrorsWithoutTranslatableLines() {
        assertFalse(TooltipInternalLineSupport.shouldShowErrorStatusLine(
                result("no-lines", "Translation request timed out", 0)
        ));
    }

    @Test
    void ignoresBlankErrors() {
        assertFalse(TooltipInternalLineSupport.shouldShowErrorStatusLine(result("blank", "")));
    }

    @Test
    void quietsRepeatedErrorsAfterTheDisplayWindow() throws Exception {
        TooltipTranslationSupport.TooltipProcessingResult probe = result("quiet", "Provider returned HTTP 503");
        String fingerprint = firstSightingFingerprint(probe);

        rewriteFingerprintSince(fingerprint, System.currentTimeMillis() - 4_000L);
        assertFalse(TooltipInternalLineSupport.shouldShowErrorStatusLine(probe));

        rewriteFingerprintSince(fingerprint, System.currentTimeMillis() - 9_000L);
        assertTrue(TooltipInternalLineSupport.shouldShowErrorStatusLine(probe));
    }

    @Test
    void treatsOnlyExactGeneratedLinesAsInternal() {
        Component generatedError = TooltipInternalLineSupport.createErrorStatusLine("Translation request timed out");
        Component generatedPending = TooltipInternalLineSupport.createAnimatedPendingStatusLine("tooltip-lore-guard");
        Component loreError = Component.literal(
                TooltipInternalLineSupport.createErrorStatusLine("").getString() + "unknown attribute"
        );
        Component loreTranslating = Component.literal(generatedPending.getString() + " please wait");

        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(generatedError));
        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(generatedPending));
        assertFalse(TooltipInternalLineSupport.isInternalGeneratedLine(loreError));
        assertFalse(TooltipInternalLineSupport.isInternalGeneratedLine(loreTranslating));
        assertEquals(2, TooltipInternalLineSupport.stripInternalGeneratedLines(
                List.of(loreError, loreTranslating)
        ).size());
    }

    @Test
    void recognizesGeneratedStatusLines() {
        Component status = TooltipInternalLineSupport.createStatusLine(
                new CacheStats(1, 3),
                false,
                "tooltip-error-status-line-test"
        );
        Component error = TooltipInternalLineSupport.createErrorStatusLine("Translation request timed out");
        Component pending = TooltipInternalLineSupport.createAnimatedPendingStatusLine("tooltip-error-status-line-test");

        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(status));
        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(error));
        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(pending));
    }

    @Test
    void dropsStaleStatusLinesAndKeepsOtherLines() {
        Component status = TooltipInternalLineSupport.createStatusLine(new CacheStats(0, 2), false, "tooltip-stale-status");
        Component error = TooltipInternalLineSupport.createErrorStatusLine("Translation request timed out");
        Component lore = Component.literal("Some lore line");
        Component notice = TooltipRefreshNoticeSupport.createRefreshNoticeLine();

        List<Component> sanitized = TooltipInternalLineSupport.withoutInternalStatusLines(
                List.of(lore, status, error, notice)
        );

        assertEquals(List.of(lore, notice), sanitized);
    }

    @Test
    void keepsListsWithoutStatusLinesUntouched() {
        List<Component> tooltip = List.of(Component.literal("first"), Component.literal("second"));

        assertSame(tooltip, TooltipInternalLineSupport.withoutInternalStatusLines(tooltip));
    }

    @Test
    void recognizesGeneratedLinesAfterVisualOrderRoundTrip() {
        Component status = TooltipInternalLineSupport.createStatusLine(new CacheStats(1, 3), false, "tooltip-round-trip");
        Component error = TooltipInternalLineSupport.createErrorStatusLine("Translation request timed out");
        Component notice = TooltipRefreshNoticeSupport.createRefreshNoticeLine();

        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(roundTrip(status)));
        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(roundTrip(error)));
        assertTrue(TooltipInternalLineSupport.isInternalGeneratedLine(roundTrip(notice)));
    }

    private static Component roundTrip(Component line) {
        MutableComponent result = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style[] currentStyle = {Style.EMPTY};

        line.getVisualOrderText().accept((index, style, codePoint) -> {
            if (!style.equals(currentStyle[0]) && segment.length() > 0) {
                result.append(Component.literal(segment.toString()).setStyle(currentStyle[0]));
                segment.setLength(0);
            }
            currentStyle[0] = style;
            segment.appendCodePoint(codePoint);
            return true;
        });

        if (segment.length() > 0) {
            result.append(Component.literal(segment.toString()).setStyle(currentStyle[0]));
        }
        return result;
    }

    private static String firstSightingFingerprint(TooltipTranslationSupport.TooltipProcessingResult probe) throws Exception {
        Map<String, Long> since = fingerprintSince();
        Set<String> known = Set.copyOf(since.keySet());
        assertTrue(TooltipInternalLineSupport.shouldShowErrorStatusLine(probe));
        return since.keySet().stream()
                .filter(fingerprint -> !known.contains(fingerprint))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> fingerprintSince() throws Exception {
        Field field = TooltipInternalLineSupport.class.getDeclaredField("ERROR_SINCE");
        field.setAccessible(true);
        return (Map<String, Long>) field.get(null);
    }

    private static void rewriteFingerprintSince(String fingerprint, long value) throws Exception {
        Map<String, Long> since = fingerprintSince();
        synchronized (since) {
            since.put(fingerprint, value);
        }
    }

    private static TooltipTranslationSupport.TooltipProcessingResult result(String fingerprint, String errorMessage) {
        return result(fingerprint, errorMessage, 2);
    }

    private static TooltipTranslationSupport.TooltipProcessingResult result(
            String fingerprint,
            String errorMessage,
            int translatableLines
    ) {
        return new TooltipTranslationSupport.TooltipProcessingResult(
                List.of(Component.literal("lore-" + fingerprint)),
                translatableLines,
                false,
                false,
                errorMessage
        );
    }
}
