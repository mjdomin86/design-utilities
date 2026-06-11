package com.flowable.designbackup.operations;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceOperationSearchServiceTest {

    /** Real REST-type .service export, including its trailing non-JSON footer. */
    private static String restServiceContent;

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final ServiceOperationSearchService service =
            new ServiceOperationSearchService(client, JsonMapper.builder().build());

    @BeforeAll
    static void loadFixture() throws Exception {
        try (var stream = ServiceOperationSearchServiceTest.class
                .getResourceAsStream("/fixtures/rest-service-example.service")) {
            restServiceContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void findsOperationByMethodAndPathWithAllParameters() {
        // Arrange
        mockSingleServiceModel(restServiceContent);

        // Act
        OperationSearchResponse response =
                service.findOperations("ws1", "app902", "POST", "/add/notifGroups");

        // Assert
        assertThat(response.scannedServiceModels()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        ServiceOperationMatch match = response.matches().getFirst();
        assertThat(match.serviceModelKey()).isEqualTo("CMS-GEN-S902-NOTIFICATION");
        assertThat(match.operationKey()).isEqualTo("addNotification");
        assertThat(match.inputParameters()).extracting(OperationParameter::name)
                .containsExactly("account", "notificationTokens");
        assertThat(match.outputParameters()).extracting(OperationParameter::name)
                .containsExactly("notifGroupId");
        assertThat(match.outputParameters().getFirst().required()).isTrue();
    }

    @Test
    void methodIsMatchedCaseInsensitivelyAndPathToleratesTrailingSlash() {
        mockSingleServiceModel(restServiceContent);

        OperationSearchResponse response =
                service.findOperations("ws1", "app902", "post", "add/notifGroups/");

        assertThat(response.matches()).extracting(ServiceOperationMatch::operationKey)
                .containsExactly("addNotification");
    }

    @Test
    void returnsEmptyWhenMethodDoesNotMatch() {
        mockSingleServiceModel(restServiceContent);

        OperationSearchResponse response =
                service.findOperations("ws1", "app902", "GET", "/add/notifGroups");

        assertThat(response.matches()).isEmpty();
    }

    @Test
    void nonHttpServiceModelsAreSkipped() {
        mockSingleServiceModel("""
                {"type":"database","key":"db1","name":"DB service",
                 "operations":[{"key":"findById","config":{}}]}
                """);

        OperationSearchResponse response =
                service.findOperations("ws1", "app902", "POST", "/add/notifGroups");

        assertThat(response.scannedServiceModels()).isEqualTo(1);
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void unreadableServiceModelContentIsSkippedNotFatal() {
        mockSingleServiceModel("this is not json at all");

        OperationSearchResponse response =
                service.findOperations("ws1", "app902", "POST", "/add/notifGroups");

        assertThat(response.matches()).isEmpty();
    }

    private void mockSingleServiceModel(String content) {
        when(client.listAppModels("ws1", "app902", "service")).thenReturn(List.of(
                new ModelSummary("1", "svc1", "Service 1", "service")));
        when(client.getAppModelContent("ws1", "app902", "service", "svc1")).thenReturn(content);
    }
}
