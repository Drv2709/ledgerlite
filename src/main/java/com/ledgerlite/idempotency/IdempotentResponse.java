package com.ledgerlite.idempotency;

/** The exact status and JSON body to send back, plus whether it is a replay of an earlier response. */
public record IdempotentResponse(int status, String body, boolean replayed) {}
