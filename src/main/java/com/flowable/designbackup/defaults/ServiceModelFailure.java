package com.flowable.designbackup.defaults;

/**
 * A .service entry of the app export that could not be read; it is
 * re-imported unchanged.
 */
public record ServiceModelFailure(String entryName, String error) {
}
