package com.flowable.designbackup.backup;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.DesignApiException;
import com.flowable.designbackup.client.ModelSummary;
import com.flowable.designbackup.config.BackupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupServiceTest {

    private static final byte[] ZIP_BYTES = {0x50, 0x4b, 0x03, 0x04, 0x42};

    @TempDir
    Path tempDir;

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-11T10:15:30Z"), ZoneOffset.UTC);

    private BackupService service;

    @BeforeEach
    void setUp() {
        BackupProperties properties = new BackupProperties(null,
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        service = new BackupService(client, properties, JsonMapper.builder().build(), clock);
    }

    @Test
    void backsUpAllAppsAndWritesManifest() throws Exception {
        // Arrange
        when(client.listApps("ws1")).thenReturn(List.of(
                new ModelSummary("1", "app902", "App 902", "app"),
                new ModelSummary("2", "app903", "App 903", "app")));
        when(client.exportApp("ws1", "app902")).thenReturn(ZIP_BYTES);
        when(client.exportApp("ws1", "app903")).thenReturn(ZIP_BYTES);

        // Act
        BackupResult result = service.performBackup("ws1", tempDir.toString());

        // Assert
        assertThat(result.totalApps()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
        Path backupDir = Path.of(result.directory());
        assertThat(backupDir).startsWith(tempDir.resolve("ws1"));
        assertThat(backupDir.resolve("app902.zip")).hasBinaryContent(ZIP_BYTES);
        assertThat(backupDir.resolve("app903.zip")).exists();
        assertThat(Files.readString(backupDir.resolve("manifest.json")))
                .contains("\"workspaceKey\" : \"ws1\"")
                .contains("app902.zip");
    }

    @Test
    void singleAppFailureDoesNotAbortTheBackup() {
        when(client.listApps("ws1")).thenReturn(List.of(
                new ModelSummary("1", "good", "Good", "app"),
                new ModelSummary("2", "bad", "Bad", "app")));
        when(client.exportApp("ws1", "good")).thenReturn(ZIP_BYTES);
        when(client.exportApp("ws1", "bad")).thenThrow(new DesignApiException("HTTP 500", 500, null));

        BackupResult result = service.performBackup("ws1", tempDir.toString());

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        AppBackupResult failed = result.apps().stream()
                .filter(app -> !app.success()).findFirst().orElseThrow();
        assertThat(failed.appKey()).isEqualTo("bad");
        assertThat(failed.error()).contains("HTTP 500");
        assertThat(Path.of(result.directory()).resolve("good.zip")).exists();
    }

    @Test
    void emptyExportIsRecordedAsFailure() {
        when(client.listApps("ws1")).thenReturn(List.of(new ModelSummary("1", "empty", "Empty", "app")));
        when(client.exportApp("ws1", "empty")).thenReturn(new byte[0]);

        BackupResult result = service.performBackup("ws1", tempDir.toString());

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.apps().getFirst().error()).contains("no content");
    }

    @Test
    void rejectsTargetDirectoryOutsideAllowedBaseDir() {
        BackupProperties restricted = new BackupProperties(tempDir.resolve("allowed").toString(),
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        BackupService restrictedService =
                new BackupService(client, restricted, JsonMapper.builder().build(), clock);

        assertThatThrownBy(() -> restrictedService.performBackup("ws1", tempDir.resolve("elsewhere").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed base directory");
    }

    @Test
    void rejectsSymlinkEscapingAllowedBaseDir() throws Exception {
        Path allowed = Files.createDirectories(tempDir.resolve("allowed"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path link = Files.createSymbolicLink(allowed.resolve("link"), outside);
        BackupProperties restricted = new BackupProperties(allowed.toString(),
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        BackupService restrictedService =
                new BackupService(client, restricted, JsonMapper.builder().build(), clock);

        assertThatThrownBy(() -> restrictedService.performBackup("ws1", link.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed base directory");
    }

    @Test
    void acceptsTargetDirectoryInsideAllowedBaseDir() {
        BackupProperties restricted = new BackupProperties(tempDir.toString(),
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        BackupService restrictedService =
                new BackupService(client, restricted, JsonMapper.builder().build(), clock);
        when(client.listApps("ws1")).thenReturn(List.of());

        BackupResult result = restrictedService.performBackup("ws1", tempDir.resolve("sub").toString());

        assertThat(result.totalApps()).isZero();
        assertThat(Path.of(result.directory())).exists();
    }
}
