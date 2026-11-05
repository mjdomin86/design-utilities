package com.flowable.designbackup.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for replacing the Flowable Design OAuth2 bearer token at runtime.
 */
public record OAuth2TokenRequest(
        @NotBlank(message = "token is required") String token) {
}
