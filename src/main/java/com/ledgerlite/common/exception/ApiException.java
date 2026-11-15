package com.ledgerlite.common.exception;

import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing errors. Later phases add subclasses (e.g. InsufficientFunds). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    public ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
}
