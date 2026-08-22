package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.font.BuiltinEmptyGlyph;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class WynnDialogueFontFallback {
    private static final String NAMEPLATE_PATH = "hud/dialogue/text/nameplate";
    private static final String BODY_PREFIX = "hud/dialogue/text/wynncraft/body_";
    private static final String CHOICE_ROOT = "hud/dialogue/text/wynncraft/choice";
    private static final int VERTICAL_ANCHOR_CODE_POINT = 'A';

    private WynnDialogueFontFallback() {
    }

    public static boolean isDialogueFont(Identifier source) {
        if (source == null) {
            return false;
        }
        String path = source.getPath();
        return NAMEPLATE_PATH.equals(path)
                || path.startsWith(BODY_PREFIX)
                || path.startsWith(CHOICE_ROOT);
    }

    public static FontStorage fallbackStorage(FontStorage target, FontStorage fallback) {
        return new FallbackFontStorage(target, fallback);
    }

    public static boolean supports(Function<Identifier, FontStorage> fonts, String value, Style style) {
        if (value == null || value.isBlank()) {
            return true;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        Identifier font = resolvedStyle.getFont();
        FontStorage target = fonts.apply(font);
        boolean fallbackAllowed = isDialogueFont(font);
        FontStorage fallback = fallbackAllowed ? fonts.apply(Style.DEFAULT_FONT_ID) : target;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && isMissing(target.getGlyph(codePoint, false))) {
                if (!fallbackAllowed || isMissing(fallback.getGlyph(codePoint, false))) {
                    return false;
                }
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public static int measure(Function<Identifier, FontStorage> fonts, String value, Style style) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        float width = 0.0F;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            width += glyph(fonts, resolvedStyle, codePoint, false).getAdvance(resolvedStyle.isBold());
            offset += Character.charCount(codePoint);
        }
        return (int)Math.ceil(width);
    }

    public static float advance(
            Function<Identifier, FontStorage> fonts,
            Style style,
            int codePoint,
            boolean validateAdvance
    ) {
        Style resolvedStyle = style == null ? Style.EMPTY : style;
        return glyph(fonts, resolvedStyle, codePoint, validateAdvance).getAdvance(resolvedStyle.isBold());
    }

    private static Glyph glyph(
            Function<Identifier, FontStorage> fonts,
            Style style,
            int codePoint,
            boolean validateAdvance
    ) {
        Identifier font = style.getFont();
        FontStorage target = fonts.apply(font);
        Glyph targetGlyph = target.getGlyph(codePoint, validateAdvance);
        if (!isDialogueFont(font) || !isMissing(targetGlyph)) {
            return targetGlyph;
        }
        Glyph fallbackGlyph = fonts.apply(Style.DEFAULT_FONT_ID).getGlyph(codePoint, validateAdvance);
        return isMissing(fallbackGlyph) ? targetGlyph : fallbackGlyph;
    }

    private static boolean isMissing(Glyph glyph) {
        return glyph == null || glyph == BuiltinEmptyGlyph.MISSING;
    }

    private static final class FallbackFontStorage extends FontStorage {
        private final FontStorage target;
        private final FontStorage fallback;
        private final Set<Glyph> fallbackGlyphs = Collections.newSetFromMap(new IdentityHashMap<>());

        private FallbackFontStorage(FontStorage target, FontStorage fallback) {
            super(null, Style.DEFAULT_FONT_ID);
            this.target = target;
            this.fallback = fallback;
        }

        @Override
        public Glyph getGlyph(int codePoint, boolean validateAdvance) {
            Glyph targetGlyph = target.getGlyph(codePoint, validateAdvance);
            if (!isMissing(targetGlyph)) {
                return targetGlyph;
            }
            Glyph fallbackGlyph = fallback.getGlyph(codePoint, validateAdvance);
            if (isMissing(fallbackGlyph)) {
                return targetGlyph;
            }
            fallbackGlyphs.add(fallbackGlyph);
            return fallbackGlyph;
        }

        @Override
        public GlyphRenderer getGlyphRenderer(int codePoint) {
            Glyph targetGlyph = target.getGlyph(codePoint, false);
            if (!isMissing(targetGlyph)) {
                return target.getGlyphRenderer(codePoint);
            }
            Glyph fallbackGlyph = fallback.getGlyph(codePoint, false);
            return isMissing(fallbackGlyph)
                    ? target.getGlyphRenderer(codePoint)
                    : fallback.getGlyphRenderer(codePoint);
        }

        @Override
        public GlyphRenderer getObfuscatedGlyphRenderer(Glyph glyph) {
            return fallbackGlyphs.contains(glyph)
                    ? fallback.getObfuscatedGlyphRenderer(glyph)
                    : target.getObfuscatedGlyphRenderer(glyph);
        }

        @Override
        public GlyphRenderer getRectangleRenderer() {
            return target.getRectangleRenderer();
        }

        @Override
        public void setFonts(List<Font> fonts) {
        }

        @Override
        public void close() {
        }
    }
}
