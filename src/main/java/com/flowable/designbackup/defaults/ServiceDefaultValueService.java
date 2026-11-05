package com.flowable.designbackup.defaults;

import com.flowable.designbackup.client.DesignApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Adds a default value to every number output parameter of every service
 * model in an app: exports the app zip, rewrites its .service entries with
 * {@link NumberDefaultValueRules}, and imports the zip back with {@code
 * overwriteOnClash}. Every other zip entry is copied byte for byte.
 */
@Service
public class ServiceDefaultValueService {

    private static final Logger log = LoggerFactory.getLogger(ServiceDefaultValueService.class);
    private static final String SERVICE_ENTRY_EXTENSION = ".service";
    private static final String ZIP_EXTENSION = ".zip";

    private final DesignApiClient designApiClient;
    private final ObjectMapper objectMapper;
    private final ObjectReader serviceModelReader;

    public ServiceDefaultValueService(DesignApiClient designApiClient, ObjectMapper objectMapper) {
        this.designApiClient = designApiClient;
        this.objectMapper = objectMapper;
        // Some exported .service files carry trailing non-JSON text; read the
        // leading JSON document only (the trailing text is dropped on rewrite).
        this.serviceModelReader = objectMapper.readerFor(JsonNode.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public DefaultValueResponse addDefaultValues(DefaultValueRequest request) {
        String defaultValue = request.effectiveDefaultValue();
        byte[] exported = designApiClient.exportApp(request.workspaceKey(), request.appKey());
        if (exported == null || exported.length == 0) {
            throw new IllegalStateException("Export of app '" + request.appKey() + "' returned no content");
        }
        RewrittenApp rewritten = rewrite(exported, defaultValue);
        boolean shouldImport = !rewritten.changes().isEmpty();
        if (shouldImport) {
            designApiClient.importApp(request.workspaceKey(), rewritten.zip(), request.appKey() + ZIP_EXTENSION);
        }
        log.info("Default values for app '{}' in workspace '{}': {} parameter(s) in {} service model(s), imported={}",
                request.appKey(), request.workspaceKey(), rewritten.changes().size(),
                rewritten.serviceModelsScanned(), shouldImport);
        return new DefaultValueResponse(request.workspaceKey(), request.appKey(), defaultValue,
                rewritten.serviceModelsScanned(), shouldImport, rewritten.changes(), rewritten.failures());
    }

    private RewrittenApp rewrite(byte[] zip, String defaultValue) {
        List<ParameterDefaultChange> changes = new ArrayList<>();
        List<ServiceModelFailure> failures = new ArrayList<>();
        int scanned = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream(zip.length);
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip));
             ZipOutputStream out = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if (!entry.isDirectory() && entry.getName().endsWith(SERVICE_ENTRY_EXTENSION)) {
                    scanned++;
                    content = rewriteServiceEntry(entry.getName(), content, defaultValue, changes, failures);
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(content);
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rewrite the app export zip", e);
        }
        return new RewrittenApp(output.toByteArray(), scanned, changes, failures);
    }

    /**
     * @return the entry's new bytes, or the original bytes when it has no
     *         number parameter to change or cannot be read.
     */
    private byte[] rewriteServiceEntry(String entryName, byte[] content, String defaultValue,
            List<ParameterDefaultChange> changes, List<ServiceModelFailure> failures) {
        try {
            JsonNode model = serviceModelReader.readValue(new String(content, StandardCharsets.UTF_8));
            NumberDefaultValueRules.Result result = NumberDefaultValueRules.apply(model, defaultValue);
            if (result.changes().isEmpty()) {
                return content;
            }
            changes.addAll(result.changes());
            return objectMapper.writeValueAsBytes(result.updatedModel());
        } catch (Exception e) {
            log.warn("Skipping service model entry '{}': content could not be read ({})", entryName, e.getMessage());
            failures.add(new ServiceModelFailure(entryName, e.getMessage()));
            return content;
        }
    }

    private record RewrittenApp(byte[] zip, int serviceModelsScanned,
            List<ParameterDefaultChange> changes, List<ServiceModelFailure> failures) {
    }
}
