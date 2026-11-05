package com.flowable.designbackup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Backup behavior settings.
 *
 * <pre>
 * flowable.design.backup.allowed-base-dir=/path/under/which/backups/may/be/written
 * flowable.design.backup.scheduled.enabled=true
 * flowable.design.backup.scheduled.cron=0 0 2 * * *
 * flowable.design.backup.scheduled.workspace-key=default
 * flowable.design.backup.scheduled.target-directory=/path/to/backups
 * </pre>
 */
@ConfigurationProperties(prefix = "flowable.design.backup")
public record BackupProperties(
        String allowedBaseDir,
        @DefaultValue Scheduled scheduled) {

    /**
     * Name of {@link #allowedBaseDir()}, referenced in error messages raised
     * by {@link com.flowable.designbackup.backup.AllowedDirectoryGuard} for
     * both backup (write) and restore (read) directories.
     */
    public static final String ALLOWED_BASE_DIR_PROPERTY = "flowable.design.backup.allowed-base-dir";

    public record Scheduled(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0 0 2 * * *") String cron,
            String workspaceKey,
            String targetDirectory) {
    }
}
