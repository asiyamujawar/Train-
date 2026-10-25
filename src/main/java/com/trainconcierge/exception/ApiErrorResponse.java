package com.trainconcierge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Single response shape returned for every error raised by the application.
 *
 * @param timestamp   moment the error was produced
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param message     human readable summary
 * @param path        request URI that failed
 * @param fieldErrors per-field validation messages, omitted when not applicable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ApiErrorResponse ofValidation(int status, String error, String message, String path,
                                                Map<String, String> fieldErrors) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, path, fieldErrors);
    }
}
