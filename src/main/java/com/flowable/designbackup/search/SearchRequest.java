package com.flowable.designbackup.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Search input. {@code elementType} is optional and only applies to BPMN
 * models: it narrows element-level matching to elements whose tag name,
 * flowable:type or flowable:extensionId matches (e.g. "service-registry").
 */
public record SearchRequest(
        @NotBlank(message = "workspaceKey is required") String workspaceKey,
        @NotEmpty(message = "at least one model type is required") List<String> modelTypes,
        @NotNull(message = "condition is required") SearchCondition condition,
        @NotBlank(message = "term is required") String term,
        String elementType) {

    public SearchRequest {
        modelTypes = modelTypes == null ? List.of()
                : modelTypes.stream().filter(type -> type != null && !type.isBlank()).toList();
    }
}
