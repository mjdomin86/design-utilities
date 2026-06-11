package com.flowable.designbackup.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Input or output parameter of a service model operation, as found in
 * .service model content (see examples/rest-service-example.service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperationParameter(
        String name,
        String displayName,
        String type,
        Boolean required,
        Boolean queryParameter) {
}
