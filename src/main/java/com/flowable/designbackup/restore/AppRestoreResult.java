package com.flowable.designbackup.restore;

/**
 * Outcome of restoring a single app zip. {@code appName} is null when it
 * could not be recovered from the backup's manifest.json. {@code error} is
 * null on success.
 */
public record AppRestoreResult(
        String appKey,
        String appName,
        String fileName,
        long sizeBytes,
        boolean success,
        String error) {

    static AppRestoreResult success(String appKey, String appName, String fileName, long sizeBytes) {
        return new AppRestoreResult(appKey, appName, fileName, sizeBytes, true, null);
    }

    static AppRestoreResult failure(String appKey, String appName, String fileName, String error) {
        return new AppRestoreResult(appKey, appName, fileName, 0, false, error);
    }
}
