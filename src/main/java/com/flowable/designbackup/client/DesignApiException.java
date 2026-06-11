package com.flowable.designbackup.client;

/**
 * Raised when a call to the Flowable Design REST API fails. Carries the HTTP
 * status (0 when the failure happened before a response was received).
 */
public class DesignApiException extends RuntimeException {

    private final int statusCode;

    public DesignApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
