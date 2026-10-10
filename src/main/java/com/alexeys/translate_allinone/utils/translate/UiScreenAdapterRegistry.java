package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UiScreenAdapterRegistry {
    private static final Map<Class<?>, Optional<UiScreenAdapter>> RESOLVED_CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> AUTOMATIC_MOD_EXCLUSIONS = Set.of(
            "minecraft",
            "fabricloader",
            Translate_AllinOne.MOD_ID
    );

    private UiScreenAdapterRegistry() {
    }

    public static UiScreenAdapter resolve(Class<?> screenClass) {
        if (screenClass == null) {
            return null;
        }
        return RESOLVED_CLASSES.computeIfAbsent(
                screenClass,
                type -> Optional.ofNullable(resolveQuietly(type))
        ).orElse(null);
    }

    static UiScreenAdapter resolveAutomatic(Class<?> screenClass, Iterable<ModContainer> containers) {
        if (screenClass == null || containers == null) {
            return null;
        }
        String className = screenClass.getName();
        if (className.startsWith("net.minecraft.")
                || className.startsWith("com.alexeys.translate_allinone.")) {
            return null;
        }
        String classResource = className.replace('.', '/') + ".class";
        for (ModContainer container : containers) {
            try {
                String modId = container.getMetadata().getId().trim().toLowerCase(Locale.ROOT);
                if (AUTOMATIC_MOD_EXCLUSIONS.contains(modId)
                        || container.findPath(classResource).isEmpty()) {
                    continue;
                }
                return new UiScreenAdapter(
                        modId,
                        className,
                        UiScreenAdapter.Backend.MINECRAFT_FONT,
                        Set.of(UiTextRole.values())
                );
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static UiScreenAdapter resolveQuietly(Class<?> screenClass) {
        try {
            return resolveAutomatic(screenClass, FabricLoader.getInstance().getAllMods());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
