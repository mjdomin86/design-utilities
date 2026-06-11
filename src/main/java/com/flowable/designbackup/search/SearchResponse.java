package com.flowable.designbackup.search;

import java.util.List;

public record SearchResponse(
        String workspaceKey,
        String term,
        SearchCondition condition,
        int scannedModels,
        int erroredModels,
        List<ModelMatch> matches) {

    public SearchResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
