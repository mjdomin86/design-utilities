package com.flowable.designbackup.config;

import com.flowable.designbackup.client.AuthMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the Flowable Design instance.
 *
 * <pre>
 * flowable.design.base-url=http://localhost:8081
 * flowable.design.api-prefix=/design-api
 * flowable.design.auth.mode=basic|oauth2|custom-header
 * flowable.design.auth.basic.username=...
 * flowable.design.auth.basic.password=...
 * flowable.design.auth.oauth2.token=...
 * flowable.design.auth.custom-header.name=...
 * flowable.design.auth.custom-header.value=...
 * </pre>
 */
@ConfigurationProperties(prefix = "flowable.design")
public record DesignProperties(
        String baseUrl,
        @DefaultValue("/design-api") String apiPrefix,
        @DefaultValue Auth auth) {

    public record Auth(
            @DefaultValue("basic") AuthMode mode,
            Basic basic,
            OAuth2 oauth2,
            CustomHeader customHeader) {
    }

    public record Basic(String username, String password) {
    }

    public record OAuth2(String token) {
    }

    public record CustomHeader(String name, String value) {
    }
}
