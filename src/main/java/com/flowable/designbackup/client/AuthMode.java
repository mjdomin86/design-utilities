package com.flowable.designbackup.client;

/**
 * Supported authentication modes against the Flowable Design REST API,
 * selected via {@code flowable.design.auth.mode}.
 */
public enum AuthMode {
    BASIC,
    OAUTH2,
    CUSTOM_HEADER
}
