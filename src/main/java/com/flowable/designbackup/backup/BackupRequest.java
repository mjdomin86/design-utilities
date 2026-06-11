package com.flowable.designbackup.backup;

import jakarta.validation.constraints.NotBlank;

public record BackupRequest(
        @NotBlank(message = "workspaceKey is required") String workspaceKey,
        @NotBlank(message = "targetDirectory is required") String targetDirectory) {
}
