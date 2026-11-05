package com.flowable.designbackup.migration;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import com.flowable.designbackup.search.AppReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Scans a workspace's models for leftover Jackson 2 references (from
 * Flowable 2025.1.x / Spring Boot 3) and fixes the ones the user selects by
 * overwriting their content in place via {@link DesignApiClient#updateModelContent}.
 */
@Service
public class JacksonMigrationService {

    private static final Logger log = LoggerFactory.getLogger(JacksonMigrationService.class);
    private static final int CONTENT_FETCH_CONCURRENCY = 4;

    /** Every model type Flowable Design supports, per swagger/Flowable-design.yaml. */
    private static final List<String> ALL_MODEL_TYPES = List.of(
            "bpmn", "cmmn", "dmn", "form", "app", "action", "document", "user", "page", "security",
            "dataObject", "template", "service", "liquibase", "channel", "event", "decisionService",
            "query", "variableExtractor", "sequence", "dataDictionary", "palette", "sla",
            "dashboardComponent", "plugin", "pluginTask", "agent", "knowledgeBase", "masterData");

    private final DesignApiClient designApiClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public JacksonMigrationService(DesignApiClient designApiClient) {
        this.designApiClient = designApiClient;
    }

    public JacksonScanResponse scan(JacksonScanRequest request) {
        List<String> modelTypes = request.modelTypes() == null || request.modelTypes().isEmpty()
                ? ALL_MODEL_TYPES : request.modelTypes();
        List<ModelJacksonIssue> matches = new ArrayList<>();
        int scanned = 0;
        int errored = 0;
        for (String modelType : modelTypes) {
            List<ModelSummary> models = designApiClient.listModels(request.workspaceKey(), modelType);
            for (ModelText text : fetchContents(request.workspaceKey(), modelType, models)) {
                scanned++;
                if (text.error()) {
                    errored++;
                    continue;
                }
                findIssue(request.workspaceKey(), text).ifPresent(matches::add);
            }
        }
        return new JacksonScanResponse(request.workspaceKey(), scanned, errored, matches);
    }

    public JacksonFixResponse fix(JacksonFixRequest request) {
        List<ModelFixResult> results = new ArrayList<>();
        for (ModelRef ref : request.models()) {
            results.add(fixSingleModel(request.workspaceKey(), ref));
        }
        int successCount = (int) results.stream().filter(ModelFixResult::success).count();
        return new JacksonFixResponse(request.workspaceKey(), results.size(), successCount,
                results.size() - successCount, results);
    }

    private Optional<ModelJacksonIssue> findIssue(String workspaceKey, ModelText text) {
        JacksonMigrationRules.ScanResult scanResult = JacksonMigrationRules.scan(text.content());
        if (scanResult.occurrences() == 0) {
            return Optional.empty();
        }
        ModelSummary model = text.model();
        return Optional.of(new ModelJacksonIssue(model.key(), model.name(), model.modelType(),
                scanResult.occurrences(), scanResult.needsManualReview(), resolveApps(workspaceKey, model)));
    }

    private ModelFixResult fixSingleModel(String workspaceKey, ModelRef ref) {
        try {
            JsonNode contentNode = designApiClient.getModelContentNode(workspaceKey, ref.modelType(), ref.modelKey());
            boolean isStringContent = contentNode.isString();
            String content = isStringContent ? contentNode.asString() : contentNode.toString();
            JacksonMigrationRules.FixResult fixResult = JacksonMigrationRules.fix(content);
            if (fixResult.occurrences() == 0) {
                return ModelFixResult.failure(ref.modelKey(), ref.modelType(),
                        "No Jackson 2 references found; nothing to fix");
            }
            JsonNode newContent = isStringContent
                    ? StringNode.valueOf(fixResult.fixedContent())
                    : jsonMapper.readTree(fixResult.fixedContent());
            designApiClient.updateModelContent(workspaceKey, ref.modelType(), ref.modelKey(), newContent);
            return ModelFixResult.success(ref.modelKey(), ref.modelType(), fixResult.occurrences());
        } catch (Exception e) {
            log.warn("Fix of {} model '{}' in workspace '{}' failed", ref.modelType(), ref.modelKey(), workspaceKey, e);
            return ModelFixResult.failure(ref.modelKey(), ref.modelType(), e.getMessage());
        }
    }

    /**
     * The apps a matched model belongs to, derived from its parent relations.
     * Enrichment only; a relations lookup failure must not drop the match.
     */
    private List<AppReference> resolveApps(String workspaceKey, ModelSummary model) {
        try {
            return designApiClient.getParentRelations(workspaceKey, model.modelType(), model.key()).stream()
                    .filter(relation -> relation.packageModelKey() != null && !relation.packageModelKey().isBlank())
                    .map(relation -> new AppReference(relation.packageModelKey(), relation.packageModelName()))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("Could not resolve apps for {} model '{}': {}", model.modelType(), model.key(), e.getMessage());
            return List.of();
        }
    }

    private List<ModelText> fetchContents(String workspaceKey, String modelType, List<ModelSummary> models) {
        try (ExecutorService executor = Executors.newFixedThreadPool(CONTENT_FETCH_CONCURRENCY)) {
            List<Future<ModelText>> futures = models.stream()
                    .map(model -> executor.submit(() -> fetchContent(workspaceKey, modelType, model)))
                    .toList();
            List<ModelText> contents = new ArrayList<>();
            for (Future<ModelText> future : futures) {
                contents.add(resolve(future));
            }
            return contents;
        }
    }

    private ModelText fetchContent(String workspaceKey, String modelType, ModelSummary model) {
        try {
            String content = designApiClient.getModelContent(workspaceKey, modelType, model.key());
            return new ModelText(model, content, false);
        } catch (Exception e) {
            log.warn("Could not fetch content of {} model '{}': {}", modelType, model.key(), e.getMessage());
            return new ModelText(model, null, true);
        }
    }

    private ModelText resolve(Future<ModelText> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scan interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Scan failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private record ModelText(ModelSummary model, String content, boolean error) {
    }
}
