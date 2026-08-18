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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UiOdinFontFallback {
    private static final String FALLBACK_FONT_NAME = "translate_allinone_cjk_fallback";
    private static final String NVG_RENDERER_CLASS = "com.odtheking.odin.utils.ui.rendering.NVGRenderer";

    private static final List<ByteBuffer> KEPT_BUFFERS = new ArrayList<>();
    private static final Map<Long, Set<Integer>> PATCHED_FONTS_BY_CONTEXT = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> FALLBACK_FONT_BY_CONTEXT = new ConcurrentHashMap<>();

    private UiOdinFontFallback() {
    }

    public static void attachFallback(int fontId) {
        attachFallback(NVG_RENDERER_CLASS, fontId);
    }

    public static void attachFallback(String rendererClassName, int fontId) {
        if (rendererClassName == null || rendererClassName.isBlank() || fontId < 0) {
            return;
        }
        Long vg = currentContext(rendererClassName);
        if (vg == null || vg <= 0) {
            return;
        }
        int fallbackId = ensureFallbackFont(vg);
        if (fallbackId < 0 || fallbackId == fontId) {
            return;
        }
        Set<Integer> patched = PATCHED_FONTS_BY_CONTEXT.computeIfAbsent(vg, key -> ConcurrentHashMap.newKeySet());
        if (patched.add(fontId)) {
            try {
                addFallbackFontId(vg, fontId, fallbackId);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static synchronized int ensureFallbackFont(long vg) {
        Integer existing = FALLBACK_FONT_BY_CONTEXT.get(vg);
        if (existing != null) {
            return existing;
        }
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
                    FALLBACK_FONT_BY_CONTEXT.put(vg, id);
                    KEPT_BUFFERS.add(buffer);
                    return id;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return -1;
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
        return currentContext(NVG_RENDERER_CLASS);
    }

    private static Long currentContext(String rendererClassName) {
        try {
            Class<?> clazz = Class.forName(rendererClassName);
            Object instance = null;
            try {
                Field instanceField = clazz.getDeclaredField("INSTANCE");
                instanceField.setAccessible(true);
                instance = instanceField.get(null);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Method method = clazz.getDeclaredMethod("access$getVg$p");
                method.setAccessible(true);
                Object value = method.invoke(instance);
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            try {
                Field field = clazz.getDeclaredField("vg");
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(instance);
                } catch (IllegalArgumentException notStaticWithoutInstance) {
                    Field instanceField = clazz.getDeclaredField("INSTANCE");
                    instanceField.setAccessible(true);
                    value = field.get(instanceField.get(null));
                }
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
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
