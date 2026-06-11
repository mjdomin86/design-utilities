package com.flowable.designbackup.backup;

import com.flowable.designbackup.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Runs the workspace backup on a cron schedule when
 * {@code flowable.design.backup.scheduled.enabled=true}. Fails fast at startup
 * when the scheduled workspace or target directory is missing.
 */
@Component
@ConditionalOnProperty(prefix = "flowable.design.backup.scheduled", name = "enabled", havingValue = "true")
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupProperties.Scheduled scheduled;

    public BackupScheduler(BackupService backupService, BackupProperties backupProperties) {
        this.backupService = backupService;
        this.scheduled = backupProperties.scheduled();
        if (!StringUtils.hasText(scheduled.workspaceKey()) || !StringUtils.hasText(scheduled.targetDirectory())) {
            throw new IllegalStateException("Scheduled backups require "
                    + "'flowable.design.backup.scheduled.workspace-key' and "
                    + "'flowable.design.backup.scheduled.target-directory'");
        }
    }

    @Scheduled(cron = "${flowable.design.backup.scheduled.cron:0 0 2 * * *}")
    public void runScheduledBackup() {
        log.info("Starting scheduled backup of workspace '{}'", scheduled.workspaceKey());
        try {
            backupService.performBackup(scheduled.workspaceKey(), scheduled.targetDirectory());
        } catch (Exception e) {
            log.error("Scheduled backup of workspace '{}' failed", scheduled.workspaceKey(), e);
        }
    }
}
