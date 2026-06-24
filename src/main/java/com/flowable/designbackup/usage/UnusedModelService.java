package com.flowable.designbackup.usage;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelRelationsSummary;
import com.flowable.designbackup.client.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Finds models that are referenced by no package and no other model, so they
 * can be deleted. Package types (app, plugin) are roots, never "used by" other
 * models, so they are excluded from the scan.
 */
@Service
public class UnusedModelService {

    private static final Logger log = LoggerFactory.getLogger(UnusedModelService.class);
    private static final int SUMMARY_FETCH_CONCURRENCY = 4;
    private static final Set<String> PACKAGE_TYPES = Set.of("app", "plugin");

    private final DesignApiClient designApiClient;

    public UnusedModelService(DesignApiClient designApiClient) {
        this.designApiClient = designApiClient;
    }

    /**
     * @param workspaceKey the workspace to scan
     * @param modelTypes   restrict the scan to these model types; empty scans all
     */
    public UnusedModelsResponse findUnusedModels(String workspaceKey, List<String> modelTypes) {
        List<ModelSummary> candidates = listCandidates(workspaceKey, modelTypes);
        List<ModelUsage> usages = fetchUsages(workspaceKey, candidates);

        List<UnusedModel> unused = new ArrayList<>();
        int errored = 0;
        for (ModelUsage usage : usages) {
            if (usage.error()) {
                errored++;
                continue;
            }
            if (usage.summary().packageCount() == 0 && usage.summary().modelsCount() == 0) {
                ModelSummary model = usage.model();
                unused.add(new UnusedModel(model.key(), model.name(), model.modelType(),
                        usage.summary().modelsCount(), usage.summary().packageCount()));
            }
        }
        return new UnusedModelsResponse(workspaceKey, usages.size(), errored, unused);
    }

    private List<ModelSummary> listCandidates(String workspaceKey, List<String> modelTypes) {
        List<ModelSummary> models;
        if (modelTypes == null || modelTypes.isEmpty()) {
            models = designApiClient.listAllModels(workspaceKey);
        } else {
            models = new ArrayList<>();
            for (String modelType : modelTypes) {
                if (!PACKAGE_TYPES.contains(modelType)) {
                    models.addAll(designApiClient.listModels(workspaceKey, modelType));
                }
            }
        }
        return models.stream().filter(model -> !PACKAGE_TYPES.contains(model.modelType())).toList();
    }

    private List<ModelUsage> fetchUsages(String workspaceKey, List<ModelSummary> models) {
        try (ExecutorService executor = Executors.newFixedThreadPool(SUMMARY_FETCH_CONCURRENCY)) {
            List<Future<ModelUsage>> futures = models.stream()
                    .map(model -> executor.submit(() -> fetchUsage(workspaceKey, model)))
                    .toList();
            List<ModelUsage> usages = new ArrayList<>();
            for (Future<ModelUsage> future : futures) {
                usages.add(resolve(future));
            }
            return usages;
        }
    }

    private ModelUsage fetchUsage(String workspaceKey, ModelSummary model) {
        try {
            ModelRelationsSummary summary =
                    designApiClient.getParentRelationsSummary(workspaceKey, model.modelType(), model.key());
            return new ModelUsage(model, summary, false);
        } catch (Exception e) {
            log.warn("Could not fetch usage of {} model '{}': {}", model.modelType(), model.key(), e.getMessage());
            return new ModelUsage(model, null, true);
        }
    }

    private ModelUsage resolve(Future<ModelUsage> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unused-model scan interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unused-model scan failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private record ModelUsage(ModelSummary model, ModelRelationsSummary summary, boolean error) {
    }
}
