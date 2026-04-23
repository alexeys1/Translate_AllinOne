package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.text.Text;

import java.util.List;

public final class TooltipTranslationContext {
    private static final long REI_CONTEXT_STALE_MILLIS = 10_000L;
    private static final long WYNN_ITEM_STAT_CONTEXT_STALE_MILLIS = 500L;
    private static final long WYNN_QUEST_CONTEXT_STALE_MILLIS = 10_000L;
    private static final long RECENT_TRANSLATED_TOOLTIP_STALE_MILLIS = 750L;
    private static final long DRAW_CONTEXT_SKIP_EXPECTATION_STALE_MILLIS = 750L;
    private static final ThreadLocal<Boolean> SKIP_DRAW_CONTEXT_TRANSLATION = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> SKIP_DRAW_CONTEXT_TOOLTIP_SIGNATURE = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> SKIP_DRAW_CONTEXT_TOOLTIP_RECORDED_AT = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Integer> WYNNMOD_TOOLTIP_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> REI_TOOLTIP_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> REI_TOOLTIP_RENDER_ENTERED_AT = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Long> WYNN_ITEM_STAT_TOOLTIP_MARKED_AT = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Integer> WYNN_QUEST_TOOLTIP_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> WYNN_QUEST_TOOLTIP_RENDER_ENTERED_AT = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Integer> RECENT_TRANSLATED_TOOLTIP_SIGNATURE = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> RECENT_TRANSLATED_TOOLTIP_RECORDED_AT = ThreadLocal.withInitial(() -> 0L);

    private TooltipTranslationContext() {
    }

    public static void setSkipDrawContextTranslation(boolean skip) {
        SKIP_DRAW_CONTEXT_TRANSLATION.set(skip);
        if (!skip) {
            clearExpectedDrawContextTooltip();
        }
    }

    public static boolean consumeSkipDrawContextTranslation() {
        boolean shouldSkip = SKIP_DRAW_CONTEXT_TRANSLATION.get();
        if (shouldSkip) {
            SKIP_DRAW_CONTEXT_TRANSLATION.set(false);
        }
        return shouldSkip;
    }

    public static boolean consumeSkipDrawContextTranslation(List<Text> tooltipLines) {
        boolean shouldSkip = consumeSkipDrawContextTranslation();
        if (!shouldSkip) {
            return false;
        }

        boolean matchesExpectedTooltip = matchesExpectedDrawContextTooltip(tooltipLines);
        clearExpectedDrawContextTooltip();
        return matchesExpectedTooltip;
    }

    public static void rememberExpectedDrawContextTooltip(List<Text> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            clearExpectedDrawContextTooltip();
            return;
        }

        SKIP_DRAW_CONTEXT_TOOLTIP_SIGNATURE.set(computeTooltipSignature(tooltipLines));
        SKIP_DRAW_CONTEXT_TOOLTIP_RECORDED_AT.set(System.currentTimeMillis());
    }

    public static void pushWynnmodTooltipRender() {
        int depth = WYNNMOD_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 0) {
            WYNNMOD_TOOLTIP_RENDER_DEPTH.set(1);
            return;
        }
        WYNNMOD_TOOLTIP_RENDER_DEPTH.set(depth + 1);
    }

    public static void popWynnmodTooltipRender() {
        int depth = WYNNMOD_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 1) {
            WYNNMOD_TOOLTIP_RENDER_DEPTH.set(0);
            return;
        }
        WYNNMOD_TOOLTIP_RENDER_DEPTH.set(depth - 1);
    }

    public static boolean isInWynnmodTooltipRender() {
        return WYNNMOD_TOOLTIP_RENDER_DEPTH.get() > 0;
    }

    private static boolean matchesExpectedDrawContextTooltip(List<Text> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return false;
        }

        long recordedAt = SKIP_DRAW_CONTEXT_TOOLTIP_RECORDED_AT.get();
        if (recordedAt <= 0L) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - recordedAt > DRAW_CONTEXT_SKIP_EXPECTATION_STALE_MILLIS) {
            return false;
        }

        return SKIP_DRAW_CONTEXT_TOOLTIP_SIGNATURE.get() == computeTooltipSignature(tooltipLines);
    }

    private static void clearExpectedDrawContextTooltip() {
        SKIP_DRAW_CONTEXT_TOOLTIP_SIGNATURE.set(0);
        SKIP_DRAW_CONTEXT_TOOLTIP_RECORDED_AT.set(0L);
    }

    public static void pushReiTooltipRender() {
        int currentDepth = REI_TOOLTIP_RENDER_DEPTH.get();
        if (currentDepth <= 0) {
            REI_TOOLTIP_RENDER_ENTERED_AT.set(System.currentTimeMillis());
            REI_TOOLTIP_RENDER_DEPTH.set(1);
            return;
        }
        REI_TOOLTIP_RENDER_DEPTH.set(currentDepth + 1);
    }

    public static void popReiTooltipRender() {
        int depth = REI_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 1) {
            REI_TOOLTIP_RENDER_DEPTH.set(0);
            REI_TOOLTIP_RENDER_ENTERED_AT.set(0L);
            return;
        }
        REI_TOOLTIP_RENDER_DEPTH.set(depth - 1);
    }

    public static boolean isInReiTooltipRender() {
        int depth = REI_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 0) {
            return false;
        }

        long enteredAt = REI_TOOLTIP_RENDER_ENTERED_AT.get();
        if (enteredAt <= 0L) {
            REI_TOOLTIP_RENDER_DEPTH.set(0);
            return false;
        }

        if (System.currentTimeMillis() - enteredAt > REI_CONTEXT_STALE_MILLIS) {
            REI_TOOLTIP_RENDER_DEPTH.set(0);
            REI_TOOLTIP_RENDER_ENTERED_AT.set(0L);
            return false;
        }

        return true;
    }

    public static void markWynntilsItemStatTooltipRender() {
        WYNN_ITEM_STAT_TOOLTIP_MARKED_AT.set(System.currentTimeMillis());
    }

    public static boolean isInWynntilsItemStatTooltipRender() {
        long markedAt = WYNN_ITEM_STAT_TOOLTIP_MARKED_AT.get();
        if (markedAt <= 0L) {
            return false;
        }

        if (System.currentTimeMillis() - markedAt > WYNN_ITEM_STAT_CONTEXT_STALE_MILLIS) {
            WYNN_ITEM_STAT_TOOLTIP_MARKED_AT.set(0L);
            return false;
        }

        return true;
    }

    public static void pushWynntilsQuestTooltipRender() {
        int currentDepth = WYNN_QUEST_TOOLTIP_RENDER_DEPTH.get();
        if (currentDepth <= 0) {
            WYNN_QUEST_TOOLTIP_RENDER_ENTERED_AT.set(System.currentTimeMillis());
            WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(1);
            return;
        }
        WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(currentDepth + 1);
    }

    public static void popWynntilsQuestTooltipRender() {
        int depth = WYNN_QUEST_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 1) {
            WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(0);
            WYNN_QUEST_TOOLTIP_RENDER_ENTERED_AT.set(0L);
            return;
        }
        WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(depth - 1);
    }

    public static boolean isInWynntilsQuestTooltipRender() {
        int depth = WYNN_QUEST_TOOLTIP_RENDER_DEPTH.get();
        if (depth <= 0) {
            return false;
        }

        long enteredAt = WYNN_QUEST_TOOLTIP_RENDER_ENTERED_AT.get();
        if (enteredAt <= 0L) {
            WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(0);
            return false;
        }

        if (System.currentTimeMillis() - enteredAt > WYNN_QUEST_CONTEXT_STALE_MILLIS) {
            WYNN_QUEST_TOOLTIP_RENDER_DEPTH.set(0);
            WYNN_QUEST_TOOLTIP_RENDER_ENTERED_AT.set(0L);
            return false;
        }

        return true;
    }

    public static boolean shouldRequireStrictParagraphStyleCoverage() {
        return isInWynnmodTooltipRender()
                || isInWynntilsItemStatTooltipRender()
                || isInWynntilsQuestTooltipRender();
    }

    public static void rememberRecentTranslatedTooltip(List<Text> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            RECENT_TRANSLATED_TOOLTIP_SIGNATURE.set(0);
            RECENT_TRANSLATED_TOOLTIP_RECORDED_AT.set(0L);
            return;
        }

        RECENT_TRANSLATED_TOOLTIP_SIGNATURE.set(computeTooltipSignature(tooltipLines));
        RECENT_TRANSLATED_TOOLTIP_RECORDED_AT.set(System.currentTimeMillis());
    }

    public static boolean matchesRecentTranslatedTooltip(List<Text> tooltipLines) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return false;
        }

        long recordedAt = RECENT_TRANSLATED_TOOLTIP_RECORDED_AT.get();
        if (recordedAt <= 0L) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - recordedAt > RECENT_TRANSLATED_TOOLTIP_STALE_MILLIS) {
            RECENT_TRANSLATED_TOOLTIP_SIGNATURE.set(0);
            RECENT_TRANSLATED_TOOLTIP_RECORDED_AT.set(0L);
            return false;
        }

        return RECENT_TRANSLATED_TOOLTIP_SIGNATURE.get() == computeTooltipSignature(tooltipLines);
    }

    private static int computeTooltipSignature(List<Text> tooltipLines) {
        int hash = 1;
        for (Text line : tooltipLines) {
            String value = line == null ? "" : line.getString();
            hash = 31 * hash + value.hashCode();
        }
        return 31 * hash + tooltipLines.size();
    }
}
