package com.flowable.designbackup.web;

import com.flowable.designbackup.client.AuthMode;
import com.flowable.designbackup.client.DesignTokenStore;
import com.flowable.designbackup.config.DesignProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigControllerTest {

    @Test
    void reportsUnconfiguredStatusWhenNoTokenIsSet() {
        // Arrange
        AuthConfigController controller = controllerWithToken(null);

        // Act
        OAuth2TokenStatusResponse status = controller.getStatus();

        // Assert
        assertThat(status.mode()).isEqualTo(AuthMode.OAUTH2);
        assertThat(status.configured()).isFalse();
        assertThat(status.expiresAt()).isNull();
        assertThat(status.expired()).isFalse();
    }

    @Test
    void updateTokenStoresNewTokenAndReturnsRefreshedStatus() {
        // Arrange
        AuthConfigController controller = controllerWithToken(null);

        // Act
        OAuth2TokenStatusResponse status = controller.updateToken(new OAuth2TokenRequest("new-token"));

        // Assert
        assertThat(status.configured()).isTrue();
        assertThat(status.expiresAt()).isNull();
    }

    @Test
    void updateTokenRejectsBlankToken() {
        // Arrange
        AuthConfigController controller = controllerWithToken(null);

        // Act / Assert
        assertThatThrownBy(() -> controller.updateToken(new OAuth2TokenRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsExpiredWhenTokenExpiryIsInThePast() {
        // Arrange
        AuthConfigController controller = controllerWithToken(null);
        long pastEpochSeconds = Instant.parse("2000-01-01T00:00:00Z").getEpochSecond();

        // Act
        OAuth2TokenStatusResponse status = controller.updateToken(
                new OAuth2TokenRequest(syntheticJwt(pastEpochSeconds)));

        // Assert
        assertThat(status.configured()).isTrue();
        assertThat(status.expired()).isTrue();
    }

    private static AuthConfigController controllerWithToken(String seedToken) {
        DesignProperties.Auth auth = new DesignProperties.Auth(
                AuthMode.OAUTH2, null, new DesignProperties.OAuth2(seedToken), null);
        DesignProperties properties = new DesignProperties("http://design", "/design-api", auth);
        return new AuthConfigController(properties, new DesignTokenStore(properties));
    }

    private static String syntheticJwt(long expEpochSeconds) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                ("{\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
