package com.ledgerlite.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException() {
        this("Source account balance is lower than the transfer amount.");
    }

    public InsufficientFundsException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient funds", detail);
    }
}
