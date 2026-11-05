package com.flowable.designbackup.migration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request to fix the models the user selected from a prior scan.
 */
public record JacksonFixRequest(
        @NotBlank(message = "workspaceKey is required") String workspaceKey,
        @NotEmpty(message = "models is required") @Valid List<ModelRef> models) {
}
