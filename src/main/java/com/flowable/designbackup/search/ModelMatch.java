package com.flowable.designbackup.search;

import java.util.List;

/**
 * A model that satisfied the search. For BPMN models {@code elements} lists
 * the individual matching elements; for other model types matching is
 * model-level and {@code snippet} carries the first matching line (null for
 * NOT_CONTAINS).
 */
public record ModelMatch(
        String modelKey,
        String modelName,
        String modelType,
        String snippet,
        List<ElementMatch> elements) {

    public ModelMatch {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }
}
