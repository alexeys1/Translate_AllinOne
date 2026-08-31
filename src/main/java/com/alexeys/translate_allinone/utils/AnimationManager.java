package com.alexeys.translate_allinone.utils;

import com.alexeys.translate_allinone.utils.text.StylePreserver;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.ColorHelper;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class AnimationManager {
    private static final int DARK_GREY = 0x555555;
    private static final int LIGHT_GREY = 0xAAAAAA;
    private static final int DARK_RED = 0x7A1E1E;
    private static final int LIGHT_RED = 0xFF5A5A;
    private static final int ANIMATION_RUN_GROUP_SIZE = 4;
    private static final long STATE_CLEANUP_INTERVAL_MS = 10_000L;
    private static final long STATE_STALE_AFTER_MS = 30_000L;

    private static final Pattern STRIP_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final long PENDING_ANIMATION_REFRESH_INTERVAL_MS = 50L;
    private static final ConcurrentHashMap<String, AlertTransitionState> ALERT_TRANSITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PendingAnimationEntry> PENDING_ANIMATIONS = new ConcurrentHashMap<>();
    private static volatile long lastStateCleanupTime = 0L;

    private record PendingAnimationEntry(Text original, MutableText result, long builtAtMillis) {
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

    public static MutableText getAnimatedText(String text) {
        String plainText = stripFormatting(text);
        MutableText animatedText = Text.empty();
        long time = System.currentTimeMillis();

        int codePointIndex = 0;
        int offset = 0;
        while (offset < plainText.length()) {
            StringBuilder group = new StringBuilder();
            int groupChars = 0;
            int runColor = 0;
            while (offset < plainText.length() && groupChars < ANIMATION_RUN_GROUP_SIZE) {
                int codePoint = plainText.codePointAt(offset);
                if (groupChars == 0) {
                    float sine = (float) (Math.sin(time / 200.0 + codePointIndex / 5.0) + 1.0) / 2.0f;
                    runColor = ColorHelper.lerp(sine, DARK_GREY, LIGHT_GREY);
                }
                group.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
                codePointIndex++;
                groupChars++;
            }
            if (group.length() > 0) {
                animatedText.append(Text.literal(group.toString())
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(runColor))));
            }
        }
        return animatedText;
    }

    public static MutableText getAnimatedStyledText(Text originalText) {
        return getAnimatedStyledText(originalText, null, false);
    }

    public static MutableText getAnimatedStyledText(Text originalText, String animationKey, boolean alertMissingKeys) {
        if (originalText == null) {
            return Text.empty();
        }
        long now = System.currentTimeMillis();
        if (animationKey != null && !animationKey.isBlank()) {
            PendingAnimationEntry entry = PENDING_ANIMATIONS.get(animationKey);
            if (entry != null
                    && entry.original().equals(originalText)
                    && now - entry.builtAtMillis() < PENDING_ANIMATION_REFRESH_INTERVAL_MS) {
                getAlertProgress(animationKey, alertMissingKeys, now);
                cleanupTransitionStates(now);
                return entry.result();
            }
        }
        MutableText result = buildAnimatedStyledText(originalText, animationKey, alertMissingKeys);
        if (animationKey != null && !animationKey.isBlank()) {
            PENDING_ANIMATIONS.put(animationKey, new PendingAnimationEntry(originalText, result, now));
        }
        cleanupTransitionStates(now);
        return result;
    }

    private static MutableText buildAnimatedStyledText(Text originalText, String animationKey, boolean alertMissingKeys) {
        MutableText animatedText = Text.empty();
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

    private static boolean containsFormattingCodes(String text) {
        return text != null && STRIP_FORMATTING_PATTERN.matcher(text).find();
    }

    private static void appendAnimatedSegment(
            MutableText animatedText,
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
        int offset = 0;
        while (offset < text.length()) {
            StringBuilder group = new StringBuilder();
            int groupChars = 0;
            int runColor = 0;
            while (offset < text.length() && groupChars < ANIMATION_RUN_GROUP_SIZE) {
                int codePoint = text.codePointAt(offset);
                if (groupChars == 0) {
                    runColor = computeAnimatedColor(time, charIndex.get(), alertProgress);
                }
                group.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
                charIndex.incrementAndGet();
                groupChars++;
            }
            if (group.length() > 0) {
                Style newStyle = resolvedStyle.withColor(TextColor.fromRgb(runColor));
                animatedText.append(Text.literal(group.toString()).setStyle(newStyle));
            }
        }
    }

    private static int computeAnimatedColor(long time, int charIndex, float alertProgress) {
        float baseSine = (float) (Math.sin(time / 200.0 + charIndex / 5.0) + 1.0) / 2.0f;
        float alertSine = (float) (Math.sin(time / 120.0 + charIndex / 2.5) + 1.0) / 2.0f;

        int baseColor = ColorHelper.lerp(baseSine, DARK_GREY, LIGHT_GREY);
        int alertColor = ColorHelper.lerp(alertSine, DARK_RED, LIGHT_RED);
        return ColorHelper.lerp(alertProgress, baseColor, alertColor);
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
        PENDING_ANIMATIONS.entrySet().removeIf(entry ->
                now - entry.getValue().builtAtMillis() > STATE_STALE_AFTER_MS);
    }
}
