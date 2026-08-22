package com.alexeys.translate_allinone.utils.cache.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentCacheMigrationServiceTest {
    @TempDir
    Path cacheRoot;

    @Test
    void snapshotsInitializesAndArchivesLegacyCaches() throws IOException {
        Path scoreboard = cacheRoot.resolve("scoreboard_translate_cache.json");
        Path advancement = cacheRoot.resolve("other_translations_translate_cache.json");
        Files.writeString(scoreboard, "{\"scoreboard\":\"value\"}");
        Files.writeString(advancement, "{\"advancement\":\"value\"}");

        Path componentRoot = cacheRoot.resolve("translate_cache");
        Path migrationStateRoot = componentRoot
                .resolve("migration")
                .resolve(ComponentCacheMigrationService.MIGRATION_ID);
        AtomicBoolean initialized = new AtomicBoolean();

        ComponentCacheMigrationService.migrateLegacyCaches(cacheRoot, componentRoot, () -> {
            assertTrue(Files.isRegularFile(scoreboard));
            assertTrue(Files.isRegularFile(advancement));
            assertTrue(Files.isRegularFile(migrationStateRoot.resolve("pre_migration_manifest.json")));
            initialized.set(true);
        });

        assertTrue(initialized.get());
        assertFalse(Files.exists(scoreboard));
        assertFalse(Files.exists(advancement));
        assertTrue(Files.isRegularFile(componentRoot.resolve("migration/legacy/scoreboard_translate_cache.json")));
        assertTrue(Files.isRegularFile(componentRoot.resolve("migration/legacy/advancement_translate_cache.json")));
        assertTrue(Files.isRegularFile(migrationStateRoot.resolve("pre_migration_snapshot/scoreboard_translate_cache.json")));
        assertTrue(Files.isRegularFile(migrationStateRoot.resolve("pre_migration_snapshot/other_translations_translate_cache.json")));

        String state = Files.readString(migrationStateRoot.resolve("state.json"));
        assertTrue(state.contains("\"scoreboard_translate_cache.json\": \"archived\""));
        assertTrue(state.contains("\"other_translations_translate_cache.json\": \"archived\""));
    }
}
