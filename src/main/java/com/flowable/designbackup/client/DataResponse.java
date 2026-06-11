package com.flowable.designbackup.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Standard Flowable Design list envelope: {@code {data, total, start, size}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataResponse<T>(List<T> data, long total, int start, int size) {

    public DataResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
