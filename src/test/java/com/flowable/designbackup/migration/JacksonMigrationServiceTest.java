package com.flowable.designbackup.migration;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.DesignApiException;
import com.flowable.designbackup.client.ModelRelationInfo;
import com.flowable.designbackup.client.ModelSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JacksonMigrationServiceTest {

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private JacksonMigrationService service;

    @BeforeEach
    void setUp() {
        service = new JacksonMigrationService(client);
    }

    @Test
    void scanOnlyReportsModelsWithJacksonReferences() {
        // Arrange
        when(client.listModels("ws1", "bpmn")).thenReturn(List.of(
                new ModelSummary("1", "clean", "Clean", "bpmn"),
                new ModelSummary("2", "dirty", "Dirty", "bpmn")));
        when(client.getModelContent("ws1", "bpmn", "clean")).thenReturn("no jackson here");
        when(client.getModelContent("ws1", "bpmn", "dirty"))
                .thenReturn("import com.fasterxml.jackson.databind.ObjectMapper");
        when(client.getParentRelations("ws1", "bpmn", "dirty")).thenReturn(List.of(
                new ModelRelationInfo("app1", "App One", "dirty", "Dirty", "bpmn")));

        // Act
        JacksonScanResponse response = service.scan(new JacksonScanRequest("ws1", List.of("bpmn")));

        // Assert
        assertThat(response.scannedModels()).isEqualTo(2);
        assertThat(response.erroredModels()).isZero();
        assertThat(response.matches()).hasSize(1);
        ModelJacksonIssue issue = response.matches().getFirst();
        assertThat(issue.modelKey()).isEqualTo("dirty");
        assertThat(issue.occurrences()).isEqualTo(1);
        assertThat(issue.needsManualReview()).isFalse();
        assertThat(issue.apps()).containsExactly(new com.flowable.designbackup.search.AppReference("app1", "App One"));
    }

    @Test
    void scanCountsUnreadableModelsAsErroredWithoutFailingTheWholeScan() {
        when(client.listModels("ws1", "bpmn")).thenReturn(List.of(new ModelSummary("1", "broken", "Broken", "bpmn")));
        when(client.getModelContent("ws1", "bpmn", "broken"))
                .thenThrow(new DesignApiException("HTTP 500", 500, null));

        JacksonScanResponse response = service.scan(new JacksonScanRequest("ws1", List.of("bpmn")));

        assertThat(response.scannedModels()).isEqualTo(1);
        assertThat(response.erroredModels()).isEqualTo(1);
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void fixRewritesStringShapedContentAndWritesItBack() {
        // Arrange: bpmn-style content where the model's "content" field is a JSON string.
        JsonNode stringContent = jsonMapper.readTree("\"import com.fasterxml.jackson.databind.ObjectMapper\"");
        when(client.getModelContentNode("ws1", "bpmn", "dirty")).thenReturn(stringContent);

        // Act
        JacksonFixResponse response = service.fix(
                new JacksonFixRequest("ws1", List.of(new ModelRef("bpmn", "dirty"))));

        // Assert
        assertThat(response.successCount()).isEqualTo(1);
        ModelFixResult result = response.results().getFirst();
        assertThat(result.success()).isTrue();
        assertThat(result.occurrencesFixed()).isEqualTo(1);
        verify(client).updateModelContent(eq("ws1"), eq("bpmn"), eq("dirty"), any());
    }

    @Test
    void fixRewritesObjectShapedContentAndReparsesItAsJson() {
        // Arrange: service-model-style content where "content" is a JSON object.
        JsonNode objectContent = jsonMapper.readTree(
                "{\"type\":\"com.fasterxml.jackson.databind.ObjectMapper\"}");
        when(client.getModelContentNode("ws1", "service", "svc1")).thenReturn(objectContent);

        // Act
        service.fix(new JacksonFixRequest("ws1", List.of(new ModelRef("service", "svc1"))));

        // Assert: the content sent back must be a parsed JSON object, not a quoted string.
        org.mockito.ArgumentCaptor<JsonNode> captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(client).updateModelContent(eq("ws1"), eq("service"), eq("svc1"), captor.capture());
        assertThat(captor.getValue().isObject()).isTrue();
        assertThat(captor.getValue().get("type").asString()).isEqualTo("tools.jackson.databind.ObjectMapper");
    }

    @Test
    void fixSkipsModelWithNoJacksonReferences() {
        JsonNode cleanContent = jsonMapper.readTree("\"no jackson here\"");
        when(client.getModelContentNode("ws1", "bpmn", "clean")).thenReturn(cleanContent);

        JacksonFixResponse response = service.fix(
                new JacksonFixRequest("ws1", List.of(new ModelRef("bpmn", "clean"))));

        assertThat(response.successCount()).isZero();
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.results().getFirst().error()).contains("nothing to fix");
        verify(client, never()).updateModelContent(any(), any(), any(), any());
    }

    @Test
    void fixReportsFailureWhenUpdateCallThrows() {
        JsonNode content = jsonMapper.readTree("\"import com.fasterxml.jackson.databind.ObjectMapper\"");
        when(client.getModelContentNode("ws1", "bpmn", "dirty")).thenReturn(content);
        org.mockito.Mockito.doThrow(new DesignApiException("HTTP 500", 500, null))
                .when(client).updateModelContent(eq("ws1"), eq("bpmn"), eq("dirty"), any());

        JacksonFixResponse response = service.fix(
                new JacksonFixRequest("ws1", List.of(new ModelRef("bpmn", "dirty"))));

        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.results().getFirst().error()).contains("HTTP 500");
    }
}
