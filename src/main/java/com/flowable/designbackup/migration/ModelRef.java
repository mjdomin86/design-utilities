package com.flowable.designbackup.migration;

import jakarta.validation.constraints.NotBlank;

/**
 * Identifies a single model to fix, as picked by the user from scan results.
 */
public record ModelRef(
        @NotBlank(message = "modelType is required") String modelType,
        @NotBlank(message = "modelKey is required") String modelKey) {
}
