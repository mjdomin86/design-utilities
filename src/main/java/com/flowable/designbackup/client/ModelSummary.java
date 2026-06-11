package com.flowable.designbackup.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of a Design model or package (app) list entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelSummary(String id, String key, String name, String modelType) {
}
