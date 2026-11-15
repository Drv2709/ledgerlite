package com.ledgerlite.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", detail);
    }
}
