package com.flowable.designbackup.client;

import com.flowable.designbackup.config.DesignProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignTokenStoreTest {

    @Test
    void seedsTokenFromPropertiesAtStartup() {
        // Arrange
        DesignTokenStore store = storeWithProperties("seed-token");

        // Act / Assert
        assertThat(store.getToken()).isEqualTo("seed-token");
        assertThat(store.hasToken()).isTrue();
    }

    @Test
    void hasNoTokenWhenPropertiesOmitIt() {
        DesignTokenStore store = storeWithProperties(null);

        assertThat(store.hasToken()).isFalse();
        assertThat(store.getToken()).isNull();
    }

    @Test
    void updateTokenReplacesCurrentValue() {
        // Arrange
        DesignTokenStore store = storeWithProperties("old-token");

        // Act
        store.updateToken("  new-token  ");

        // Assert
        assertThat(store.getToken()).isEqualTo("new-token");
    }

    @Test
    void updateTokenRejectsBlankValue() {
        DesignTokenStore store = storeWithProperties(null);

        assertThatThrownBy(() -> store.updateToken("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token must not be blank");
    }

    @Test
    void getExpiryReadsTheJwtExpClaim() {
        // Arrange
        Instant exp = Instant.parse("2030-01-01T00:00:00Z");
        DesignTokenStore store = storeWithProperties(null);
        store.updateToken(syntheticJwt(exp.getEpochSecond()));

        // Act / Assert
        assertThat(store.getExpiry()).isEqualTo(exp);
    }

    @Test
    void getExpiryReturnsNullWhenTokenIsNotAJwt() {
        DesignTokenStore store = storeWithProperties(null);
        store.updateToken("opaque-token-without-dots");

        assertThat(store.getExpiry()).isNull();
    }

    @Test
    void getExpiryReturnsNullWhenNoTokenIsSet() {
        DesignTokenStore store = storeWithProperties(null);

        assertThat(store.getExpiry()).isNull();
    }

    private static DesignTokenStore storeWithProperties(String seedToken) {
        DesignProperties.Auth auth = new DesignProperties.Auth(
                AuthMode.OAUTH2, null, new DesignProperties.OAuth2(seedToken), null);
        return new DesignTokenStore(new DesignProperties("http://design", "/design-api", auth));
    }

    private static String syntheticJwt(long expEpochSeconds) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                ("{\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
