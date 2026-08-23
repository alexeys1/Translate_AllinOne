package com.alexeys.translate_allinone.utils;

import com.alexeys.translate_allinone.utils.text.StylePreserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;

public class AnimationManager {
    private static final int DARK_GREY = 0x555555;
    private static final int LIGHT_GREY = 0xAAAAAA;
    private static final int DARK_RED = 0x7A1E1E;
    private static final int LIGHT_RED = 0xFF5A5A;
    private static final long STATE_CLEANUP_INTERVAL_MS = 10_000L;
    private static final long STATE_STALE_AFTER_MS = 30_000L;

    private static final Pattern STRIP_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final ConcurrentHashMap<String, AlertTransitionState> ALERT_TRANSITIONS = new ConcurrentHashMap<>();
    private static volatile long lastStateCleanupTime = 0L;
    private static final int SINE_TABLE_SIZE = 256;
    private static final float[] SINE_TABLE = new float[SINE_TABLE_SIZE];
    private static final double BASE_TIME_STEP = SINE_TABLE_SIZE / (200.0 * Math.PI * 2.0);
    private static final double ALERT_TIME_STEP = SINE_TABLE_SIZE / (120.0 * Math.PI * 2.0);
    private static final int BASE_CHAR_STEP = 8;
    private static final int ALERT_CHAR_STEP = 16;

    static {
        for (int i = 0; i < SINE_TABLE_SIZE; i++) {
            SINE_TABLE[i] = (float) Math.sin(Math.PI * 2.0 * i / SINE_TABLE_SIZE);
        }
    }


    private static final class AlertTransitionState {
        private float alertProgress;
        private long lastUpdateTime;
        private long lastAccessTime;

        private AlertTransitionState(long now) {
            this.lastUpdateTime = now;
            this.lastAccessTime = now;
            this.alertProgress = 0.0f;
        }
    }


    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return STRIP_FORMATTING_PATTERN.matcher(text).replaceAll("");
    }

    public static MutableComponent getAnimatedText(String text) {
        String plainText = stripFormatting(text);
        MutableComponent animatedText = Component.empty();
        long time = System.currentTimeMillis();

        int codePointIndex = 0;
        for (int offset = 0; offset < plainText.length(); ) {
            int codePoint = plainText.codePointAt(offset);
            float sine = (float) (Math.sin(time / 200.0 + codePointIndex / 5.0) + 1.0) / 2.0f;
            int color = ARGB.srgbLerp(sine, DARK_GREY, LIGHT_GREY);
            animatedText.append(Component.literal(new String(Character.toChars(codePoint)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
            offset += Character.charCount(codePoint);
            codePointIndex++;
        }
        return animatedText;
    }

    public static MutableComponent getAnimatedStyledText(Component originalText) {
        return getAnimatedStyledText(originalText, null, false);
    }

    public static MutableComponent getAnimatedStyledText(Component originalText, String animationKey, boolean alertMissingKeys) {
        MutableComponent animatedText = Component.empty();
        if (originalText == null) {
            return animatedText;
        }
        long time = System.currentTimeMillis();
        AtomicInteger charIndex = new AtomicInteger(0);
        float alertProgress = getAlertProgress(animationKey, alertMissingKeys, time);

        originalText.visit((style, s) -> {
            if (s == null || s.isEmpty()) {
                return Optional.empty();
            }

            if (!containsFormattingCodes(s)) {
                appendAnimatedSegment(animatedText, style, s, time, charIndex, alertProgress);
                return Optional.empty();
            }

            StylePreserver.fromLegacyText(s).visit((resolvedStyle, resolvedString) -> {
                appendAnimatedSegment(animatedText, resolvedStyle, resolvedString, time, charIndex, alertProgress);
                return Optional.empty();
            }, style == null ? Style.EMPTY : style);
            return Optional.empty();
        }, Style.EMPTY);

        return animatedText;
    }

    public record AnimatedSegment(Style style, String text) {
    }

    public static AnimatedSegment[] prepareAnimatedSegments(Component originalText) {
        if (originalText == null) {
            return new AnimatedSegment[0];
        }

        List<AnimatedSegment> segments = new ArrayList<>();
        originalText.visit((style, s) -> {
            if (s == null || s.isEmpty()) {
                return Optional.empty();
            }

            if (!containsFormattingCodes(s)) {
                segments.add(new AnimatedSegment(style == null ? Style.EMPTY : style, s));
                return Optional.empty();
            }

            StylePreserver.fromLegacyText(s).visit((resolvedStyle, resolvedString) -> {
                segments.add(new AnimatedSegment(resolvedStyle, resolvedString));
                return Optional.empty();
            }, style == null ? Style.EMPTY : style);
            return Optional.empty();
        }, Style.EMPTY);

        return segments.toArray(new AnimatedSegment[0]);
    }

    public static MutableComponent getAnimatedStyledText(AnimatedSegment[] segments) {
        MutableComponent animatedText = Component.empty();
        if (segments == null || segments.length == 0) {
            return animatedText;
        }

        long time = System.currentTimeMillis();
        int charIndex = 0;
        float alertProgress = getAlertProgress(null, false, time);
        boolean alertActive = alertProgress > 0.001f;
        int basePhaseIdx = (int) ((long) (time * BASE_TIME_STEP) & (SINE_TABLE_SIZE - 1));
        int alertPhaseIdx = (int) ((long) (time * ALERT_TIME_STEP) & (SINE_TABLE_SIZE - 1));

        for (AnimatedSegment segment : segments) {
            Style segmentStyle = segment.style();
            String text = segment.text();
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                int baseIdx = (basePhaseIdx + charIndex * BASE_CHAR_STEP) & (SINE_TABLE_SIZE - 1);
                float baseSine = SINE_TABLE[baseIdx];
                int baseColor = ARGB.srgbLerp((baseSine + 1.0f) / 2.0f, DARK_GREY, LIGHT_GREY);
                int color = baseColor;
                if (alertActive) {
                    int alertIdx = (alertPhaseIdx + charIndex * ALERT_CHAR_STEP) & (SINE_TABLE_SIZE - 1);
                    float alertSine = SINE_TABLE[alertIdx];
                    int alertColor = ARGB.srgbLerp((alertSine + 1.0f) / 2.0f, DARK_RED, LIGHT_RED);
                    color = ARGB.srgbLerp(alertProgress, baseColor, alertColor);
                }

                Style newStyle = segmentStyle.withColor(TextColor.fromRgb(color));
                animatedText.append(Component.literal(new String(Character.toChars(codePoint))).setStyle(newStyle));
                offset += Character.charCount(codePoint);
                charIndex++;
            }
        }
        return animatedText;
    }


    private static boolean containsFormattingCodes(String text) {
        return text != null && STRIP_FORMATTING_PATTERN.matcher(text).find();
    }

    private static void appendAnimatedSegment(
            MutableComponent animatedText,
            Style style,
            String text,
            long time,
            AtomicInteger charIndex,
            float alertProgress
    ) {
        if (animatedText == null || text == null || text.isEmpty()) {
            return;
        }

        Style resolvedStyle = style == null ? Style.EMPTY : style;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            float baseSine = (float) (Math.sin(time / 200.0 + charIndex.get() / 5.0) + 1.0) / 2.0f;
            float alertSine = (float) (Math.sin(time / 120.0 + charIndex.get() / 2.5) + 1.0) / 2.0f;

            int baseColor = ARGB.srgbLerp(baseSine, DARK_GREY, LIGHT_GREY);
            int alertColor = ARGB.srgbLerp(alertSine, DARK_RED, LIGHT_RED);
            int color = ARGB.srgbLerp(alertProgress, baseColor, alertColor);

            Style newStyle = resolvedStyle.withColor(TextColor.fromRgb(color));
            animatedText.append(Component.literal(new String(Character.toChars(codePoint))).setStyle(newStyle));
            offset += Character.charCount(codePoint);
            charIndex.incrementAndGet();
        }
    }

    private static float getAlertProgress(String animationKey, boolean alertMissingKeys, long now) {
        if (animationKey == null || animationKey.isEmpty()) {
            return alertMissingKeys ? 1.0f : 0.0f;
        }

        AlertTransitionState state = ALERT_TRANSITIONS.computeIfAbsent(animationKey, key -> new AlertTransitionState(now));
        float target = alertMissingKeys ? 1.0f : 0.0f;

        synchronized (state) {
            long elapsedMs = Math.max(1L, now - state.lastUpdateTime);
            float elapsedSeconds = Math.min(elapsedMs / 1000.0f, 0.25f);
            float blend = 1.0f - (float) Math.exp(-6.0f * elapsedSeconds);
            state.alertProgress += (target - state.alertProgress) * blend;
            state.lastUpdateTime = now;
            state.lastAccessTime = now;
        }

        cleanupTransitionStates(now);
        return state.alertProgress;
    }

    private static void cleanupTransitionStates(long now) {
        if (now - lastStateCleanupTime < STATE_CLEANUP_INTERVAL_MS) {
            return;
        }
        lastStateCleanupTime = now;

        ALERT_TRANSITIONS.entrySet().removeIf(entry -> {
            AlertTransitionState state = entry.getValue();
            synchronized (state) {
                return (now - state.lastAccessTime) > STATE_STALE_AFTER_MS && state.alertProgress < 0.01f;
            }
        });
    }
}
