package com.flowable.designbackup.usage;

import java.util.List;

public record UnusedModelsResponse(
        String workspaceKey,
        int scannedModels,
        int erroredModels,
        List<UnusedModel> unusedModels) {

    public UnusedModelsResponse {
        unusedModels = unusedModels == null ? List.of() : List.copyOf(unusedModels);
    }
}
