package com.cedarxuesong.translate_allinone.utils.cache.component;

import com.cedarxuesong.translate_allinone.utils.cache.CacheBackupManager;
import com.cedarxuesong.translate_allinone.utils.componentjson.ComponentTranslationRoute;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class ComponentTranslationStoreRegistry {
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
            configured.put(module, new ComponentTranslationStore(cacheDirectory, module, passiveBackupEnabled));
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

    public void ensureInitialized() {
        stores.values().forEach(ComponentTranslationStore::ensureInitialized);
    }

    private static final class Holder {
        private static final ComponentTranslationStoreRegistry INSTANCE = new ComponentTranslationStoreRegistry();
    }
}
