package com.flowable.designbackup.client;

import com.flowable.designbackup.config.DesignProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the OAuth2 bearer token used to authenticate against Flowable
 * Design. Unlike {@link DesignProperties}, this value can be replaced at
 * runtime (e.g. from the UI settings tab) without restarting the service,
 * which matters because these tokens are typically short-lived.
 */
@Component
public class DesignTokenStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AtomicReference<String> token = new AtomicReference<>();

    public DesignTokenStore(DesignProperties properties) {
        DesignProperties.OAuth2 oauth2 = properties.auth() == null ? null : properties.auth().oauth2();
        if (oauth2 != null && StringUtils.hasText(oauth2.token())) {
            token.set(oauth2.token().trim());
        }
    }

    public String getToken() {
        return token.get();
    }

    public boolean hasToken() {
        return StringUtils.hasText(token.get());
    }

    public void updateToken(String newToken) {
        if (!StringUtils.hasText(newToken)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        token.set(newToken.trim());
    }

    /**
     * Best-effort decode of the JWT {@code exp} claim, purely to surface
     * when the current token will stop working. Returns {@code null} when
     * no token is set or it is not a decodable JWT with an {@code exp}
     * claim; callers must treat the token as a plain opaque string
     * otherwise.
     */
    public Instant getExpiry() {
        String current = token.get();
        if (!StringUtils.hasText(current)) {
            return null;
        }
        String[] parts = current.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = JSON.readTree(new String(payload, StandardCharsets.UTF_8));
            JsonNode exp = claims.get("exp");
            return exp == null || !exp.isNumber() ? null : Instant.ofEpochSecond(exp.asLong());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
