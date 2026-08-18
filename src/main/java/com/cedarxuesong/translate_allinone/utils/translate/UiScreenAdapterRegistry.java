package com.cedarxuesong.translate_allinone.utils.translate;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class UiScreenAdapterRegistry {
    private static final List<SoftAdapterRegistration> REGISTRATIONS = new ArrayList<>();

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
    }

    public static UiScreenAdapter resolve(Screen screen) {
        if (screen == null) {
            return null;
        }
        String className = screen.getClass().getName();
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


