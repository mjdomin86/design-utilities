package com.flowable.designbackup.restore;

import com.flowable.designbackup.backup.AllowedDirectoryGuard;
import com.flowable.designbackup.backup.AppBackupResult;
import com.flowable.designbackup.backup.BackupResult;
import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Restores app export zips (as produced by {@link
 * com.flowable.designbackup.backup.BackupService}) from a local directory
 * into a Flowable Design workspace. Each app is imported independently with
 * {@code overwriteOnClash}, so an app that already exists in the target
 * workspace is replaced and one that doesn't is created; a failure on one
 * app never aborts the others.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);
    private static final String DEFAULT_WORKSPACE_KEY = "default";
    private static final String ZIP_EXTENSION = ".zip";
    private static final String MANIFEST_FILE = "manifest.json";

    private final DesignApiClient designApiClient;
    private final BackupProperties backupProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RestoreService(DesignApiClient designApiClient, BackupProperties backupProperties,
            ObjectMapper objectMapper, Clock clock) {
        this.designApiClient = designApiClient;
        this.backupProperties = backupProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public RestoreResult performRestore(RestoreRequest request) {
        Instant startedAt = clock.instant();
        Path sourceDir = resolveSourceDirectory(request.sourceDirectory());
        String workspaceKey = resolveWorkspaceKey(request);

        if (request.createWorkspace()) {
            designApiClient.createWorkspace(workspaceKey, request.workspaceName());
        }

        Map<String, String> appNamesByKey = readAppNames(sourceDir);
        List<AppRestoreResult> results = new ArrayList<>();
        for (Path zipFile : listZipFiles(sourceDir)) {
            results.add(restoreSingleApp(workspaceKey, zipFile, appNamesByKey));
        }

        int successCount = (int) results.stream().filter(AppRestoreResult::success).count();
        RestoreResult result = new RestoreResult(workspaceKey, request.createWorkspace(), sourceDir.toString(),
                startedAt, clock.instant(), results.size(), successCount, results.size() - successCount, results);
        log.info("Restore into workspace '{}' finished: {}/{} apps restored from {}",
                workspaceKey, successCount, results.size(), sourceDir);
        return result;
    }

    private String resolveWorkspaceKey(RestoreRequest request) {
        if (request.createWorkspace()) {
            if (!StringUtils.hasText(request.workspaceKey())) {
                throw new IllegalArgumentException("workspaceKey is required when createWorkspace is true");
            }
            return request.workspaceKey();
        }
        return StringUtils.hasText(request.workspaceKey()) ? request.workspaceKey() : DEFAULT_WORKSPACE_KEY;
    }

    private Path resolveSourceDirectory(String sourceDirectory) {
        Path source = AllowedDirectoryGuard.resolve(sourceDirectory, backupProperties.allowedBaseDir(),
                BackupProperties.ALLOWED_BASE_DIR_PROPERTY);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException(
                    "Source directory " + source + " does not exist or is not a directory");
        }
        return source;
    }

    private List<Path> listZipFiles(Path sourceDir) {
        try (Stream<Path> files = Files.list(sourceDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(ZIP_EXTENSION))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list zip files in " + sourceDir, e);
        }
    }

    /**
     * Reads app names from a prior backup's manifest.json, if present, purely
     * so restore results can show friendly names; a manifest-less directory
     * (or an unparsable one) is restored just the same, with names omitted.
     */
    private Map<String, String> readAppNames(Path sourceDir) {
        Path manifestFile = sourceDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestFile)) {
            return Map.of();
        }
        try {
            BackupResult manifest = objectMapper.readValue(manifestFile.toFile(), BackupResult.class);
            Map<String, String> names = new LinkedHashMap<>();
            for (AppBackupResult app : manifest.apps()) {
                names.put(app.appKey(), app.appName());
            }
            return names;
        } catch (Exception e) {
            log.warn("Could not read {}, restoring without app names", manifestFile, e);
            return Map.of();
        }
    }

    private AppRestoreResult restoreSingleApp(String workspaceKey, Path zipFile, Map<String, String> appNamesByKey) {
        String fileName = zipFile.getFileName().toString();
        String appKey = fileName.substring(0, fileName.length() - ZIP_EXTENSION.length());
        String appName = appNamesByKey.get(appKey);
        try {
            byte[] zip = Files.readAllBytes(zipFile);
            designApiClient.importApp(workspaceKey, zip, fileName);
            return AppRestoreResult.success(appKey, appName, fileName, zip.length);
        } catch (Exception e) {
            log.warn("Restore of app '{}' into workspace '{}' failed", appKey, workspaceKey, e);
            return AppRestoreResult.failure(appKey, appName, fileName, e.getMessage());
        }
    }
}
