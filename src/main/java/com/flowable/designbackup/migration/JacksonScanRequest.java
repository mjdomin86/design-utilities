package com.flowable.designbackup.migration;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request to scan a workspace for leftover Jackson 2 references.
 * {@code modelTypes} is optional: empty means every model type.
 */
public record JacksonScanRequest(
        @NotBlank(message = "workspaceKey is required") String workspaceKey,
        List<String> modelTypes) {
}
