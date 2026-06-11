package com.flowable.designbackup.operations;

import java.util.List;

public record OperationSearchResponse(
        String workspaceKey,
        String appKey,
        String method,
        String path,
        int scannedServiceModels,
        List<ServiceOperationMatch> matches) {

    public OperationSearchResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
