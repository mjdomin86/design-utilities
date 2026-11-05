package com.flowable.designbackup.web;

import com.flowable.designbackup.client.DesignTokenStore;
import com.flowable.designbackup.config.DesignProperties;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Lets the UI inspect and replace the Flowable Design OAuth2 bearer token
 * at runtime, without a restart. Needed because these tokens are typically
 * short-lived (minutes to a few hours).
 */
@RestController
@RequestMapping("/api/config/oauth2-token")
public class AuthConfigController {

    private final DesignProperties properties;
    private final DesignTokenStore tokenStore;

    public AuthConfigController(DesignProperties properties, DesignTokenStore tokenStore) {
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    @GetMapping
    public OAuth2TokenStatusResponse getStatus() {
        return status();
    }

    @PutMapping
    public OAuth2TokenStatusResponse updateToken(@Valid @RequestBody OAuth2TokenRequest request) {
        tokenStore.updateToken(request.token());
        return status();
    }

    private OAuth2TokenStatusResponse status() {
        Instant expiresAt = tokenStore.getExpiry();
        boolean expired = expiresAt != null && expiresAt.isBefore(Instant.now());
        return new OAuth2TokenStatusResponse(properties.auth().mode(), tokenStore.hasToken(), expiresAt, expired);
    }
}
