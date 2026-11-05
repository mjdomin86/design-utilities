package com.flowable.designbackup.migration;

/**
 * Outcome of fixing a single model. {@code error} is null on success.
 */
public record ModelFixResult(
        String modelKey,
        String modelType,
        boolean success,
        int occurrencesFixed,
        String error) {

    static ModelFixResult success(String modelKey, String modelType, int occurrencesFixed) {
        return new ModelFixResult(modelKey, modelType, true, occurrencesFixed, null);
    }

    static ModelFixResult failure(String modelKey, String modelType, String error) {
        return new ModelFixResult(modelKey, modelType, false, 0, error);
    }
}
