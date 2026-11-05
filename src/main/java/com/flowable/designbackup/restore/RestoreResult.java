package com.flowable.designbackup.restore;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a full restore run.
 */
public record RestoreResult(
        String workspaceKey,
        boolean workspaceCreated,
        String sourceDirectory,
        Instant startedAt,
        Instant finishedAt,
        int totalApps,
        int successCount,
        int failureCount,
        List<AppRestoreResult> apps) {

    public RestoreResult {
        apps = apps == null ? List.of() : List.copyOf(apps);
    }
}
