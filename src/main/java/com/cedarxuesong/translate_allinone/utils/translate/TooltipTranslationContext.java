package com.cedarxuesong.translate_allinone.utils.translate;

public final class TooltipTranslationContext {
    private static final long REI_CONTEXT_STALE_MILLIS = 10_000L;
    private static final ThreadLocal<Boolean> SKIP_DRAW_CONTEXT_TRANSLATION = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> REI_TOOLTIP_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> REI_TOOLTIP_RENDER_ENTERED_AT = ThreadLocal.withInitial(() -> 0L);

    private TooltipTranslationContext() {
    }

    public static void setSkipDrawContextTranslation(boolean skip) {
        SKIP_DRAW_CONTEXT_TRANSLATION.set(skip);
    }

    public static boolean consumeSkipDrawContextTranslation() {
        boolean shouldSkip = SKIP_DRAW_CONTEXT_TRANSLATION.get();
        if (shouldSkip) {
            SKIP_DRAW_CONTEXT_TRANSLATION.set(false);
        }
        return shouldSkip;
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
}
