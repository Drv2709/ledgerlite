package com.ledgerlite.common.exception;

import java.time.Instant;
import java.util.List;

/** Error body used by every API error (RFC 7807 style, see docs/API_CONTRACT.md). */
public record ApiError(
        String type,
        String title,
        int status,
        String code,
        String detail,
        Instant timestamp,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String title, String code, String detail) {
        return new ApiError("about:blank", title, status, code, detail, Instant.now(), null);
    }

    public static ApiError validation(List<FieldError> fieldErrors) {
        return new ApiError("about:blank", "Validation failed", 400, "VALIDATION_ERROR",
                "One or more fields are invalid.", Instant.now(), fieldErrors);
    }
}
