package com.flowable.designbackup.config;

import com.flowable.designbackup.client.AuthMode;
import com.flowable.designbackup.client.DesignTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignClientConfigurationTest {

    @Test
    void basicModeSetsBasicAuthorizationHeader() throws Exception {
        // Arrange
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.BASIC,
                new DesignProperties.Basic("admin", "secret"), null, null);

        // Act
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth, null));

        // Assert
        String expected = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expected);
    }

    @Test
    void oauth2ModeSetsBearerAuthorizationHeaderFromTokenStore() throws Exception {
        // Arrange
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.OAUTH2, null, null, null);
        DesignTokenStore tokenStore = tokenStoreWithToken("my-token");

        // Act
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth, tokenStore));

        // Assert
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-token");
    }

    @Test
    void oauth2ModePicksUpTokenUpdatedAfterInterceptorWasBuilt() throws Exception {
        // Arrange: the interceptor is built once, the token can change later (e.g. via the UI).
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.OAUTH2, null, null, null);
        DesignTokenStore tokenStore = tokenStoreWithToken("first-token");
        ClientHttpRequestInterceptor interceptor = DesignClientConfiguration.authInterceptor(auth, tokenStore);

        // Act
        tokenStore.updateToken("refreshed-token");
        HttpHeaders headers = executeInterceptor(interceptor);

        // Assert
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer refreshed-token");
    }

    @Test
    void customHeaderModeSetsConfiguredHeader() throws Exception {
        // Arrange
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.CUSTOM_HEADER,
                null, null, new DesignProperties.CustomHeader("X-Api-Key", "key-123"));

        // Act
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth, null));

        // Assert
        assertThat(headers.getFirst("X-Api-Key")).isEqualTo("key-123");
    }

    @Test
    void throwsWhenBasicCredentialsAreMissing() {
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.BASIC, null, null, null);

        assertThatThrownBy(() -> DesignClientConfiguration.authInterceptor(auth, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.basic.username");
    }

    @Test
    void throwsWhenOauth2TokenIsMissingAtRequestTime() {
        // Arrange: no startup failure for oauth2 — the token may be supplied later via the UI.
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.OAUTH2, null, null, null);
        DesignTokenStore tokenStore = tokenStoreWithToken(null);
        ClientHttpRequestInterceptor interceptor = DesignClientConfiguration.authInterceptor(auth, tokenStore);

        assertThatThrownBy(() -> executeInterceptor(interceptor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.oauth2.token");
    }

    @Test
    void throwsWhenCustomHeaderIsMissing() {
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.CUSTOM_HEADER, null, null, null);

        assertThatThrownBy(() -> DesignClientConfiguration.authInterceptor(auth, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.custom-header.name");
    }

    private static DesignTokenStore tokenStoreWithToken(String token) {
        DesignProperties.Auth seedAuth = new DesignProperties.Auth(
                AuthMode.OAUTH2, null, new DesignProperties.OAuth2(token), null);
        DesignProperties seedProperties = new DesignProperties("http://design", "/design-api", seedAuth);
        return new DesignTokenStore(seedProperties);
    }

    private HttpHeaders executeInterceptor(ClientHttpRequestInterceptor interceptor) throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://design/test"));
        interceptor.intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        return request.getHeaders();
    }
}
