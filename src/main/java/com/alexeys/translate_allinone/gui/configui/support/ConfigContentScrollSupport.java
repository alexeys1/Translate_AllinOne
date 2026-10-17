package com.alexeys.translate_allinone.gui.configui.support;

import com.alexeys.translate_allinone.gui.configui.render.ConfigUiDraw;
import com.alexeys.translate_allinone.utils.config.ui.ConfigSection;
import com.alexeys.translate_allinone.utils.config.ui.UiRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ConfigContentScrollSupport {
    private static final int VIEWPORT_TOP_INSET = 28;
    private static final int VIEWPORT_SIDE_INSET = 20;
    private static final int VIEWPORT_RIGHT_MARGIN = 16;
    private static final int VIEWPORT_BOTTOM_MARGIN = 12;
    private static final int CONTENT_CLIP_SIDE_PADDING = 6;
    private static final int CONTENT_CLIP_TOP_PADDING = 6;
    private static final int CONTENT_CLIP_BOTTOM_PADDING = 8;
    private static final int CONTENT_SCROLLBAR_GAP = CONTENT_CLIP_SIDE_PADDING;
    private static final int SCROLL_STEP = 28;
    private static final double SCROLL_ANIMATION_SPEED = 15.0;
    private static final double OVER_SCROLL_RESISTANCE = 0.42;
    private static final double OVER_SCROLL_MAX = 56.0;
    private static final double OVER_SCROLL_SPRING = 58.0;
    private static final double OVER_SCROLL_DAMPING = 12.0;
    private static final double OVER_SCROLL_WHEEL_OFFSET_FACTOR = 0.2;
    private static final double OVER_SCROLL_WHEEL_VELOCITY_FACTOR = 9.0;

    private final int leftPanelWidth;
    private final int topBarHeight;
    private final int scrollbarWidth;
    private final int scrollbarMinThumbHeight;
    private final Runnable onOffsetApplied;
    private final Runnable onRebuild;

    private final int[] sectionScrollOffsets = new int[ConfigSection.values().length];
    private UiRect viewport = new UiRect(0, 0, 0, 0);
    private int activeSectionIndex;
    private int scrollOffset;
    private double visualOffset;
    private int maxOffset;
    private double elasticOffset;
    private double elasticVelocity;
    private boolean draggingScrollbar;
    private boolean draggingByMouse;
    private int dragStartOffset;
    private double dragStartMouseY;

    public ConfigContentScrollSupport(
            int leftPanelWidth,
            int topBarHeight,
            int scrollbarWidth,
            int scrollbarMinThumbHeight,
            Runnable onOffsetApplied,
            Runnable onRebuild
    ) {
        this.leftPanelWidth = leftPanelWidth;
        this.topBarHeight = topBarHeight;
        this.scrollbarWidth = scrollbarWidth;
        this.scrollbarMinThumbHeight = scrollbarMinThumbHeight;
        this.onOffsetApplied = onOffsetApplied;
        this.onRebuild = onRebuild;
    }

    public void updateViewport(int screenWidth, int screenHeight) {
        int x = leftPanelWidth + 20;
        int y = topBarHeight + 28;
        int width = Math.max(0, screenWidth - x - 16);
        int height = Math.max(0, screenHeight - y - 12);
        viewport = new UiRect(x, y, width, height);
    }

    public void useViewport(UiRect rect) {
        viewport = rect;
    }

    public void resetOffset() {
        scrollOffset = 0;
        visualOffset = 0.0;
        elasticOffset = 0.0;
        elasticVelocity = 0.0;
        stopScrollDrag();
    }

    public void selectSection(ConfigSection section) {
        activeSectionIndex = section.ordinal();
        scrollOffset = Math.max(0, sectionScrollOffsets[activeSectionIndex]);
        if (!Double.isFinite(visualOffset)) {
            visualOffset = scrollOffset;
        }
    }

    public void switchSection(ConfigSection from, ConfigSection to) {
        sectionScrollOffsets[from.ordinal()] = scrollOffset;
        activeSectionIndex = to.ordinal();
        scrollOffset = Math.max(0, sectionScrollOffsets[activeSectionIndex]);
        visualOffset = scrollOffset;
        elasticOffset = 0.0;
        elasticVelocity = 0.0;
    }

    public UiRect viewport() {
        return viewport;
    }

    public int layoutWidth() {
        return Math.max(0, viewport.width - scrollbarWidth - CONTENT_SCROLLBAR_GAP);
    }

    public UiRect clipRect(int screenWidth, int screenHeight) {
        if (viewport.width <= 0 || viewport.height <= 0) {
            return viewport;
        }

        int clipX = Math.max(leftPanelWidth, viewport.x - CONTENT_CLIP_SIDE_PADDING);
        int clipY = Math.max(topBarHeight, viewport.y - CONTENT_CLIP_TOP_PADDING);
        int clipRight = Math.min(screenWidth, layoutRight() + CONTENT_CLIP_SIDE_PADDING);
        int clipBottom = Math.min(screenHeight, viewport.bottom() + CONTENT_CLIP_BOTTOM_PADDING);
        return new UiRect(
                clipX,
                clipY,
                Math.max(0, clipRight - clipX),
                Math.max(0, clipBottom - clipY)
        );
    }

    public int currentRenderOffset() {
        return (int) Math.round(visualOffset - elasticOffset);
    }

    public void updateBounds(int contentBottomY) {
        double naturalBottomY = contentBottomY + currentRenderOffset();
        int viewportBottom = viewport.bottom();
        maxOffset = Math.max(0, (int) Math.ceil(naturalBottomY - viewportBottom));
        int clamped = clampScrollOffset(scrollOffset);
        if (clamped != scrollOffset) {
            scrollOffset = clamped;
            sectionScrollOffsets[activeSectionIndex] = clamped;
        }
    }

    public boolean containsMouse(double mouseX, double mouseY) {
        return viewport.contains(mouseX, mouseY);
    }

    public boolean hasScrollableContent() {
        return maxOffset > 0;
    }

    public boolean pressScrollbar(double mouseX, double mouseY) {
        UiRect thumb = scrollbarThumbRect();
        if (thumb != null && thumb.contains(mouseX, mouseY)) {
            startScrollbarDrag();
            return true;
        }

        UiRect track = scrollbarTrackRect();
        if (track == null || !track.contains(mouseX, mouseY)) {
            return false;
        }

        int nextOffset = scrollOffsetFromThumbMouseY(mouseY);
        if (nextOffset != scrollOffset) {
            applyScrollOffset(nextOffset, false);
        }
        startScrollbarDrag();
        return true;
    }

    public boolean startContentDrag(double mouseY) {
        if (maxOffset <= 0) {
            return false;
        }
        draggingByMouse = true;
        dragStartMouseY = mouseY;
        dragStartOffset = scrollOffset;
        draggingScrollbar = false;
        return true;
    }

    public boolean dragScrollbarTo(double mouseY) {
        if (!draggingScrollbar) {
            return false;
        }

        int nextOffset = scrollOffsetFromThumbMouseY(mouseY);
        if (nextOffset != scrollOffset) {
            applyScrollOffset(nextOffset, true);
        }
        return true;
    }

    public boolean dragContentTo(double mouseY) {
        if (!draggingByMouse) {
            return false;
        }

        double delta = mouseY - dragStartMouseY;
        double rawOffset = dragStartOffset - delta;
        int nextOffset = clampScrollOffset((int) Math.round(rawOffset));
        if (nextOffset != scrollOffset) {
            applyScrollOffset(nextOffset, true);
        }

        double overflow = rawOffset - nextOffset;
        if (Math.abs(overflow) > 0.001) {
            elasticOffset = clampDouble(-overflow * OVER_SCROLL_RESISTANCE, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);
            elasticVelocity = 0.0;
            onRebuild.run();
        } else if (Math.abs(elasticOffset) > 0.001 || Math.abs(elasticVelocity) > 0.001) {
            elasticOffset = 0.0;
            elasticVelocity = 0.0;
            onRebuild.run();
        }
        return true;
    }

    public boolean releaseDrags() {
        if (!draggingScrollbar && !draggingByMouse) {
            return false;
        }
        stopScrollDrag();
        return true;
    }

    public void stopScrollDrag() {
        draggingScrollbar = false;
        draggingByMouse = false;
    }

    public void clearDragAndElastic() {
        stopScrollDrag();
        elasticOffset = 0.0;
        elasticVelocity = 0.0;
    }

    public boolean scrollByWheel(double mouseX, double mouseY, double verticalAmount) {
        if (!viewport.contains(mouseX, mouseY) || maxOffset <= 0 || verticalAmount == 0.0) {
            return false;
        }

        int delta = (int) Math.round(-verticalAmount * SCROLL_STEP);
        if (delta == 0) {
            delta = verticalAmount > 0 ? -SCROLL_STEP : SCROLL_STEP;
        }
        return tryScrollBy(delta);
    }

    public boolean animate(double deltaSeconds) {
        int beforeRenderOffset = currentRenderOffset();

        if (draggingScrollbar || draggingByMouse) {
            visualOffset = scrollOffset;
        } else {
            double blend = 1.0 - Math.exp(-deltaSeconds * SCROLL_ANIMATION_SPEED);
            visualOffset += (scrollOffset - visualOffset) * blend;

            double acceleration = -elasticOffset * OVER_SCROLL_SPRING - elasticVelocity * OVER_SCROLL_DAMPING;
            elasticVelocity += acceleration * deltaSeconds;
            elasticOffset += elasticVelocity * deltaSeconds;
            elasticOffset = clampDouble(elasticOffset, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);

            if (Math.abs(elasticOffset) < 0.01 && Math.abs(elasticVelocity) < 0.01) {
                elasticOffset = 0.0;
                elasticVelocity = 0.0;
            }
        }

        return currentRenderOffset() != beforeRenderOffset;
    }

    public void drawScrollbar(GuiGraphicsExtractor context, int mouseX, int mouseY, Style style) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null) {
            return;
        }

        context.fill(track.x, track.y, track.right(), track.bottom(), style.colorTrack());
        boolean hovered = thumb.contains(mouseX, mouseY);
        int thumbColor = draggingScrollbar || hovered ? style.colorThumbHover() : style.colorThumb();
        context.fill(thumb.x, thumb.y, thumb.right(), thumb.bottom(), thumbColor);
        ConfigUiDraw.drawOutline(context, track.x, track.y, track.width, track.height, style.colorBorder());
    }

    private int layoutRight() {
        return viewport.x + layoutWidth();
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampScrollOffset(int value) {
        return Math.max(0, Math.min(maxOffset, value));
    }

    private boolean tryScrollBy(int delta) {
        if (delta == 0) {
            return false;
        }

        int rawNext = scrollOffset + delta;
        int next = clampScrollOffset(rawNext);
        if (next == scrollOffset && rawNext == next) {
            return false;
        }

        if (rawNext != next) {
            applyWheelOverscroll(delta);
        }

        applyScrollOffset(next, false);
        return true;
    }

    private void applyScrollOffset(int nextOffset, boolean immediateVisual) {
        scrollOffset = nextOffset;
        sectionScrollOffsets[activeSectionIndex] = nextOffset;
        if (immediateVisual) {
            visualOffset = nextOffset;
            if (!draggingByMouse) {
                elasticOffset = 0.0;
                elasticVelocity = 0.0;
            }
        }
        onOffsetApplied.run();
    }

    private void applyWheelOverscroll(int delta) {
        double nextElastic = elasticOffset + (-delta) * OVER_SCROLL_WHEEL_OFFSET_FACTOR;
        elasticOffset = clampDouble(nextElastic, -OVER_SCROLL_MAX, OVER_SCROLL_MAX);
        elasticVelocity += (-delta) * OVER_SCROLL_WHEEL_VELOCITY_FACTOR;
        elasticVelocity = clampDouble(elasticVelocity, -900.0, 900.0);
    }

    private UiRect scrollbarTrackRect() {
        if (viewport.width <= 0 || viewport.height <= 0 || maxOffset <= 0) {
            return null;
        }
        return new UiRect(
                viewport.right() - scrollbarWidth,
                viewport.y,
                scrollbarWidth,
                viewport.height
        );
    }

    private UiRect scrollbarThumbRect() {
        UiRect track = scrollbarTrackRect();
        if (track == null) {
            return null;
        }

        int thumbHeight = Math.max(
                scrollbarMinThumbHeight,
                (int) Math.round(track.height * (track.height / (double) (track.height + maxOffset)))
        );
        thumbHeight = Math.min(track.height, thumbHeight);
        int travel = Math.max(0, track.height - thumbHeight);
        double thumbOffset = clampDouble(visualOffset, 0.0, maxOffset);
        int thumbY = travel == 0
                ? track.y
                : track.y + (int) Math.round((thumbOffset / Math.max(1.0, maxOffset)) * travel);
        return new UiRect(track.x, thumbY, track.width, thumbHeight);
    }

    private int scrollOffsetFromThumbMouseY(double mouseY) {
        UiRect track = scrollbarTrackRect();
        UiRect thumb = scrollbarThumbRect();
        if (track == null || thumb == null) {
            return scrollOffset;
        }

        int travel = Math.max(1, track.height - thumb.height);
        double anchorY = mouseY - thumb.height / 2.0;
        double ratio = (anchorY - track.y) / travel;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return clampScrollOffset((int) Math.round(ratio * maxOffset));
    }

    private void startScrollbarDrag() {
        draggingScrollbar = true;
        draggingByMouse = false;
        elasticOffset = 0.0;
        elasticVelocity = 0.0;
    }

    public record Style(
            int colorTrack,
            int colorThumb,
            int colorThumbHover,
            int colorBorder
    ) {
    }
}
