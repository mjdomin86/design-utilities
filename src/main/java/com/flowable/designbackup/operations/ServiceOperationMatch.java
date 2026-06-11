package com.flowable.designbackup.operations;

import java.util.List;

/**
 * A service model operation matching the requested HTTP method and path.
 */
public record ServiceOperationMatch(
        String serviceModelKey,
        String serviceModelName,
        String operationKey,
        String operationName,
        String method,
        String url,
        List<OperationParameter> inputParameters,
        List<OperationParameter> outputParameters) {

    public ServiceOperationMatch {
        inputParameters = inputParameters == null ? List.of() : List.copyOf(inputParameters);
        outputParameters = outputParameters == null ? List.of() : List.copyOf(outputParameters);
    }
}
