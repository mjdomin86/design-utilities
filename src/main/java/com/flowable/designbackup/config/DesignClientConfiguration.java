package com.flowable.designbackup.config;

import com.flowable.designbackup.client.DesignTokenStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to talk to Flowable Design, with the
 * authentication interceptor selected by {@code flowable.design.auth.mode}.
 * Fails fast at startup when the selected mode is missing its credentials,
 * except for {@code oauth2}: that token is read from {@link DesignTokenStore}
 * on every request, so it can be supplied or refreshed later from the UI
 * without restarting the service.
 */
@Configuration(proxyBeanMethods = false)
public class DesignClientConfiguration {

    @Bean
    RestClient designRestClient(RestClient.Builder builder, DesignProperties properties,
            DesignTokenStore tokenStore) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            throw new IllegalStateException("Missing required property 'flowable.design.base-url'");
        }
        return builder
                .baseUrl(properties.baseUrl() + properties.apiPrefix())
                .requestInterceptor(authInterceptor(properties.auth(), tokenStore))
                .build();
    }

    static ClientHttpRequestInterceptor authInterceptor(DesignProperties.Auth auth, DesignTokenStore tokenStore) {
        return switch (auth.mode()) {
            case BASIC -> basicAuthInterceptor(auth.basic());
            case OAUTH2 -> bearerTokenInterceptor(tokenStore);
            case CUSTOM_HEADER -> customHeaderInterceptor(auth.customHeader());
        };
    }

    private static ClientHttpRequestInterceptor basicAuthInterceptor(DesignProperties.Basic basic) {
        if (basic == null || !StringUtils.hasText(basic.username()) || !StringUtils.hasText(basic.password())) {
            throw new IllegalStateException(
                    "Auth mode 'basic' requires 'flowable.design.auth.basic.username' and 'flowable.design.auth.basic.password'");
        }
        return (request, body, execution) -> {
            request.getHeaders().setBasicAuth(basic.username(), basic.password());
            return execution.execute(request, body);
        };
    }

    private static ClientHttpRequestInterceptor bearerTokenInterceptor(DesignTokenStore tokenStore) {
        return (request, body, execution) -> {
            String token = tokenStore.getToken();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException(
                        "Auth mode 'oauth2' requires a token; set 'flowable.design.auth.oauth2.token' "
                                + "or configure it from the UI settings tab");
            }
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        };
    }

    private static ClientHttpRequestInterceptor customHeaderInterceptor(DesignProperties.CustomHeader header) {
        if (header == null || !StringUtils.hasText(header.name()) || !StringUtils.hasText(header.value())) {
            throw new IllegalStateException(
                    "Auth mode 'custom-header' requires 'flowable.design.auth.custom-header.name' and 'flowable.design.auth.custom-header.value'");
        }
        return (request, body, execution) -> {
            request.getHeaders().set(header.name(), header.value());
            return execution.execute(request, body);
        };
    }
}
