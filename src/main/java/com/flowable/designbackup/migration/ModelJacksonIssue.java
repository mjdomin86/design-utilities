package com.flowable.designbackup.migration;

import com.flowable.designbackup.search.AppReference;

import java.util.List;

/**
 * A model found to still reference the old Jackson 2 package. {@code
 * needsManualReview} flags models that also mention {@code IOException},
 * since Jackson 3 throws an unchecked exception instead — the package
 * rename alone is not enough there and needs a human look.
 */
public record ModelJacksonIssue(
        String modelKey,
        String modelName,
        String modelType,
        int occurrences,
        boolean needsManualReview,
        List<AppReference> apps) {

    public ModelJacksonIssue {
        apps = apps == null ? List.of() : List.copyOf(apps);
    }
}
