package com.alexeys.translate_allinone.utils.cache.component;

import com.alexeys.translate_allinone.utils.cache.CacheBackupManager;

public final class ComponentCacheMigrationManager {
    public static final String MIGRATION_ID = ComponentCacheMigrationService.MIGRATION_ID;

    private ComponentCacheMigrationManager() {
    }

    public static synchronized void migrateLegacyCaches(ComponentTranslationStoreRegistry registry) {
        if (registry == null) {
            return;
        }
        ComponentCacheMigrationService.migrateLegacyCaches(
                CacheBackupManager.getCacheDirectory(),
                CacheBackupManager.getComponentCacheDirectory(),
                registry::ensureInitialized
        );
    }
}
