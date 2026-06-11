package com.flowable.designbackup.search;

/**
 * A single BPMN element that satisfied the search condition.
 */
public record ElementMatch(String elementId, String elementName, String elementType, String flowableType) {
}
