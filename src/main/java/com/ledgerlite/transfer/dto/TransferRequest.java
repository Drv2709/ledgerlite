package com.ledgerlite.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceAccountId,

        @NotBlank @Size(max = 20) String destinationAccountNumber,

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 255) String description
) {}
