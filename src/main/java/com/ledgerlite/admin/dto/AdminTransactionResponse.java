package com.ledgerlite.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** A transaction as an administrator sees it: not relative to any user, with readable account numbers. */
public record AdminTransactionResponse(
        UUID id,
        String type,
        String status,
        String amount,
        String currency,
        UUID sourceAccountId,
        String sourceAccountNumber,
        UUID destinationAccountId,
        String destinationAccountNumber,
        UUID initiatedBy,
        UUID reversesTransactionId,
        String description,
        Instant createdAt
) {}
