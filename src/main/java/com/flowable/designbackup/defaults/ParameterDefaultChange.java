package com.flowable.designbackup.defaults;

/**
 * A single number output parameter that received a default value.
 */
public record ParameterDefaultChange(
        String serviceModelKey,
        String serviceModelName,
        String operationKey,
        String operationName,
        String parameterName,
        String parameterType) {
}
