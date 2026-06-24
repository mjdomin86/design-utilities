package com.flowable.designbackup.usage;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.DesignApiException;
import com.flowable.designbackup.client.ModelRelationsSummary;
import com.flowable.designbackup.client.ModelSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnusedModelServiceTest {

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final UnusedModelService service = new UnusedModelService(client);

    @Test
    void reportsOnlyModelsReferencedByNoPackageAndNoModel() {
        // Arrange
        when(client.listAllModels("ws1")).thenReturn(List.of(
                new ModelSummary("1", "orphan", "Orphan", "form"),
                new ModelSummary("2", "usedByModel", "Used By Model", "form"),
                new ModelSummary("3", "usedByApp", "Used By App", "service")));
        when(client.getParentRelationsSummary("ws1", "form", "orphan"))
                .thenReturn(new ModelRelationsSummary(false, 0, 0));
        when(client.getParentRelationsSummary("ws1", "form", "usedByModel"))
                .thenReturn(new ModelRelationsSummary(false, 0, 2));
        when(client.getParentRelationsSummary("ws1", "service", "usedByApp"))
                .thenReturn(new ModelRelationsSummary(true, 1, 0));

        // Act
        UnusedModelsResponse response = service.findUnusedModels("ws1", List.of());

        // Assert
        assertThat(response.scannedModels()).isEqualTo(3);
        assertThat(response.unusedModels()).extracting(UnusedModel::modelKey).containsExactly("orphan");
    }

    @Test
    void excludesPackageTypesFromTheScan() {
        when(client.listAllModels("ws1")).thenReturn(List.of(
                new ModelSummary("1", "app902", "App 902", "app"),
                new ModelSummary("2", "plugin1", "Plugin 1", "plugin"),
                new ModelSummary("3", "orphan", "Orphan", "form")));
        when(client.getParentRelationsSummary("ws1", "form", "orphan"))
                .thenReturn(new ModelRelationsSummary(false, 0, 0));

        UnusedModelsResponse response = service.findUnusedModels("ws1", List.of());

        assertThat(response.scannedModels()).isEqualTo(1);
        assertThat(response.unusedModels()).extracting(UnusedModel::modelKey).containsExactly("orphan");
        verify(client, never()).getParentRelationsSummary("ws1", "app", "app902");
        verify(client, never()).getParentRelationsSummary("ws1", "plugin", "plugin1");
    }

    @Test
    void scansOnlyRequestedModelTypesWhenProvided() {
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "orphan", "Orphan", "form")));
        when(client.getParentRelationsSummary("ws1", "form", "orphan"))
                .thenReturn(new ModelRelationsSummary(false, 0, 0));

        UnusedModelsResponse response = service.findUnusedModels("ws1", List.of("form"));

        assertThat(response.unusedModels()).extracting(UnusedModel::modelKey).containsExactly("orphan");
        verify(client, never()).listAllModels("ws1");
    }

    @Test
    void usageLookupFailuresAreCountedNotFatal() {
        when(client.listAllModels("ws1")).thenReturn(List.of(
                new ModelSummary("1", "orphan", "Orphan", "form"),
                new ModelSummary("2", "boom", "Boom", "form")));
        when(client.getParentRelationsSummary("ws1", "form", "orphan"))
                .thenReturn(new ModelRelationsSummary(false, 0, 0));
        when(client.getParentRelationsSummary("ws1", "form", "boom"))
                .thenThrow(new DesignApiException("boom", 500, null));

        UnusedModelsResponse response = service.findUnusedModels("ws1", List.of());

        assertThat(response.scannedModels()).isEqualTo(2);
        assertThat(response.erroredModels()).isEqualTo(1);
        assertThat(response.unusedModels()).extracting(UnusedModel::modelKey).containsExactly("orphan");
    }
}
