package com.flowable.designbackup.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
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

    public List<ModelSummary> listAppModels(String workspaceKey, String appKey, String modelType) {
        return fetchAllPages(start -> uri -> uri.path("/workspaces/{ws}/apps/{app}/models")
                .queryParam("modelType", modelType)
                .queryParam("start", start)
                .queryParam("size", PAGE_SIZE)
                .build(workspaceKey, appKey));
    }

    public String getModelContent(String workspaceKey, String modelType, String modelKey) {
        return execute("get content of " + modelType + " model '" + modelKey + "'", () -> restClient.get()
                .uri("/workspaces/{ws}/models/{type}/{key}/content", workspaceKey, modelType, modelKey)
                .retrieve()
                .body(String.class));
    }

    public String getAppModelContent(String workspaceKey, String appKey, String modelType, String modelKey) {
        return execute("get content of " + modelType + " model '" + modelKey + "'", () -> restClient.get()
                .uri("/workspaces/{ws}/apps/{app}/models/{type}/{key}/content",
                        workspaceKey, appKey, modelType, modelKey)
                .retrieve()
                .body(String.class));
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
