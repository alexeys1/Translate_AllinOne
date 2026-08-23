package com.alexeys.translate_allinone.utils.translate;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UiScreenAdapterRegistryTest {
    @Test
    void resolvesScreenFromOwningMod() {
        Class<?> externalScreenClass = org.junit.jupiter.api.Test.class;
        String resource = classResource(externalScreenClass);

        UiScreenAdapter adapter = UiScreenAdapterRegistry.resolveAutomatic(
                externalScreenClass,
                List.of(container("Example_Mod", resource))
        );

        assertEquals("example_mod", adapter.modId());
        assertEquals(externalScreenClass.getName().toLowerCase(Locale.ROOT), adapter.screenId());
        assertEquals(UiScreenAdapter.Backend.MINECRAFT_FONT, adapter.backend());
    }

    @Test
    void skipsContainersThatDoNotOwnTheScreen() {
        UiScreenAdapter adapter = UiScreenAdapterRegistry.resolveAutomatic(
                OwnedScreen.class,
                List.of(container("example_mod", "other/Screen.class"))
        );

        assertNull(adapter);
    }

    @Test
    void excludesMinecraftAndTranslateAllInOneScreens() {
        assertNull(UiScreenAdapterRegistry.resolveAutomatic(
                Screen.class,
                List.of(container("example_mod", classResource(Screen.class)))
        ));
        assertNull(UiScreenAdapterRegistry.resolveAutomatic(
                OwnedScreen.class,
                List.of(container("translate_allinone", classResource(OwnedScreen.class)))
        ));
    }

    private static String classResource(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static ModContainer container(String modId, String ownedResource) {
        ModMetadata metadata = (ModMetadata) Proxy.newProxyInstance(
                ModMetadata.class.getClassLoader(),
                new Class<?>[]{ModMetadata.class},
                (proxy, method, arguments) -> {
                    if ("getId".equals(method.getName())) {
                        return modId;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        return new ModContainer() {
            @Override
            public ModMetadata getMetadata() {
                return metadata;
            }

            @Override
            public List<Path> getRootPaths() {
                return List.of();
            }

            @Override
            public Optional<Path> findPath(String file) {
                return ownedResource.equals(file) ? Optional.of(Path.of(file)) : Optional.empty();
            }

            @Override
            public ModOrigin getOrigin() {
                return null;
            }

            @Override
            public Optional<ModContainer> getContainingMod() {
                return Optional.empty();
            }

            @Override
            public Collection<ModContainer> getContainedMods() {
                return List.of();
            }

            @Override
            public Path getRootPath() {
                return Path.of(".");
            }

            @Override
            public Path getPath(String file) {
                return Path.of(file);
            }
        };
    }

    private static final class OwnedScreen {
    }
}
