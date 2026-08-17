package com.cedarxuesong.translate_allinone.utils.translate;


import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaches a CJK-capable fallback font to Odin's NanoVG fonts.
 *
 * <p>Odin's click GUI renders with a custom Latin TTF through NanoVG. Translated
 * CJK text therefore shows as boxes because the custom font has no matching glyphs.
 * NanoVG supports per-font fallback chains, so we register a system CJK font once
 * and add it as a fallback to every font Odin creates.</p>
 */
public final class UiOdinFontFallback {
    private static final String FALLBACK_FONT_NAME = "translate_allinone_cjk_fallback";
    private static final String NVG_RENDERER_CLASS = "com.odtheking.odin.utils.ui.rendering.NVGRenderer";

    private static final List<ByteBuffer> KEPT_BUFFERS = new ArrayList<>();
    private static final Set<Integer> PATCHED_FONTS = ConcurrentHashMap.newKeySet();

    private static volatile boolean attempted;
    private static volatile int fallbackFontId = -1;

    private UiOdinFontFallback() {
    }

    public static void attachFallback(int fontId) {
        if (fontId < 0) {
            return;
        }
        Long vg = currentContext();
        if (vg == null || vg <= 0) {
            return;
        }
        ensureFallbackFont(vg);
        if (fallbackFontId < 0 || fallbackFontId == fontId) {
            return;
        }
        if (PATCHED_FONTS.add(fontId)) {
            try {
                addFallbackFontId(vg, fontId, fallbackFontId);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static synchronized void ensureFallbackFont(long vg) {
        if (attempted) {
            return;
        }
        attempted = true;
        for (Path path : candidateFontPaths()) {
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (bytes.length == 0) {
                    continue;
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.put(bytes).flip();
                int id = createFontMem(vg, FALLBACK_FONT_NAME, buffer);
                if (id >= 0) {
                    fallbackFontId = id;
                    KEPT_BUFFERS.add(buffer);
                    return;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private static int createFontMem(long vg, String name, ByteBuffer buffer) {
        try {
            Class<?> clazz = Class.forName("org.lwjgl.nanovg.NanoVG");
            Method method = clazz.getMethod("nvgCreateFontMem", long.class, CharSequence.class, ByteBuffer.class, boolean.class);
            return (Integer) method.invoke(null, vg, name, buffer, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static void addFallbackFontId(long vg, int fontId, int fallbackId) {
        try {
            Class<?> clazz = Class.forName("org.lwjgl.nanovg.NanoVG");
            Method method = clazz.getMethod("nvgAddFallbackFontId", long.class, int.class, int.class);
            method.invoke(null, vg, fontId, fallbackId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Long currentContext() {
        try {
            Class<?> clazz = Class.forName(NVG_RENDERER_CLASS);
            try {
                Method method = clazz.getMethod("access$getVg$p");
                return (Long) method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
                Field field = clazz.getDeclaredField("vg");
                field.setAccessible(true);
                return field.getLong(null);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static List<Path> candidateFontPaths() {
        List<Path> paths = new ArrayList<>();
        addIfExists(paths,
                "C:\\Windows\\Fonts\\msyh.ttc",
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\simsun.ttc",
                "C:\\Windows\\Fonts\\Deng.ttf",
                "C:\\Windows\\Fonts\\msyhl.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/arphic/uming.ttc",
                "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/STHeiti Light.ttc",
                "/System/Library/Fonts/Hiragino Sans GB.ttc"
        );
        return paths;
    }

    private static void addIfExists(List<Path> paths, String... candidates) {
        for (String candidate : candidates) {
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path)) {
                paths.add(path);
            }
        }
    }
}
