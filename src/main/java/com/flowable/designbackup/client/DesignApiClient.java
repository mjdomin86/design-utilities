package com.flowable.designbackup.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Thin client over the Flowable Design REST API. Paths follow
 * swagger/Flowable-design.yaml; the {@code /design-api} prefix is part of the
 * RestClient base URL.
 */
@Component
public class DesignApiClient {

    private static final int PAGE_SIZE = 200;
    private static final ParameterizedTypeReference<DataResponse<ModelSummary>> MODEL_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<ModelRelationInfo>> RELATION_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public DesignApiClient(RestClient designRestClient) {
        this.restClient = designRestClient;
    }

    public List<ModelSummary> listWorkspaces() {
        return fetchAllPages(start -> uri -> uri.path("/workspaces")
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build());
    }

    public List<ModelSummary> listApps(String workspaceKey) {
        return fetchAllPages(start -> uri -> uri.path("/workspaces/{ws}/apps")
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build(workspaceKey));
    }

    public byte[] exportApp(String workspaceKey, String appKey) {
        return execute("export app '" + appKey + "'", () -> restClient.get()
                .uri(uri -> uri.path("/workspaces/{ws}/apps/{app}/export")
                        .queryParam("excludeChildReferences", false)
                        .build(workspaceKey, appKey))
                .retrieve()
                .body(byte[].class));
    }

    public List<ModelSummary> listModels(String workspaceKey, String modelType) {
        return fetchAllPages(start -> uri -> uri.path("/workspaces/{ws}/models")
                .queryParam("modelType", modelType)
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build(workspaceKey));
    }

    public List<ModelSummary> listAllModels(String workspaceKey) {
        return fetchAllPages(start -> uri -> uri.path("/workspaces/{ws}/models")
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build(workspaceKey));
    }

    public List<ModelSummary> listAppModels(String workspaceKey, String appKey, String modelType) {
        return fetchAllPages(start -> uri -> uri.path("/workspaces/{ws}/apps/{app}/models")
                .queryParam("modelType", modelType)
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build(workspaceKey, appKey));
    }

    /**
     * Downloads the model's exported file. For XML-based models (BPMN, CMMN,
     * DMN) this is the XML definition, unlike the JSON returned by
     * {@link #getModelContent}.
     */
    public String exportModel(String workspaceKey, String modelType, String modelKey) {
        return execute("export " + modelType + " model '" + modelKey + "'", () -> {
            byte[] bytes = restClient.get()
                    .uri("/workspaces/{ws}/models/{type}/{key}/export", workspaceKey, modelType, modelKey)
                    .retrieve()
                    .body(byte[].class);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        });
    }

    public String getModelContent(String workspaceKey, String modelType, String modelKey) {
        String action = "get content of " + modelType + " model '" + modelKey + "'";
        JsonNode model = execute(action, () -> restClient.get()
                .uri(uri -> uri.path("/workspaces/{ws}/models/{type}/{key}")
                        .queryParam("includeContent", true)
                        .build(workspaceKey, modelType, modelKey))
                .retrieve()
                .body(JsonNode.class));
        return extractContent(model, action);
    }

    public String getAppModelContent(String workspaceKey, String appKey, String modelType, String modelKey) {
        String action = "get content of " + modelType + " model '" + modelKey + "'";
        JsonNode model = execute(action, () -> restClient.get()
                .uri(uri -> uri.path("/workspaces/{ws}/apps/{app}/models/{type}/{key}")
                        .queryParam("includeContent", true)
                        .build(workspaceKey, appKey, modelType, modelKey))
                .retrieve()
                .body(JsonNode.class));
        return extractContent(model, action);
    }

    /**
     * Usage summary of the model: how many packages and other models reference
     * it. Used to detect models that are safe to delete.
     */
    public ModelRelationsSummary getParentRelationsSummary(String workspaceKey, String modelType, String modelKey) {
        return execute("get relations summary of " + modelType + " model '" + modelKey + "'", () -> restClient.get()
                .uri("/workspaces/{ws}/models/{type}/{key}/parent-relations-summary",
                        workspaceKey, modelType, modelKey)
                .retrieve()
                .body(ModelRelationsSummary.class));
    }

    /**
     * The models and packages that reference this model (its parents).
     */
    public List<ModelRelationInfo> getParentRelations(String workspaceKey, String modelType, String modelKey) {
        List<ModelRelationInfo> relations = execute(
                "get parent relations of " + modelType + " model '" + modelKey + "'", () -> restClient.get()
                        .uri("/workspaces/{ws}/models/{type}/{key}/parent-relations",
                                workspaceKey, modelType, modelKey)
                        .retrieve()
                        .body(RELATION_LIST));
        return relations == null ? List.of() : relations;
    }

    /**
     * The model detail response carries the definition in its {@code content}
     * field: a raw string for XML-based models (BPMN, CMMN, DMN) and a JSON
     * object for the rest (service, form, ...).
     */
    private static String extractContent(JsonNode model, String action) {
        JsonNode content = model == null ? null : model.get("content");
        if (content == null || content.isNull()) {
            throw new DesignApiException(
                    "Flowable Design API returned no content (" + action + ")", 0, null);
        }
        return content.isString() ? content.asString() : content.toString();
    }

    private List<ModelSummary> fetchAllPages(Function<Integer, Function<UriBuilder, URI>> uriForStart) {
        List<ModelSummary> all = new ArrayList<>();
        int start = 0;
        while (true) {
            DataResponse<ModelSummary> page = execute("list models", () -> restClient.get()
                    .uri(uriForStart.apply(all.size()))
                    .retrieve()
                    .body(MODEL_PAGE));
            if (page == null || page.data().isEmpty()) {
                return List.copyOf(all);
            }
            all.addAll(page.data());
            start += page.data().size();
            if (start >= page.total()) {
                return List.copyOf(all);
            }
        }
    }

    private <T> T execute(String action, ApiCall<T> call) {
        try {
            return call.invoke();
        } catch (RestClientResponseException e) {
            throw new DesignApiException(
                    "Flowable Design API call failed (" + action + "): HTTP " + e.getStatusCode().value(),
                    e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new DesignApiException(
                    "Could not reach Flowable Design (" + action + "): " + e.getMessage(), 0, e);
        }
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T invoke();
    }
}
