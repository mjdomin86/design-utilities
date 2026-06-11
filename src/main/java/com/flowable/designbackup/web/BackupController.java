package com.flowable.designbackup.web;

import com.flowable.designbackup.backup.BackupRequest;
import com.flowable.designbackup.backup.BackupResult;
import com.flowable.designbackup.backup.BackupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual backup trigger. Runs synchronously and returns the full result,
 * including per-app failures.
 */
@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    public BackupResult startBackup(@Valid @RequestBody BackupRequest request) {
        return backupService.performBackup(request.workspaceKey(), request.targetDirectory());
    }
}
