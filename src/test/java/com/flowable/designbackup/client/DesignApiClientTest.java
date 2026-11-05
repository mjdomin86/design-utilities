package com.flowable.designbackup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class DesignApiClientTest {

    private static final String BASE = "http://design:8081/design-api";

    private MockRestServiceServer server;
    private DesignApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DesignApiClient(builder.build());
    }

    @Test
    void listAppsReturnsAllAppsOfWorkspace() {
        // Arrange
        server.expect(requestTo(BASE + "/workspaces/ws1/apps?start=0&size=200"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"1","key":"app902","name":"App 902","modelType":"app"}],
                         "total":1,"start":0,"size":200}
                        """, MediaType.APPLICATION_JSON));

        // Act
        List<ModelSummary> apps = client.listApps("ws1");

        // Assert
        assertThat(apps).containsExactly(new ModelSummary("1", "app902", "App 902", "app"));
        server.verify();
    }

    @Test
    void listModelsFetchesAllPagesUntilTotalReached() {
        // Arrange
        server.expect(requestTo(BASE + "/workspaces/ws1/models?modelType=form&start=0&size=200"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"1","key":"f1","name":"Form 1","modelType":"form"},
                                 {"id":"2","key":"f2","name":"Form 2","modelType":"form"}],
                         "total":3,"start":0,"size":2}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/workspaces/ws1/models?modelType=form&start=2&size=200"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"3","key":"f3","name":"Form 3","modelType":"form"}],
                         "total":3,"start":2,"size":1}
                        """, MediaType.APPLICATION_JSON));

        // Act
        List<ModelSummary> models = client.listModels("ws1", "form");

        // Assert
        assertThat(models).extracting(ModelSummary::key).containsExactly("f1", "f2", "f3");
        server.verify();
    }

    @Test
    void exportAppDownloadsZipBytes() {
        // Arrange
        byte[] zip = {0x50, 0x4b, 0x03, 0x04};
        server.expect(requestTo(BASE + "/workspaces/ws1/apps/app902/export?excludeChildReferences=false"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(zip, MediaType.parseMediaType("application/zip")));

        // Act
        byte[] result = client.exportApp("ws1", "app902");

        // Assert
        assertThat(result).isEqualTo(zip);
        server.verify();
    }

    @Test
    void exportModelReturnsExportedFileAsString() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/bpmn/creditCheck1/export"))
                .andRespond(withSuccess("<definitions/>".getBytes(),
                        MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.exportModel("ws1", "bpmn", "creditCheck1")).isEqualTo("<definitions/>");
        server.verify();
    }

    @Test
    void getModelContentExtractsStringContentFromModelDetails() {
        // XML-based models (BPMN, CMMN, DMN) carry their definition as a string.
        server.expect(requestTo(BASE + "/workspaces/ws1/models/bpmn/creditCheck1?includeContent=true"))
                .andRespond(withSuccess("""
                        {"id":"1","key":"creditCheck1","modelType":"bpmn","content":"<definitions/>"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getModelContent("ws1", "bpmn", "creditCheck1")).isEqualTo("<definitions/>");
        server.verify();
    }

    @Test
    void getModelContentSerializesJsonObjectContent() {
        // JSON-based models (service, form, ...) carry their definition as an object.
        server.expect(requestTo(BASE + "/workspaces/ws1/models/service/svc1?includeContent=true"))
                .andRespond(withSuccess("""
                        {"id":"2","key":"svc1","modelType":"service",
                         "content":{"type":"REST","operations":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getModelContent("ws1", "service", "svc1"))
                .isEqualTo("{\"type\":\"REST\",\"operations\":[]}");
        server.verify();
    }

    @Test
    void getAppModelContentUsesIncludeContentParameter() {
        server.expect(requestTo(BASE + "/workspaces/ws1/apps/app902/models/service/svc1?includeContent=true"))
                .andRespond(withSuccess("""
                        {"id":"2","key":"svc1","modelType":"service","content":{"type":"REST"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getAppModelContent("ws1", "app902", "service", "svc1"))
                .isEqualTo("{\"type\":\"REST\"}");
        server.verify();
    }

    @Test
    void getModelContentFailsClearlyWhenContentIsMissing() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/bpmn/creditCheck1?includeContent=true"))
                .andRespond(withSuccess("""
                        {"id":"1","key":"creditCheck1","modelType":"bpmn"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getModelContent("ws1", "bpmn", "creditCheck1"))
                .isInstanceOf(DesignApiException.class)
                .hasMessageContaining("no content");
    }

    @Test
    void listAllModelsOmitsModelTypeFilter() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models?start=0&size=200"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"1","key":"m1","name":"M1","modelType":"bpmn"}],
                         "total":1,"start":0,"size":200}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listAllModels("ws1")).extracting(ModelSummary::key).containsExactly("m1");
        server.verify();
    }

    @Test
    void getParentRelationsSummaryReturnsUsageCounts() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/service/svc1/parent-relations-summary"))
                .andRespond(withSuccess("""
                        {"usedInPackage":true,"packageCount":2,"modelsCount":3}
                        """, MediaType.APPLICATION_JSON));

        var summary = client.getParentRelationsSummary("ws1", "service", "svc1");

        assertThat(summary.usedInPackage()).isTrue();
        assertThat(summary.packageCount()).isEqualTo(2);
        assertThat(summary.modelsCount()).isEqualTo(3);
        server.verify();
    }

    @Test
    void getParentRelationsReturnsPackageAwareRelations() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/form/form1/parent-relations"))
                .andRespond(withSuccess("""
                        [{"packageModelKey":"app902","packageModelName":"App 902",
                          "modelKey":"proc1","modelName":"Process 1","modelType":"bpmn"}]
                        """, MediaType.APPLICATION_JSON));

        var relations = client.getParentRelations("ws1", "form", "form1");

        assertThat(relations).hasSize(1);
        assertThat(relations.getFirst().packageModelKey()).isEqualTo("app902");
        server.verify();
    }

    @Test
    void wrapsHttpErrorsInDesignApiExceptionWithStatus() {
        server.expect(requestTo(BASE + "/workspaces/ws1/apps/app902/export?excludeChildReferences=false"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.exportApp("ws1", "app902"))
                .isInstanceOf(DesignApiException.class)
                .satisfies(e -> assertThat(((DesignApiException) e).getStatusCode()).isEqualTo(401));
    }

    @Test
    void createWorkspaceSendsKeyAndNameAndReturnsCreatedWorkspace() {
        server.expect(requestTo(BASE + "/workspaces"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"key":"ws-restored","name":"Restored workspace"}
                        """))
                .andRespond(withSuccess("""
                        {"id":"1","key":"ws-restored","name":"Restored workspace"}
                        """, MediaType.APPLICATION_JSON));

        ModelSummary created = client.createWorkspace("ws-restored", "Restored workspace");

        assertThat(created.key()).isEqualTo("ws-restored");
        assertThat(created.name()).isEqualTo("Restored workspace");
        server.verify();
    }

    @Test
    void importAppSendsZipAsMultipartWithOverwriteStrategy() {
        server.expect(requestTo(BASE + "/workspaces/ws1/import-app"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().multipartDataContains(Map.of("importStrategy", "overwriteOnClash")))
                .andRespond(withSuccess());

        client.importApp("ws1", new byte[] {0x50, 0x4b, 0x03, 0x04}, "app902.zip");

        server.verify();
    }

    @Test
    void importAppWrapsFailureInDesignApiException() {
        server.expect(requestTo(BASE + "/workspaces/ws1/import-app"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.importApp("ws1", new byte[] {0x50, 0x4b}, "app902.zip"))
                .isInstanceOf(DesignApiException.class)
                .satisfies(e -> assertThat(((DesignApiException) e).getStatusCode()).isEqualTo(401));
    }

    @Test
    void getModelContentNodeReturnsRawContentNodeUnflattened() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/service/svc1?includeContent=true"))
                .andRespond(withSuccess("""
                        {"id":"1","key":"svc1","modelType":"service","content":{"type":"REST"}}
                        """, MediaType.APPLICATION_JSON));

        tools.jackson.databind.JsonNode content = client.getModelContentNode("ws1", "service", "svc1");

        assertThat(content.isObject()).isTrue();
        assertThat(content.get("type").asString()).isEqualTo("REST");
        server.verify();
    }

    @Test
    void updateModelContentSendsContentAndNewVersionFalse() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/bpmn/creditCheck1/content"))
                .andExpect(method(org.springframework.http.HttpMethod.PUT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"content":"<definitions/>","newVersion":false}
                        """))
                .andRespond(withSuccess());

        client.updateModelContent("ws1", "bpmn", "creditCheck1",
                tools.jackson.databind.node.StringNode.valueOf("<definitions/>"));

        server.verify();
    }
}
