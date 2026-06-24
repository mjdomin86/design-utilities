package com.flowable.designbackup.usage;

/**
 * A model that is referenced by no package and no other model, and is
 * therefore a candidate for deletion.
 */
public record UnusedModel(
        String modelKey,
        String modelName,
        String modelType,
        long modelsCount,
        long packageCount) {
}
