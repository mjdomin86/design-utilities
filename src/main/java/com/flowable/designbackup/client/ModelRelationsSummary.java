package com.flowable.designbackup.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Usage summary of a model (Design {@code parent-relations-summary}):
 * whether and how often the model is referenced by packages and other models.
 *
 * @param usedInPackage whether the model is directly used in a package (app/plugin)
 * @param packageCount  number of apps/plugins using the model
 * @param modelsCount   number of other models using the model
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelRelationsSummary(boolean usedInPackage, long packageCount, long modelsCount) {
}
