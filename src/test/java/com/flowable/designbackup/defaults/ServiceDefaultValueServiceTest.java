package com.flowable.designbackup.defaults;

import com.flowable.designbackup.client.DesignApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDefaultValueServiceTest {

    private static final String SERVICE_WITHOUT_DEFAULT = """
            {"key":"svc","name":"Svc","operations":[{"key":"op","name":"Op","outputParameters":[
              {"name":"amount","type":"bigDecimal","missingValueConfig":"ignore","nullValueConfig":"setToNull"}]}]}
            """;
    private static final String BPMN = "<definitions/>";

    private final DesignApiClient client = mock(DesignApiClient.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ServiceDefaultValueService service = new ServiceDefaultValueService(client, mapper);

    @Test
    void rewritesServiceEntriesAndReimportsApp() throws Exception {
        // Arrange
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("app/service-svc.service", SERVICE_WITHOUT_DEFAULT + "trailing disclaimer text");
        entries.put("app/process.bpmn", BPMN);
        when(client.exportApp("ws1", "app1")).thenReturn(zip(entries));

        // Act
        DefaultValueResponse response = service.addDefaultValues(new DefaultValueRequest("ws1", "app1", "0"));

        // Assert
        assertThat(response.imported()).isTrue();
        assertThat(response.serviceModelsScanned()).isEqualTo(1);
        assertThat(response.changes()).extracting(ParameterDefaultChange::parameterName).containsExactly("amount");
        ArgumentCaptor<byte[]> imported = ArgumentCaptor.forClass(byte[].class);
        verify(client).importApp(eq("ws1"), imported.capture(), eq("app1.zip"));
        Map<String, String> importedEntries = unzip(imported.getValue());
        assertThat(importedEntries.get("app/process.bpmn")).isEqualTo(BPMN);
        JsonNode parameter = mapper.readTree(importedEntries.get("app/service-svc.service"))
                .path("operations").get(0).path("outputParameters").get(0);
        assertThat(parameter.get("nullValueConfig").asString()).isEqualTo("setToDefault");
        assertThat(parameter.get("nullValueDefault").asString()).isEqualTo("0");
    }

    @Test
    void doesNotImportWhenNothingChanges() throws Exception {
        // Arrange
        when(client.exportApp("ws1", "app1")).thenReturn(zip(Map.of("app/process.bpmn", BPMN)));

        // Act
        DefaultValueResponse response = service.addDefaultValues(new DefaultValueRequest("ws1", "app1", null));

        // Assert
        assertThat(response.imported()).isFalse();
        assertThat(response.defaultValue()).isEqualTo("0");
        verify(client, never()).importApp(anyString(), any(), anyString());
    }

    @Test
    void reportsUnreadableServiceEntryAndStillImportsTheRest() throws Exception {
        // Arrange
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("broken.service", "not json");
        entries.put("svc.service", SERVICE_WITHOUT_DEFAULT);
        when(client.exportApp("ws1", "app1")).thenReturn(zip(entries));

        // Act
        DefaultValueResponse response = service.addDefaultValues(new DefaultValueRequest("ws1", "app1", "1"));

        // Assert
        assertThat(response.failures()).extracting(ServiceModelFailure::entryName).containsExactly("broken.service");
        assertThat(response.changes()).hasSize(1);
        ArgumentCaptor<byte[]> imported = ArgumentCaptor.forClass(byte[].class);
        verify(client).importApp(eq("ws1"), imported.capture(), eq("app1.zip"));
        assertThat(unzip(imported.getValue()).get("broken.service")).isEqualTo("not json");
    }

    @Test
    void failsWhenExportIsEmpty() {
        // Arrange
        when(client.exportApp("ws1", "app1")).thenReturn(new byte[0]);

        // Act / Assert
        assertThatThrownBy(() -> service.addDefaultValues(new DefaultValueRequest("ws1", "app1", "0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app1");
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
