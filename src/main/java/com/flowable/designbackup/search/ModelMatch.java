package com.flowable.designbackup.search;

import java.util.List;

/**
 * A model that satisfied the search. For BPMN/CMMN models {@code elements}
 * lists the individual matching elements; for other model types matching is
 * model-level and {@code snippet} carries the first matching line (null for
 * NOT_CONTAINS). {@code apps} lists the apps (packages) the model belongs to.
 */
public record ModelMatch(
        String modelKey,
        String modelName,
        String modelType,
        String snippet,
        List<ElementMatch> elements,
        List<AppReference> apps) {

    public ModelMatch {
        elements = elements == null ? List.of() : List.copyOf(elements);
        apps = apps == null ? List.of() : List.copyOf(apps);
    }
}
