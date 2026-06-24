package com.flowable.designbackup.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of a model's relation list (Design {@code parent-relations} /
 * {@code child-relations}). {@code packageModelKey}/{@code packageModelName}
 * name the package (app) the related model belongs to, when any.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelRelationInfo(
        String packageModelKey,
        String packageModelName,
        String modelKey,
        String modelName,
        String modelType) {
}
