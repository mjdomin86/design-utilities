package com.flowable.designbackup.search;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.DesignApiException;
import com.flowable.designbackup.client.ModelRelationInfo;
import com.flowable.designbackup.client.ModelSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelSearchServiceTest {

    private static String bpmnXml;

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final ModelSearchService service = new ModelSearchService(client, new BpmnElementSearcher());

    @BeforeAll
    static void loadFixture() throws Exception {
        try (var stream = ModelSearchServiceTest.class.getResourceAsStream("/fixtures/creditCheck1.bpmn")) {
            bpmnXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void matchesAreEnrichedWithDistinctAppsFromParentRelations() {
        // Arrange
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "form1", "Form 1", "form")));
        when(client.getModelContent("ws1", "form", "form1")).thenReturn("has GEN-902");
        when(client.getParentRelations("ws1", "form", "form1")).thenReturn(List.of(
                new ModelRelationInfo("app902", "App 902", "proc1", "Process 1", "bpmn"),
                new ModelRelationInfo("app902", "App 902", "proc2", "Process 2", "bpmn"),
                new ModelRelationInfo(null, null, "wsModel", "Workspace Model", "bpmn")));

        // Act
        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("form"), SearchCondition.CONTAINS, "GEN-902", null));

        // Assert
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().apps())
                .extracting(AppReference::key)
                .containsExactly("app902");
    }

    @Test
    void appResolutionFailureDoesNotDropTheMatch() {
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "form1", "Form 1", "form")));
        when(client.getModelContent("ws1", "form", "form1")).thenReturn("has GEN-902");
        when(client.getParentRelations("ws1", "form", "form1"))
                .thenThrow(new DesignApiException("boom", 500, null));

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("form"), SearchCondition.CONTAINS, "GEN-902", null));

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().apps()).isEmpty();
    }

    @Test
    void matchesNonBpmnModelsOnWholeContent() {
        // Arrange
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "form1", "Form 1", "form"),
                new ModelSummary("2", "form2", "Form 2", "form")));
        when(client.getModelContent("ws1", "form", "form1")).thenReturn("{\"label\":\"GEN-902 form\"}");
        when(client.getModelContent("ws1", "form", "form2")).thenReturn("{\"label\":\"other\"}");

        // Act
        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("form"), SearchCondition.CONTAINS, "GEN-902", null));

        // Assert
        assertThat(response.scannedModels()).isEqualTo(2);
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().modelKey()).isEqualTo("form1");
        assertThat(response.matches().getFirst().snippet()).contains("GEN-902");
    }

    @Test
    void notContainsReturnsModelsWithoutTheTerm() {
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "form1", "Form 1", "form")));
        when(client.getModelContent("ws1", "form", "form1")).thenReturn("{\"label\":\"other\"}");

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("form"), SearchCondition.NOT_CONTAINS, "GEN-902", null));

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().snippet()).isNull();
    }

    @Test
    void bpmnModelsAreMatchedElementByElement() {
        when(client.listModels("ws1", "bpmn")).thenReturn(List.of(
                new ModelSummary("1", "creditCheck1", "Credit check", "bpmn")));
        when(client.exportModel("ws1", "bpmn", "creditCheck1")).thenReturn(bpmnXml);

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("bpmn"), SearchCondition.NOT_CONTAINS, "GEN-902", "serviceTask"));

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().elements())
                .extracting(ElementMatch::elementId)
                .containsExactly("AgentTask_4");
    }

    @Test
    void cmmnModelsAreMatchedElementByElementViaExport() throws Exception {
        String cmmnXml;
        try (var stream = ModelSearchServiceTest.class.getResourceAsStream("/fixtures/loanRequest.cmmn")) {
            cmmnXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        when(client.listModels("ws1", "cmmn")).thenReturn(List.of(
                new ModelSummary("1", "loanRequest", "Loan Request", "cmmn")));
        when(client.exportModel("ws1", "cmmn", "loanRequest")).thenReturn(cmmnXml);

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("cmmn"), SearchCondition.CONTAINS, "Manual review of application", "humanTask"));

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().elements())
                .extracting(ElementMatch::elementId)
                .containsExactly("HumanTask_29");
    }

    @Test
    void bpmnModelWithoutMatchingElementsIsExcluded() {
        when(client.listModels("ws1", "bpmn")).thenReturn(List.of(
                new ModelSummary("1", "creditCheck1", "Credit check", "bpmn")));
        when(client.exportModel("ws1", "bpmn", "creditCheck1")).thenReturn(bpmnXml);

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("bpmn"), SearchCondition.CONTAINS, "GEN-902", "serviceTask"));

        assertThat(response.matches()).isEmpty();
    }

    @Test
    void contentFetchFailuresAreCountedNotFatal() {
        when(client.listModels("ws1", "form")).thenReturn(List.of(
                new ModelSummary("1", "form1", "Form 1", "form"),
                new ModelSummary("2", "form2", "Form 2", "form")));
        when(client.getModelContent("ws1", "form", "form1"))
                .thenThrow(new DesignApiException("boom", 500, null));
        when(client.getModelContent("ws1", "form", "form2")).thenReturn("has GEN-902");

        SearchResponse response = service.search(new SearchRequest(
                "ws1", List.of("form"), SearchCondition.CONTAINS, "GEN-902", null));

        assertThat(response.scannedModels()).isEqualTo(2);
        assertThat(response.erroredModels()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
    }
}
