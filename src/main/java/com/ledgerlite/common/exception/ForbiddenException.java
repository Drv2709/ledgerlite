package com.ledgerlite.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", detail);
    }
}
