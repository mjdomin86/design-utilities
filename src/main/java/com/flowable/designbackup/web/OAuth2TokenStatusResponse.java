package com.flowable.designbackup.web;

import com.flowable.designbackup.client.AuthMode;

import java.time.Instant;

/**
 * Current state of the Flowable Design OAuth2 token, for the UI settings
 * tab. {@code expiresAt} is {@code null} when no token is set or its
 * expiry could not be determined (not a JWT, or no {@code exp} claim).
 */
public record OAuth2TokenStatusResponse(
        AuthMode mode,
        boolean configured,
        Instant expiresAt,
        boolean expired) {
}
