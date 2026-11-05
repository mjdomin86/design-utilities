package com.flowable.designbackup.migration;

import java.util.List;

/**
 * Outcome of fixing the user-selected models.
 */
public record JacksonFixResponse(
        String workspaceKey,
        int totalModels,
        int successCount,
        int failureCount,
        List<ModelFixResult> results) {

    public JacksonFixResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
