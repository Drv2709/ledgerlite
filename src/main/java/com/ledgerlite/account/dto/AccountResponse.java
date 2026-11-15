package com.ledgerlite.account.dto;

import com.ledgerlite.account.Account;
import com.ledgerlite.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** balance is a decimal STRING so JavaScript never turns money into a floating-point number. */
public record AccountResponse(
        UUID id,
        String accountNumber,
        String name,
        String currency,
        String balance,
        Instant createdAt
) {
    public static AccountResponse from(Account account, BigDecimal balance) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getName(),
                account.getCurrency(), Money.format(balance), account.getCreatedAt());
    }
}
