package com.alexeys.translate_allinone.utils.translate;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;

public final class WynnDialogueFontFallback {
    private static final String NAMEPLATE_PATH = "hud/dialogue/text/nameplate";
    private static final String BODY_PREFIX = "hud/dialogue/text/wynncraft/body_";
    private static final String CHOICE_ROOT = "hud/dialogue/text/wynncraft/choice";
    private static final int VERTICAL_ANCHOR_CODE_POINT = 'A';

    private WynnDialogueFontFallback() {
    }

    public static boolean isDialogueFont(FontDescription source) {
        if (!(source instanceof FontDescription.Resource font)) {
            return false;
        }
        String path = font.id().getPath();
        return NAMEPLATE_PATH.equals(path)
                || path.startsWith(BODY_PREFIX)
                || path.startsWith(CHOICE_ROOT);
    }

    public static GlyphSource provider(Font.Provider fonts, Style style) {
        if (fonts == null) {
            return null;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        FontDescription source = resolvedStyle.getFont();
        GlyphSource target = fonts.glyphs(source);
        if (!isDialogueFont(source)) {
            return target;
        }
        return provider(target, fonts.glyphs(FontDescription.DEFAULT));
    }

    static GlyphSource provider(GlyphSource target, GlyphSource fallback) {
        return provider(target, fallback, verticalOffset(target, fallback));
    }

    static GlyphSource provider(GlyphSource target, GlyphSource fallback, float verticalOffset) {
        if (target == null || fallback == null || target == fallback) {
            return target;
        }
        return new FallbackGlyphSource(target, fallback, verticalOffset);
    }

    public static boolean supports(Font.Provider fonts, String value, Style style) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (fonts == null) {
            return false;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        FontDescription source = resolvedStyle.getFont();
        GlyphSource target = fonts.glyphs(source);
        boolean fallbackAllowed = isDialogueFont(source);
        GlyphSource fallback = fallbackAllowed ? fonts.glyphs(FontDescription.DEFAULT) : target;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && isMissing(target == null ? null : target.getGlyph(codePoint))) {
                if (!fallbackAllowed || isMissing(fallback == null ? null : fallback.getGlyph(codePoint))) {
                    return false;
                }
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public static int measure(Font.Provider fonts, String value, Style style) {
        if (value == null || value.isEmpty() || fonts == null) {
            return 0;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        GlyphSource glyphs = provider(fonts, resolvedStyle);
        if (glyphs == null) {
            return 0;
        }
        float width = 0.0F;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            BakedGlyph glyph = glyphs.getGlyph(codePoint);
            if (glyph != null) {
                width += glyph.info().getAdvance(resolvedStyle.isBold());
            }
            offset += Character.charCount(codePoint);
        }
        return (int) Math.ceil(width);
    }

    private static float verticalOffset(GlyphSource target, GlyphSource fallback) {
        if (target == null || fallback == null) {
            return 0.0F;
        }
        BakedGlyph targetAnchor = target.getGlyph(VERTICAL_ANCHOR_CODE_POINT);
        BakedGlyph fallbackAnchor = fallback.getGlyph(VERTICAL_ANCHOR_CODE_POINT);
        if (isMissing(targetAnchor) || isMissing(fallbackAnchor)) {
            return 0.0F;
        }
        TextRenderable.Styled targetRenderable = targetAnchor.createGlyph(
                0.0F,
                0.0F,
                -1,
                0,
                Style.EMPTY,
                0.0F,
                0.0F
        );
        TextRenderable.Styled fallbackRenderable = fallbackAnchor.createGlyph(
                0.0F,
                0.0F,
                -1,
                0,
                Style.EMPTY,
                0.0F,
                0.0F
        );
        if (targetRenderable == null || fallbackRenderable == null) {
            return 0.0F;
        }
        return targetRenderable.top() - fallbackRenderable.top();
    }

    private static boolean isMissing(BakedGlyph glyph) {
        return glyph == null || glyph.info() == SpecialGlyphs.MISSING;
    }

    private record FallbackGlyphSource(
            GlyphSource target,
            GlyphSource fallback,
            float verticalOffset
    ) implements GlyphSource {
        @Override
        public BakedGlyph getGlyph(int codePoint) {
            BakedGlyph targetGlyph = target.getGlyph(codePoint);
            if (!isMissing(targetGlyph)) {
                return targetGlyph;
            }
            BakedGlyph fallbackGlyph = fallback.getGlyph(codePoint);
            if (isMissing(fallbackGlyph)) {
                return targetGlyph;
            }
            return new ShiftedGlyph(fallbackGlyph, verticalOffset);
        }

        @Override
        public BakedGlyph getRandomGlyph(RandomSource random, int width) {
            return target.getRandomGlyph(random, width);
        }
    }

    private record ShiftedGlyph(BakedGlyph delegate, float verticalOffset) implements BakedGlyph {
        @Override
        public GlyphInfo info() {
            return delegate.info();
        }

        @Override
        public TextRenderable.Styled createGlyph(
                float x,
                float y,
                int color,
                int shadowColor,
                Style style,
                float boldOffset,
                float shadowOffset
        ) {
            return delegate.createGlyph(
                    x,
                    y + verticalOffset,
                    color,
                    shadowColor,
                    style,
                    boldOffset,
                    shadowOffset
            );
        }
    }
}
