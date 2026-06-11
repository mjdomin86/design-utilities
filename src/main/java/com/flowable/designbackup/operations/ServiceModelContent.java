package com.flowable.designbackup.operations;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Projection of a .service model's content, matching the structure of
 * examples/rest-service-example.service. Field aliases tolerate naming
 * variants ({@code httpMethod} vs {@code method}, {@code path} vs {@code url}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceModelContent(
        String type,
        String key,
        String name,
        List<Operation> operations) {

    public ServiceModelContent {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(
            String key,
            String name,
            Config config,
            List<OperationParameter> inputParameters,
            List<OperationParameter> outputParameters) {

        public Operation {
            inputParameters = inputParameters == null ? List.of() : List.copyOf(inputParameters);
            outputParameters = outputParameters == null ? List.of() : List.copyOf(outputParameters);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(
            @JsonAlias("httpMethod") String method,
            @JsonAlias("path") String url) {
    }
}
