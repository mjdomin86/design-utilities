package com.flowable.designbackup.operations;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds, within one app, the service model operation(s) bound to a given HTTP
 * method and path, returning the service model key, operation key and all
 * input/output parameters.
 */
@Service
public class ServiceOperationSearchService {

    private static final Logger log = LoggerFactory.getLogger(ServiceOperationSearchService.class);
    private static final String SERVICE_MODEL_TYPE = "service";
    private static final Set<String> HTTP_SERVICE_TYPES = Set.of("rest", "http");

    private final DesignApiClient designApiClient;
    private final ObjectReader serviceModelReader;

    public ServiceOperationSearchService(DesignApiClient designApiClient, ObjectMapper objectMapper) {
        this.designApiClient = designApiClient;
        // Some exported .service files carry trailing non-JSON text; read the
        // leading JSON document only.
        this.serviceModelReader = objectMapper.readerFor(ServiceModelContent.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public OperationSearchResponse findOperations(String workspaceKey, String appKey, String method, String path) {
        List<ModelSummary> serviceModels = designApiClient.listAppModels(workspaceKey, appKey, SERVICE_MODEL_TYPE);
        List<ServiceOperationMatch> matches = new ArrayList<>();
        for (ModelSummary model : serviceModels) {
            ServiceModelContent content = readServiceModel(workspaceKey, appKey, model);
            if (content == null || !isHttpService(content)) {
                continue;
            }
            for (ServiceModelContent.Operation operation : content.operations()) {
                if (matchesOperation(operation, method, path)) {
                    matches.add(toMatch(model, content, operation));
                }
            }
        }
        return new OperationSearchResponse(workspaceKey, appKey, method, path, serviceModels.size(), matches);
    }

    private ServiceModelContent readServiceModel(String workspaceKey, String appKey, ModelSummary model) {
        try {
            String raw = designApiClient.getAppModelContent(workspaceKey, appKey, SERVICE_MODEL_TYPE, model.key());
            return serviceModelReader.readValue(raw);
        } catch (Exception e) {
            log.warn("Skipping service model '{}': content could not be read ({})", model.key(), e.getMessage());
            return null;
        }
    }

    private boolean isHttpService(ServiceModelContent content) {
        return content.type() != null && HTTP_SERVICE_TYPES.contains(content.type().toLowerCase());
    }

    private boolean matchesOperation(ServiceModelContent.Operation operation, String method, String path) {
        ServiceModelContent.Config config = operation.config();
        return config != null
                && method.equalsIgnoreCase(config.method())
                && normalizePath(path).equals(normalizePath(config.url()));
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.strip();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private ServiceOperationMatch toMatch(ModelSummary model, ServiceModelContent content,
            ServiceModelContent.Operation operation) {
        return new ServiceOperationMatch(
                content.key() != null ? content.key() : model.key(),
                content.name() != null ? content.name() : model.name(),
                operation.key(),
                operation.name(),
                operation.config().method(),
                operation.config().url(),
                operation.inputParameters(),
                operation.outputParameters());
    }
}
