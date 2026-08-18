package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.utils.text.LegacyComponentTextCodec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class NvgAnimatedTextRenderer {
    private static final int HORIZONTAL_MASK = 0x07;
    private static final int NVG_ALIGN_LEFT = 1;
    private static final int NVG_ALIGN_CENTER = 2;
    private static final int NVG_ALIGN_RIGHT = 4;
    private static final int NVG_ALIGN_TOP = 8;

    private static Method NVG_TEXT;
    private static Method NVG_TEXT_BOUNDS;
    private static Method NVG_TEXT_BOX;
    private static Method NVG_TEXT_ALIGN_GET;
    private static Method NVG_TEXT_ALIGN_SET;
    private static Method NVG_FILL_COLOR;
    private static Method NVG_RGBA;
    private static Class<?> NVG_COLOR_CLASS;
    private static Method NVG_COLOR_CALLOC;
    private static Method NVG_COLOR_SET;
    private static Method NVG_COLOR_FREE;
    private static Object NVG_COLOR;
    private static boolean INITIALIZED;

    private NvgAnimatedTextRenderer() {
    }

    public static float drawText(long vg, float x, float y, String text) {
        if (text == null || text.isEmpty()) {
            return callText(vg, x, y, text == null ? "" : text);
        }
        if (text.indexOf('\u00A7') < 0) {
            return callText(vg, x, y, text);
        }
        try {
            return drawStyledText(vg, x, y, text);
        } catch (RuntimeException ignored) {
            return callText(vg, x, y, text);
        }
    }

    public static void drawTextBox(long vg, float x, float y, float breakRowWidth, String text) {
        if (text == null || text.isEmpty()) {
            callTextBox(vg, x, y, breakRowWidth, text == null ? "" : text);
            return;
        }
        if (text.indexOf('\u00A7') < 0) {
            callTextBox(vg, x, y, breakRowWidth, text);
            return;
        }
        try {
            drawStyledTextBox(vg, x, y, breakRowWidth, text);
        } catch (RuntimeException ignored) {
            callTextBox(vg, x, y, breakRowWidth, text);
        }
    }

    private static float drawStyledText(long vg, float x, float y, String text) {
        int oldAlign = getTextAlign(vg);
        return drawStyledCharacters(vg, x, y, parse(text), oldAlign);
    }

    private static float drawStyledCharacters(long vg, float x, float y, List<StyledChar> chars, int oldAlign) {
        if (chars.isEmpty()) {
            return x;
        }
        float width = measureWidth(vg, plain(chars));
        float startX = x - width * alignmentFactor(oldAlign);
        int leftAlign = oldAlign < 0 ? NVG_ALIGN_LEFT | NVG_ALIGN_TOP : (oldAlign & ~HORIZONTAL_MASK) | NVG_ALIGN_LEFT;
        setTextAlign(vg, leftAlign);
        float cursor = startX;
        try {
            for (StyledChar c : chars) {
                setFill(vg, c.r(), c.g(), c.b(), 1.0f);
                cursor = callText(vg, cursor, y, c.text());
            }
            return startX + width;
        } finally {
            setTextAlign(vg, oldAlign);
        }
    }

    private static void drawStyledTextBox(long vg, float x, float y, float breakRowWidth, String text) {
        List<StyledChar> chars = parse(text);
        List<List<StyledChar>> lines = wrap(chars, vg, breakRowWidth);
        float lineHeight = measureHeight(vg);
        int oldAlign = getTextAlign(vg);
        float currentY = y;
        for (List<StyledChar> line : lines) {
            if (!line.isEmpty()) {
                drawStyledCharacters(vg, x, currentY, line, oldAlign);
            }
            currentY += lineHeight;
        }
    }

    private static List<List<StyledChar>> wrap(List<StyledChar> chars, long vg, float breakRowWidth) {
        List<List<StyledChar>> tokens = tokenize(chars);
        List<List<StyledChar>> lines = new ArrayList<>();
        List<StyledChar> line = new ArrayList<>();
        float lineWidth = 0.0f;
        for (List<StyledChar> token : tokens) {
            if (token.size() == 1 && "\n".equals(token.get(0).text())) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                line = new ArrayList<>();
                lineWidth = 0.0f;
                continue;
            }
            float tokenWidth = measureWidth(vg, plain(token));
            boolean whitespace = token.size() == 1 && Character.isWhitespace(token.get(0).text().codePointAt(0));
            if (!whitespace && !line.isEmpty() && lineWidth + tokenWidth > breakRowWidth) {
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0.0f;
            }
            line.addAll(token);
            lineWidth += tokenWidth;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add(new ArrayList<>());
        }
        return lines;
    }

    private static List<List<StyledChar>> tokenize(List<StyledChar> chars) {
        List<List<StyledChar>> tokens = new ArrayList<>();
        List<StyledChar> current = new ArrayList<>();
        boolean currentWhitespace = false;
        boolean hasCurrent = false;
        for (StyledChar c : chars) {
            String text = c.text();
            if ("\n".equals(text)) {
                if (hasCurrent) {
                    tokens.add(current);
                    current = new ArrayList<>();
                    hasCurrent = false;
                }
                tokens.add(List.of(c));
                continue;
            }
            boolean whitespace = Character.isWhitespace(text.codePointAt(0));
            if (!hasCurrent) {
                current = new ArrayList<>();
                current.add(c);
                currentWhitespace = whitespace;
                hasCurrent = true;
            } else if (whitespace != currentWhitespace) {
                tokens.add(current);
                current = new ArrayList<>();
                current.add(c);
                currentWhitespace = whitespace;
            } else {
                current.add(c);
            }
        }
        if (hasCurrent) {
            tokens.add(current);
        }
        return tokens;
    }

    private static List<StyledChar> parse(String text) {
        List<StyledChar> chars = new ArrayList<>();
        Text component = LegacyComponentTextCodec.decode(text);
        component.visit((style, value) -> {
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            Style resolved = style == null ? Style.EMPTY : style;
            int rgb = resolved.getColor() == null ? 0xFFFFFF : resolved.getColor().getRgb();
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;
            for (int offset = 0; offset < value.length(); ) {
                int codePoint = value.codePointAt(offset);
                chars.add(new StyledChar(new String(Character.toChars(codePoint)), r, g, b));
                offset += Character.charCount(codePoint);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return chars;
    }

    private static String plain(List<StyledChar> chars) {
        StringBuilder builder = new StringBuilder();
        for (StyledChar c : chars) {
            builder.append(c.text());
        }
        return builder.toString();
    }

    private static float alignmentFactor(int align) {
        if (align < 0) {
            return 0.0f;
        }
        int horizontal = align & HORIZONTAL_MASK;
        if (horizontal == NVG_ALIGN_CENTER) {
            return 0.5f;
        }
        if (horizontal == NVG_ALIGN_RIGHT) {
            return 1.0f;
        }
        return 0.0f;
    }

    private static float measureWidth(long vg, String text) {
        try {
            ensureInitialized();
            Object result = NVG_TEXT_BOUNDS.invoke(null, vg, 0.0f, 0.0f, text, new float[4]);
            return ((Number) result).floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0.0f;
        }
    }

    private static float measureHeight(long vg) {
        try {
            ensureInitialized();
            float[] bounds = new float[4];
            NVG_TEXT_BOUNDS.invoke(null, vg, 0.0f, 0.0f, "Ag", bounds);
            float height = bounds[3] - bounds[1];
            return height > 0.0f ? height + 2.0f : 10.0f;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 10.0f;
        }
    }

    private static void setFill(long vg, float r, float g, float b, float a) {
        try {
            ensureInitialized();
            if (NVG_COLOR == null) {
                NVG_COLOR = NVG_COLOR_CALLOC.invoke(null);
            }
            if (NVG_COLOR_SET != null) {
                try {
                    NVG_COLOR_SET.invoke(NVG_COLOR, r, g, b, a);
                } catch (ReflectiveOperationException setFailed) {
                    if (NVG_RGBA != null) {
                        NVG_RGBA.invoke(null, r, g, b, a, NVG_COLOR);
                    }
                }
            } else if (NVG_RGBA != null) {
                NVG_RGBA.invoke(null, r, g, b, a, NVG_COLOR);
            }
            NVG_FILL_COLOR.invoke(null, vg, NVG_COLOR);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static int getTextAlign(long vg) {
        try {
            ensureInitialized();
            if (NVG_TEXT_ALIGN_GET == null) {
                return -1;
            }
            Object result = NVG_TEXT_ALIGN_GET.invoke(null, vg);
            return ((Number) result).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static void setTextAlign(long vg, int align) {
        if (align < 0 || NVG_TEXT_ALIGN_SET == null) {
            return;
        }
        try {
            NVG_TEXT_ALIGN_SET.invoke(null, vg, align);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static float callText(long vg, float x, float y, String text) {
        try {
            ensureInitialized();
            Object result = NVG_TEXT.invoke(null, vg, x, y, text);
            return ((Number) result).floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0.0f;
        }
    }

    private static void callTextBox(long vg, float x, float y, float breakRowWidth, String text) {
        try {
            ensureInitialized();
            NVG_TEXT_BOX.invoke(null, vg, x, y, breakRowWidth, text);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void ensureInitialized() throws ReflectiveOperationException {
        if (INITIALIZED) {
            return;
        }
        Class<?> nvg = Class.forName("org.lwjgl.nanovg.NanoVG");
        NVG_TEXT = nvg.getMethod("nvgText", long.class, float.class, float.class, CharSequence.class);
        NVG_TEXT_BOUNDS = nvg.getMethod("nvgTextBounds", long.class, float.class, float.class, CharSequence.class, float[].class);
        NVG_TEXT_BOX = nvg.getMethod("nvgTextBox", long.class, float.class, float.class, float.class, CharSequence.class);
        try {
            NVG_TEXT_ALIGN_GET = nvg.getMethod("nvgTextAlign", long.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            NVG_TEXT_ALIGN_SET = nvg.getMethod("nvgTextAlign", long.class, int.class);
        } catch (NoSuchMethodException ignored) {
        }
        NVG_COLOR_CLASS = Class.forName("org.lwjgl.nanovg.NVGColor");
        NVG_COLOR_CALLOC = NVG_COLOR_CLASS.getMethod("calloc");
        try {
            NVG_COLOR_SET = NVG_COLOR_CLASS.getMethod("set", float.class, float.class, float.class, float.class);
        } catch (NoSuchMethodException ignored) {
        }
        NVG_COLOR_FREE = NVG_COLOR_CLASS.getMethod("free");
        for (Method method : nvg.getMethods()) {
            if ("nvgFillColor".equals(method.getName())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[1] == NVG_COLOR_CLASS) {
                NVG_FILL_COLOR = method;
            }
            if ("nvgRGBAf".equals(method.getName())
                    && method.getParameterCount() == 5
                    && method.getParameterTypes()[4] == NVG_COLOR_CLASS) {
                NVG_RGBA = method;
            }
        }
        INITIALIZED = true;
    }

    private record StyledChar(String text, float r, float g, float b) {
    }
}