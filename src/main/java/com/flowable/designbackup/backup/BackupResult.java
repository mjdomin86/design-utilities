package com.flowable.designbackup.backup;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a full workspace backup run; also serialized as manifest.json
 * inside the backup directory.
 */
public record BackupResult(
        String workspaceKey,
        String directory,
        Instant startedAt,
        Instant finishedAt,
        int totalApps,
        int successCount,
        int failureCount,
        List<AppBackupResult> apps) {

    public BackupResult {
        apps = apps == null ? List.of() : List.copyOf(apps);
    }
}
