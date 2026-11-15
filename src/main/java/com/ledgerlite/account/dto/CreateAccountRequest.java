package com.ledgerlite.account.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String name,

        @NotBlank
        @Pattern(regexp = "(?i)USD|CAD|EUR|GBP|INR", message = "must be one of USD, CAD, EUR, GBP, INR")
        String currency,

        // Optional demo funding. At most 4 decimals: rejected, never silently rounded.
        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "1000000.00", message = "must not exceed 1000000")
        @Digits(integer = 7, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal initialDeposit
) {}
