package com.cedarxuesong.translate_allinone.utils.config.pojos;

public class CacheBackupConfig {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int MIN_BACKUP_INTERVAL_MINUTES = 1;
    public static final int MAX_BACKUP_INTERVAL_MINUTES = 10080;
    public static final int DEFAULT_BACKUP_INTERVAL_MINUTES = 120;

    public static final int MIN_MAX_BACKUP_COUNT = 1;
    public static final int MAX_MAX_BACKUP_COUNT = 10080;
    public static final int DEFAULT_MAX_BACKUP_COUNT = 5;

    public Boolean enabled = DEFAULT_ENABLED;
    public int backup_interval_minutes = DEFAULT_BACKUP_INTERVAL_MINUTES;
    public int max_backup_count = DEFAULT_MAX_BACKUP_COUNT;

    public boolean isEnabled() {
        return enabled == null ? DEFAULT_ENABLED : enabled;
    }
}
