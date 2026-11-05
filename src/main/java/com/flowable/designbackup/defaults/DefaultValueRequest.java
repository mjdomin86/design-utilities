package com.flowable.designbackup.defaults;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to add a default value to every number output parameter of every
 * service model in an app. {@code defaultValue} is optional and defaults to
 * {@code 0}; it must be a whole number so it is valid for every number type.
 */
public record DefaultValueRequest(
        @NotBlank(message = "workspaceKey is required") String workspaceKey,
        @NotBlank(message = "appKey is required") String appKey,
        @Pattern(regexp = "-?\\d+", message = "defaultValue must be a whole number") String defaultValue) {

    static final String DEFAULT_VALUE = "0";

    public String effectiveDefaultValue() {
        return defaultValue == null || defaultValue.isBlank() ? DEFAULT_VALUE : defaultValue;
    }
}
