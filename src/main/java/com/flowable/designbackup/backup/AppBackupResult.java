package com.flowable.designbackup.backup;

/**
 * Outcome of backing up a single app. {@code error} is null on success.
 */
public record AppBackupResult(
        String appKey,
        String appName,
        String fileName,
        long sizeBytes,
        boolean success,
        String error) {

    static AppBackupResult success(String appKey, String appName, String fileName, long sizeBytes) {
        return new AppBackupResult(appKey, appName, fileName, sizeBytes, true, null);
    }

    static AppBackupResult failure(String appKey, String appName, String error) {
        return new AppBackupResult(appKey, appName, null, 0, false, error);
    }
}
