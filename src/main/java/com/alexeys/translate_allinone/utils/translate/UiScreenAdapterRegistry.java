package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UiScreenAdapterRegistry {
    private static final List<SoftAdapterRegistration> REGISTRATIONS = new ArrayList<>();
    private static final Map<Class<?>, Optional<UiScreenAdapter>> RESOLVED_CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> AUTOMATIC_MOD_EXCLUSIONS = Set.of(
            "minecraft",
            "fabricloader",
            Translate_AllinOne.MOD_ID
    );

    static {
        registerMod(
                "odin",
                "com.odtheking.odin.clickgui.",
                "",
                UiScreenAdapter.Backend.NANOVG,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "noammaddons",
                "com.github.noamm9.ui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "modmenu",
                "com.terraformersmc.modmenu.gui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "cloth-config",
                "me.shedaniel.clothconfig2.gui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "yet_another_config_lib_v3",
                "dev.isxander.yacl3.gui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "owo",
                "io.wispforest.owo.config.ui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "athen",
                "foo.starred.athen.config.ui.",
                "",
                UiScreenAdapter.Backend.NANOVG,
                Set.of(UiTextRole.values())
        );
        registerMod(
                "devonian",
                "com.github.synnerz.devonian.config.ui.",
                "",
                UiScreenAdapter.Backend.MINECRAFT_FONT,
                Set.of(UiTextRole.values())
        );
    }

    private UiScreenAdapterRegistry() {
    }

    public static void registerMod(
            String modId,
            String packagePrefix,
            String simpleNameSuffix,
            UiScreenAdapter.Backend backend,
            Set<UiTextRole> roles
    ) {
        if (modId == null || modId.isBlank() || packagePrefix == null || packagePrefix.isBlank()) {
            return;
        }
        REGISTRATIONS.add(new SoftAdapterRegistration(
                modId.trim().toLowerCase(Locale.ROOT),
                packagePrefix.trim(),
                simpleNameSuffix == null ? "" : simpleNameSuffix.trim(),
                backend,
                roles == null ? Set.of(UiTextRole.values()) : Set.copyOf(roles)
        ));
        RESOLVED_CLASSES.clear();
    }

    public static UiScreenAdapter resolve(Screen screen) {
        return screen == null ? null : resolve(screen.getClass());
    }

    public static UiScreenAdapter resolve(Class<?> screenClass) {
        if (screenClass == null) {
            return null;
        }
        return RESOLVED_CLASSES.computeIfAbsent(
                screenClass,
                type -> Optional.ofNullable(resolveClass(type))
        ).orElse(null);
    }

    public static UiScreenAdapter resolve(String className) {
        return resolveRegistered(className);
    }

    private static UiScreenAdapter resolveClass(Class<?> screenClass) {
        UiScreenAdapter registered = resolveRegistered(screenClass.getName());
        if (registered != null) {
            return registered;
        }
        try {
            return resolveAutomatic(screenClass, FabricLoader.getInstance().getAllMods());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static UiScreenAdapter resolveRegistered(String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        for (SoftAdapterRegistration registration : REGISTRATIONS) {
            if (!isModLoaded(registration.modId())) {
                continue;
            }
            if (matches(className, registration)) {
                return new UiScreenAdapter(
                        registration.modId(),
                        className,
                        registration.backend(),
                        registration.roles()
                );
            }
        }
        return null;
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

    private static boolean isModLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean matches(String className, SoftAdapterRegistration registration) {
        String prefix = registration.packagePrefix();
        if (!prefix.isEmpty() && className.startsWith(prefix)) {
            return true;
        }
        String suffix = registration.simpleNameSuffix();
        if (!suffix.isEmpty()) {
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            if (simpleName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private record SoftAdapterRegistration(
            String modId,
            String packagePrefix,
            String simpleNameSuffix,
            UiScreenAdapter.Backend backend,
            Set<UiTextRole> roles
    ) {
        private SoftAdapterRegistration {
            modId = modId == null ? "" : modId.trim().toLowerCase(Locale.ROOT);
            packagePrefix = packagePrefix == null ? "" : packagePrefix.trim();
            simpleNameSuffix = simpleNameSuffix == null ? "" : simpleNameSuffix.trim();
            backend = backend == null ? UiScreenAdapter.Backend.COMPONENT : backend;
        }
    }
}
