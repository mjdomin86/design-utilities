package com.flowable.designbackup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void getModelContentReturnsRawBody() {
        server.expect(requestTo(BASE + "/workspaces/ws1/models/bpmn/creditCheck1/content"))
                .andRespond(withSuccess("<definitions/>", MediaType.APPLICATION_XML));

        assertThat(client.getModelContent("ws1", "bpmn", "creditCheck1")).isEqualTo("<definitions/>");
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
}
