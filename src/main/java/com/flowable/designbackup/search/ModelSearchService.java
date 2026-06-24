package com.flowable.designbackup.search;

import com.flowable.designbackup.client.DesignApiClient;
import com.flowable.designbackup.client.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Searches a term across all models of the selected types in a workspace.
 * BPMN models are evaluated element by element via {@link BpmnElementSearcher};
 * other model types are matched on their whole content.
 */
@Service
public class ModelSearchService {

    private static final Logger log = LoggerFactory.getLogger(ModelSearchService.class);
    private static final int CONTENT_FETCH_CONCURRENCY = 4;
    private static final int SNIPPET_MAX_LENGTH = 200;
    /** XML-based model types searched element by element via their export. */
    private static final Set<String> XML_MODEL_TYPES = Set.of("bpmn", "cmmn");

    private final DesignApiClient designApiClient;
    private final BpmnElementSearcher bpmnElementSearcher;

    public ModelSearchService(DesignApiClient designApiClient, BpmnElementSearcher bpmnElementSearcher) {
        this.designApiClient = designApiClient;
        this.bpmnElementSearcher = bpmnElementSearcher;
    }

    public SearchResponse search(SearchRequest request) {
        List<ModelMatch> matches = new ArrayList<>();
        int scanned = 0;
        int errored = 0;
        for (String modelType : request.modelTypes()) {
            List<ModelSummary> models = designApiClient.listModels(request.workspaceKey(), modelType);
            List<ModelContent> contents = fetchContents(request.workspaceKey(), modelType, models);
            for (ModelContent content : contents) {
                scanned++;
                if (content.error()) {
                    errored++;
                    continue;
                }
                matchModel(request, content).ifPresent(matches::add);
            }
        }
        return new SearchResponse(request.workspaceKey(), request.term(), request.condition(),
                scanned, errored, matches);
    }

    private Optional<ModelMatch> matchModel(SearchRequest request, ModelContent content) {
        ModelSummary model = content.model();
        if (XML_MODEL_TYPES.contains(model.modelType())) {
            List<ElementMatch> elements = bpmnElementSearcher.findMatchingElements(
                    content.content(), request.condition(), request.term(), request.elementType());
            if (elements.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ModelMatch(model.key(), model.name(), model.modelType(),
                    null, elements, resolveApps(request.workspaceKey(), model)));
        }
        if (!request.condition().matches(content.content(), request.term())) {
            return Optional.empty();
        }
        return Optional.of(new ModelMatch(model.key(), model.name(), model.modelType(),
                extractSnippet(content.content(), request), List.of(),
                resolveApps(request.workspaceKey(), model)));
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
            log.warn("Could not resolve apps for {} model '{}': {}",
                    model.modelType(), model.key(), e.getMessage());
            return List.of();
        }
    }

    private String extractSnippet(String content, SearchRequest request) {
        if (request.condition() == SearchCondition.NOT_CONTAINS) {
            return null;
        }
        return content.lines()
                .filter(line -> request.condition().matches(line, request.term()))
                .findFirst()
                .map(String::strip)
                .map(line -> line.length() > SNIPPET_MAX_LENGTH ? line.substring(0, SNIPPET_MAX_LENGTH) + "…" : line)
                .orElse(null);
    }

    private List<ModelContent> fetchContents(String workspaceKey, String modelType, List<ModelSummary> models) {
        try (ExecutorService executor = Executors.newFixedThreadPool(CONTENT_FETCH_CONCURRENCY)) {
            List<Future<ModelContent>> futures = models.stream()
                    .map(model -> executor.submit(() -> fetchContent(workspaceKey, modelType, model)))
                    .toList();
            List<ModelContent> contents = new ArrayList<>();
            for (Future<ModelContent> future : futures) {
                contents.add(resolve(future));
            }
            return contents;
        }
    }

    private ModelContent fetchContent(String workspaceKey, String modelType, ModelSummary model) {
        try {
            // BPMN/CMMN models are stored as Design-internal JSON; only the
            // export carries the XML the element-level search needs.
            String content = XML_MODEL_TYPES.contains(modelType)
                    ? designApiClient.exportModel(workspaceKey, modelType, model.key())
                    : designApiClient.getModelContent(workspaceKey, modelType, model.key());
            return new ModelContent(model, content, false);
        } catch (Exception e) {
            log.warn("Could not fetch content of {} model '{}': {}", modelType, model.key(), e.getMessage());
            return new ModelContent(model, null, true);
        }
    }

    private ModelContent resolve(Future<ModelContent> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Search interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Search failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private record ModelContent(ModelSummary model, String content, boolean error) {
    }
}
