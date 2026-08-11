package com.cedarxuesong.translate_allinone.utils.translate;

import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.BuiltinEmptyGlyph;
import net.minecraft.client.font.GlyphMetrics;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.math.random.Random;

public final class WynnDialogueFontFallback {
    private static final String NAMEPLATE_PATH = "hud/dialogue/text/nameplate";
    private static final String BODY_PREFIX = "hud/dialogue/text/wynncraft/body_";
    private static final String CHOICE_ROOT = "hud/dialogue/text/wynncraft/choice";
    private static final int VERTICAL_ANCHOR_CODE_POINT = 'A';

    private WynnDialogueFontFallback() {
    }

    public static boolean isDialogueFont(StyleSpriteSource source) {
        if (!(source instanceof StyleSpriteSource.Font font)) {
            return false;
        }
        String path = font.id().getPath();
        return NAMEPLATE_PATH.equals(path)
                || path.startsWith(BODY_PREFIX)
                || path.startsWith(CHOICE_ROOT);
    }

    public static GlyphProvider provider(TextRenderer.GlyphsProvider fonts, Style style) {
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        GlyphProvider target = fonts.getGlyphs(resolvedStyle.getFont());
        if (!isDialogueFont(resolvedStyle.getFont())) {
            return target;
        }
        return provider(target, fonts.getGlyphs(StyleSpriteSource.DEFAULT));
    }

    static GlyphProvider provider(GlyphProvider target, GlyphProvider fallback) {
        return provider(target, fallback, verticalOffset(target, fallback));
    }

    static GlyphProvider provider(GlyphProvider target, GlyphProvider fallback, float verticalOffset) {
        return new FallbackGlyphProvider(target, fallback, verticalOffset);
    }

    public static boolean supports(TextRenderer.GlyphsProvider fonts, String value, Style style) {
        if (value == null || value.isBlank()) {
            return true;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        GlyphProvider target = fonts.getGlyphs(resolvedStyle.getFont());
        boolean fallbackAllowed = isDialogueFont(resolvedStyle.getFont());
        GlyphProvider fallback = fallbackAllowed ? fonts.getGlyphs(StyleSpriteSource.DEFAULT) : target;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && isMissing(target.get(codePoint))) {
                if (!fallbackAllowed || isMissing(fallback.get(codePoint))) {
                    return false;
                }
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public static int measure(TextRenderer.GlyphsProvider fonts, String value, Style style) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        GlyphProvider glyphs = provider(fonts, resolvedStyle);
        float width = 0.0F;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += glyphs.get(codePoint).getMetrics().getAdvance(resolvedStyle.isBold());
            offset += Character.charCount(codePoint);
        }
        return (int)Math.ceil(width);
    }

    private static float verticalOffset(GlyphProvider target, GlyphProvider fallback) {
        BakedGlyph targetAnchor = target.get(VERTICAL_ANCHOR_CODE_POINT);
        BakedGlyph fallbackAnchor = fallback.get(VERTICAL_ANCHOR_CODE_POINT);
        if (isMissing(targetAnchor) || isMissing(fallbackAnchor)) {
            return 0.0F;
        }
        TextDrawable.DrawnGlyphRect targetRect = targetAnchor.create(
                0.0F,
                0.0F,
                -1,
                0,
                Style.EMPTY,
                0.0F,
                0.0F
        );
        TextDrawable.DrawnGlyphRect fallbackRect = fallbackAnchor.create(
                0.0F,
                0.0F,
                -1,
                0,
                Style.EMPTY,
                0.0F,
                0.0F
        );
        if (targetRect == null || fallbackRect == null) {
            return 0.0F;
        }
        return targetRect.getTop() - fallbackRect.getTop();
    }

    private static boolean isMissing(BakedGlyph glyph) {
        return glyph == null || glyph.getMetrics() == BuiltinEmptyGlyph.MISSING;
    }

    private record FallbackGlyphProvider(
            GlyphProvider target,
            GlyphProvider fallback,
            float verticalOffset
    ) implements GlyphProvider {
        @Override
        public BakedGlyph get(int codePoint) {
            BakedGlyph targetGlyph = target.get(codePoint);
            if (!isMissing(targetGlyph)) {
                return targetGlyph;
            }
            BakedGlyph fallbackGlyph = fallback.get(codePoint);
            if (isMissing(fallbackGlyph)) {
                return targetGlyph;
            }
            return new ShiftedGlyph(fallbackGlyph, verticalOffset);
        }

        @Override
        public BakedGlyph getObfuscated(Random random, int width) {
            return target.getObfuscated(random, width);
        }
    }

    private record ShiftedGlyph(BakedGlyph delegate, float verticalOffset) implements BakedGlyph {
        @Override
        public GlyphMetrics getMetrics() {
            return delegate.getMetrics();
        }

        @Override
        public TextDrawable.DrawnGlyphRect create(
                float x,
                float y,
                int color,
                int shadowColor,
                Style style,
                float boldOffset,
                float shadowOffset
        ) {
            return delegate.create(
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
