package com.flowable.designbackup.migration;

import java.util.List;

/**
 * Outcome of a workspace scan for leftover Jackson 2 references.
 */
public record JacksonScanResponse(
        String workspaceKey,
        int scannedModels,
        int erroredModels,
        List<ModelJacksonIssue> matches) {

    public JacksonScanResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
