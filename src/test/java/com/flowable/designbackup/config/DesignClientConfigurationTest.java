package com.flowable.designbackup.config;

import com.flowable.designbackup.client.AuthMode;
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
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth));

        // Assert
        String expected = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expected);
    }

    @Test
    void oauth2ModeSetsBearerAuthorizationHeader() throws Exception {
        // Arrange
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.OAUTH2,
                null, new DesignProperties.OAuth2("my-token"), null);

        // Act
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth));

        // Assert
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-token");
    }

    @Test
    void customHeaderModeSetsConfiguredHeader() throws Exception {
        // Arrange
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.CUSTOM_HEADER,
                null, null, new DesignProperties.CustomHeader("X-Api-Key", "key-123"));

        // Act
        HttpHeaders headers = executeInterceptor(DesignClientConfiguration.authInterceptor(auth));

        // Assert
        assertThat(headers.getFirst("X-Api-Key")).isEqualTo("key-123");
    }

    @Test
    void throwsWhenBasicCredentialsAreMissing() {
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.BASIC, null, null, null);

        assertThatThrownBy(() -> DesignClientConfiguration.authInterceptor(auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.basic.username");
    }

    @Test
    void throwsWhenOauth2TokenIsMissing() {
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.OAUTH2,
                null, new DesignProperties.OAuth2(""), null);

        assertThatThrownBy(() -> DesignClientConfiguration.authInterceptor(auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.oauth2.token");
    }

    @Test
    void throwsWhenCustomHeaderIsMissing() {
        DesignProperties.Auth auth = new DesignProperties.Auth(AuthMode.CUSTOM_HEADER, null, null, null);

        assertThatThrownBy(() -> DesignClientConfiguration.authInterceptor(auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flowable.design.auth.custom-header.name");
    }

    private HttpHeaders executeInterceptor(ClientHttpRequestInterceptor interceptor) throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://design/test"));
        interceptor.intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        return request.getHeaders();
    }
}
