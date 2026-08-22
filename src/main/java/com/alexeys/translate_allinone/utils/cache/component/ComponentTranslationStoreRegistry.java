package com.alexeys.translate_allinone.utils.cache.component;

import com.alexeys.translate_allinone.utils.cache.CacheBackupManager;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationDebugLogger;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ComponentTranslationStoreRegistry {
    private static final ComponentTranslationStore.BackupAccess BACKUP_ACCESS = new ComponentTranslationStore.BackupAccess() {
        @Override
        public void maybeBackup(Path cacheFilePath, String cacheTypeLabel) {
            CacheBackupManager.maybeBackup(cacheFilePath, cacheTypeLabel);
        }

        @Override
        public List<Path> findVerifiedBackups(Path cacheFilePath) {
            return CacheBackupManager.findVerifiedComponentCacheBackups(cacheFilePath);
        }
    };
    private static final ComponentTranslationStore.Diagnostics DIAGNOSTICS = new ComponentTranslationStore.Diagnostics() {
        @Override
        public void flowForNamespace(String namespace, String message, Object... arguments) {
            ComponentTranslationDebugLogger.flowForNamespace(namespace, message, arguments);
        }

        @Override
        public void error(ComponentTranslationRoute route, String message, Object... arguments) {
            ComponentTranslationDebugLogger.error(route, message, arguments);
        }
    };
    private final Map<ComponentCacheModule, ComponentTranslationStore> stores;

    private ComponentTranslationStoreRegistry() {
        this(CacheBackupManager.getComponentCacheDirectory(), true);
    }

    ComponentTranslationStoreRegistry(Path cacheDirectory) {
        this(cacheDirectory, false);
    }

    private ComponentTranslationStoreRegistry(Path cacheDirectory, boolean passiveBackupEnabled) {
        Map<ComponentCacheModule, ComponentTranslationStore> configured = new EnumMap<>(ComponentCacheModule.class);
        for (ComponentCacheModule module : ComponentCacheModule.values()) {
            configured.put(module, new ComponentTranslationStore(
                    cacheDirectory,
                    module,
                    passiveBackupEnabled,
                    BACKUP_ACCESS,
                    DIAGNOSTICS
            ));
        }
        stores = Map.copyOf(configured);
    }

    public static ComponentTranslationStoreRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public ComponentTranslationStore forRoute(ComponentTranslationRoute route) {
        return stores.get(ComponentCacheModule.forRoute(route));
    }

    public ComponentTranslationStore forModule(ComponentCacheModule module) {
        return stores.get(module);
    }

    public void load() {
        stores.values().forEach(ComponentTranslationStore::load);
    }

    public void save() {
        stores.values().forEach(ComponentTranslationStore::save);
    }

    public void endSession() {
        stores.values().forEach(ComponentTranslationStore::endSession);
    }

    public void ensureInitialized() {
        stores.values().forEach(ComponentTranslationStore::ensureInitialized);
    }

    private static final class Holder {
        private static final ComponentTranslationStoreRegistry INSTANCE = new ComponentTranslationStoreRegistry();
    }
}
