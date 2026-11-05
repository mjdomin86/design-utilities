package com.flowable.designbackup.restore;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.DesignApiException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RestoreServiceTest {

    private static final byte[] ZIP_BYTES = {0x50, 0x4b, 0x03, 0x04, 0x42};

    @TempDir
    Path tempDir;

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-11T10:15:30Z"), ZoneOffset.UTC);

    private RestoreService service;

    @BeforeEach
    void setUp() {
        BackupProperties properties = new BackupProperties(null,
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        service = new RestoreService(client, properties, JsonMapper.builder().build(), clock);
    }

    @Test
    void restoresAllZipsAndUsesManifestNames() throws Exception {
        // Arrange
        Files.write(tempDir.resolve("app902.zip"), ZIP_BYTES);
        Files.write(tempDir.resolve("app903.zip"), ZIP_BYTES);
        Files.writeString(tempDir.resolve("manifest.json"), """
                {"workspaceKey":"ws1","directory":"/x","startedAt":"2026-06-11T10:00:00Z",
                 "finishedAt":"2026-06-11T10:01:00Z","totalApps":2,"successCount":2,"failureCount":0,
                 "apps":[{"appKey":"app902","appName":"App 902","fileName":"app902.zip",
                          "sizeBytes":5,"success":true,"error":null},
                         {"appKey":"app903","appName":"App 903","fileName":"app903.zip",
                          "sizeBytes":5,"success":true,"error":null}]}
                """);

        // Act
        RestoreResult result = service.performRestore(
                new RestoreRequest(tempDir.toString(), "ws1", false, null));

        // Assert
        assertThat(result.workspaceKey()).isEqualTo("ws1");
        assertThat(result.workspaceCreated()).isFalse();
        assertThat(result.totalApps()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.apps()).extracting(AppRestoreResult::appName)
                .containsExactlyInAnyOrder("App 902", "App 903");
        verify(client).importApp(eq("ws1"), any(), eq("app902.zip"));
        verify(client).importApp(eq("ws1"), any(), eq("app903.zip"));
        verify(client, never()).createWorkspace(any(), any());
    }

    @Test
    void defaultsToDefaultWorkspaceWhenWorkspaceKeyIsBlank() throws Exception {
        Files.write(tempDir.resolve("app902.zip"), ZIP_BYTES);

        RestoreResult result = service.performRestore(new RestoreRequest(tempDir.toString(), "  ", false, null));

        assertThat(result.workspaceKey()).isEqualTo("default");
        verify(client).importApp(eq("default"), any(), eq("app902.zip"));
    }

    @Test
    void createsWorkspaceWhenRequested() throws Exception {
        Files.write(tempDir.resolve("app902.zip"), ZIP_BYTES);

        RestoreResult result = service.performRestore(
                new RestoreRequest(tempDir.toString(), "ws-new", true, "New Workspace"));

        assertThat(result.workspaceCreated()).isTrue();
        verify(client).createWorkspace("ws-new", "New Workspace");
        verify(client).importApp(eq("ws-new"), any(), eq("app902.zip"));
    }

    @Test
    void throwsWhenCreateWorkspaceRequestedWithoutKey() {
        assertThatThrownBy(() -> service.performRestore(new RestoreRequest(tempDir.toString(), " ", true, "Name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceKey");
        verifyNoInteractions(client);
    }

    @Test
    void restoresWithoutManifestUsingFileNamesOnly() throws Exception {
        Files.write(tempDir.resolve("app902.zip"), ZIP_BYTES);

        RestoreResult result = service.performRestore(
                new RestoreRequest(tempDir.toString(), "ws1", false, null));

        assertThat(result.apps()).hasSize(1);
        assertThat(result.apps().getFirst().appKey()).isEqualTo("app902");
        assertThat(result.apps().getFirst().appName()).isNull();
    }

    @Test
    void singleAppFailureDoesNotAbortTheRestore() throws Exception {
        Files.write(tempDir.resolve("good.zip"), ZIP_BYTES);
        Files.write(tempDir.resolve("bad.zip"), ZIP_BYTES);
        doThrow(new DesignApiException("HTTP 500", 500, null))
                .when(client).importApp(eq("ws1"), any(), eq("bad.zip"));

        RestoreResult result = service.performRestore(new RestoreRequest(tempDir.toString(), "ws1", false, null));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        AppRestoreResult failed = result.apps().stream().filter(app -> !app.success()).findFirst().orElseThrow();
        assertThat(failed.appKey()).isEqualTo("bad");
        assertThat(failed.error()).contains("HTTP 500");
    }

    @Test
    void rejectsSourceDirectoryOutsideAllowedBaseDir() {
        BackupProperties restricted = new BackupProperties(tempDir.resolve("allowed").toString(),
                new BackupProperties.Scheduled(false, "0 0 2 * * *", null, null));
        RestoreService restrictedService = new RestoreService(client, restricted, JsonMapper.builder().build(), clock);

        assertThatThrownBy(() -> restrictedService.performRestore(
                new RestoreRequest(tempDir.resolve("elsewhere").toString(), "ws1", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed base directory");
    }

    @Test
    void rejectsSourceDirectoryThatDoesNotExist() {
        assertThatThrownBy(() -> service.performRestore(
                new RestoreRequest(tempDir.resolve("missing").toString(), "ws1", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void emptyDirectoryRestoresZeroApps() {
        RestoreResult result = service.performRestore(new RestoreRequest(tempDir.toString(), "ws1", false, null));

        assertThat(result.totalApps()).isZero();
        assertThat(result.apps()).isEqualTo(List.of());
    }
}
