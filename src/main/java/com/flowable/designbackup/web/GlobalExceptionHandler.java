package com.flowable.designbackup.web;

import com.flowable.designbackup.client.DesignApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;

/**
 * Maps service errors to ProblemDetail responses with messages safe to show
 * in the UI; full details are logged server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DesignApiException.class)
    public ProblemDetail handleDesignApi(DesignApiException e) {
        log.error("Flowable Design API error", e);
        String detail = e.getMessage();
        if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
            detail += " — check the flowable.design.auth.* configuration";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejected request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ProblemDetail handleIo(UncheckedIOException e) {
        log.error("I/O error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException e) {
        log.error("Internal error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
