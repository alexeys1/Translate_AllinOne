package com.cedarxuesong.translate_allinone.utils.cache;

import com.cedarxuesong.translate_allinone.utils.config.pojos.CacheBackupConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheBackupServiceTest {
    @TempDir
    Path cacheRoot;

    @Test
    void createsVerifiesAndRejectsTamperedComponentSnapshot() throws IOException {
        Path componentCache = cacheRoot.resolve("translate_cache").resolve("screen_ui_cache.json");
        Files.createDirectories(componentCache.getParent());
        Files.writeString(componentCache, "{\"value\":\"original\"}");

        CacheBackupConfig config = new CacheBackupConfig();
        config.backup_interval_minutes = 1;
        config.max_backup_count = 2;
        CacheBackupService service = new CacheBackupService(cacheRoot, () -> config);

        service.maybeBackup(componentCache, "screen UI");

        List<CacheBackupService.BackupDirectorySummary> backups = service.listManagedBackupDirectories();
        assertEquals(1, backups.size());
        assertEquals(List.of("translate_cache/screen_ui_cache.json"), backups.get(0).fileNames());

        List<Path> verified = service.findVerifiedComponentCacheBackups(componentCache);
        assertEquals(1, verified.size());
        assertEquals("{\"value\":\"original\"}", Files.readString(verified.get(0)));

        Files.writeString(verified.get(0), "{\"value\":\"tampered\"}");
        assertTrue(service.findVerifiedComponentCacheBackups(componentCache).isEmpty());
    }
}
