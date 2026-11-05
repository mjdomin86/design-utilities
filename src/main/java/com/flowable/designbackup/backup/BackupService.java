package com.flowable.designbackup.backup;

import tools.jackson.databind.ObjectMapper;
import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import com.flowable.designbackup.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads every app of a workspace as a zip into
 * {@code {targetDir}/{workspaceKey}/{timestamp}/} and writes a manifest.json
 * describing the run. A failure on one app never aborts the others.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final String MANIFEST_FILE = "manifest.json";

    private final DesignApiClient designApiClient;
    private final BackupProperties backupProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BackupService(DesignApiClient designApiClient, BackupProperties backupProperties,
            ObjectMapper objectMapper, Clock clock) {
        this.designApiClient = designApiClient;
        this.backupProperties = backupProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BackupResult performBackup(String workspaceKey, String targetDirectory) {
        Instant startedAt = clock.instant();
        Path backupDir = prepareBackupDirectory(workspaceKey, targetDirectory, startedAt);

        List<ModelSummary> apps = designApiClient.listApps(workspaceKey);
        List<AppBackupResult> results = new ArrayList<>();
        for (ModelSummary app : apps) {
            results.add(backupSingleApp(workspaceKey, app, backupDir));
        }

        int successCount = (int) results.stream().filter(AppBackupResult::success).count();
        BackupResult result = new BackupResult(workspaceKey, backupDir.toString(), startedAt, clock.instant(),
                results.size(), successCount, results.size() - successCount, results);
        writeManifest(backupDir, result);
        log.info("Backup of workspace '{}' finished: {}/{} apps saved to {}",
                workspaceKey, successCount, results.size(), backupDir);
        return result;
    }

    private Path prepareBackupDirectory(String workspaceKey, String targetDirectory, Instant startedAt) {
        Path target = AllowedDirectoryGuard.resolve(targetDirectory, backupProperties.allowedBaseDir(),
                BackupProperties.ALLOWED_BASE_DIR_PROPERTY);
        Path backupDir = target.resolve(workspaceKey).resolve(TIMESTAMP_FORMAT.format(startedAt));
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create backup directory " + backupDir, e);
        }
        return backupDir;
    }

    private AppBackupResult backupSingleApp(String workspaceKey, ModelSummary app, Path backupDir) {
        String fileName = app.key() + ".zip";
        try {
            byte[] zip = designApiClient.exportApp(workspaceKey, app.key());
            if (zip == null || zip.length == 0) {
                return AppBackupResult.failure(app.key(), app.name(), "Export returned no content");
            }
            Files.write(backupDir.resolve(fileName), zip);
            return AppBackupResult.success(app.key(), app.name(), fileName, zip.length);
        } catch (Exception e) {
            log.warn("Backup of app '{}' in workspace '{}' failed", app.key(), workspaceKey, e);
            return AppBackupResult.failure(app.key(), app.name(), e.getMessage());
        }
    }

    private void writeManifest(Path backupDir, BackupResult result) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            Files.writeString(backupDir.resolve(MANIFEST_FILE), json);
        } catch (IOException e) {
            throw new UncheckedIOException("Backup completed but manifest.json could not be written to "
                    + backupDir, e);
        }
    }
}
