package com.cedarxuesong.translate_allinone.utils.translate;

import com.cedarxuesong.translate_allinone.Translate_AllinOne;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class WynncraftDictionaryInstaller {
    static final String RESOURCE_DIRECTORY = "assets/translate_allinone/lang/dictionary";
    private static final String CONFIG_DIRECTORY_NAME = "dictionary";

    private WynncraftDictionaryInstaller() {
    }

    public static void ensureInstalled() {
        Path sourceRoot = resolveBundledDictionaryDirectory();
        if (sourceRoot == null) {
            Translate_AllinOne.LOGGER.warn(
                    "Bundled Wynncraft dictionary directory not found: {}",
                    RESOURCE_DIRECTORY
            );
            return;
        }

        Path targetRoot = resolveConfigDictionaryDirectory();
        if (targetRoot == null) {
            Translate_AllinOne.LOGGER.warn("Wynncraft dictionary config directory is unavailable, skipping install.");
            return;
        }
        try {
            Files.createDirectories(targetRoot);

            AtomicInteger copiedCount = new AtomicInteger();
            AtomicInteger skippedCount = new AtomicInteger();
            List<Path> sourcePaths;
            try (var stream = Files.walk(sourceRoot)) {
                sourcePaths = stream.toList();
            }

            for (Path sourcePath : sourcePaths) {
                Path relativePath = sourceRoot.relativize(sourcePath);
                Path targetPath = relativePath.toString().isEmpty()
                        ? targetRoot
                        : targetRoot.resolve(relativePath.toString());

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                    continue;
                }

                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                if (Files.exists(targetPath)) {
                    skippedCount.incrementAndGet();
                    continue;
                }

                Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
                copiedCount.incrementAndGet();
            }

            Translate_AllinOne.LOGGER.info(
                    "Ensured Wynncraft dictionary directory in config. copied={}, preserved={}, path={}",
                    copiedCount.get(),
                    skippedCount.get(),
                    targetRoot
            );
        } catch (IOException | RuntimeException e) {
            Translate_AllinOne.LOGGER.error(
                    "Failed to install Wynncraft dictionary directory into config path: {}",
                    targetRoot,
                    e
            );
        }
    }

    static Path resolveConfigDictionaryDirectory() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            if (configDir == null) {
                return null;
            }
            return configDir
                    .resolve(Translate_AllinOne.MOD_ID)
                    .resolve(CONFIG_DIRECTORY_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Path resolveConfigDictionaryFile(String fileName) {
        Path configDirectory = resolveConfigDictionaryDirectory();
        if (configDirectory == null) {
            return null;
        }
        if (fileName == null || fileName.isBlank()) {
            return configDirectory;
        }
        return configDirectory.resolve(fileName);
    }

    private static Path resolveBundledDictionaryDirectory() {
        return FabricLoader.getInstance()
                .getModContainer(Translate_AllinOne.MOD_ID)
                .flatMap(container -> container.findPath(RESOURCE_DIRECTORY))
                .orElse(null);
    }
}
