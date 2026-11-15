package com.ledgerlite.transaction.dto;

import com.ledgerlite.common.money.Money;
import com.ledgerlite.ledger.LedgerEntry;
import com.ledgerlite.transaction.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Amounts are decimal strings. "entries" is only filled for single-transaction and transfer responses. */
public record TransactionResponse(
        UUID id,
        String type,
        String status,
        String amount,
        String currency,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String direction,
        UUID reversesTransactionId,
        String description,
        Instant createdAt,
        List<EntryResponse> entries
) {
    public record EntryResponse(UUID accountId, String entryType, String amount) {}

    /** direction is OUT if the money left one of the viewer's accounts, otherwise IN. */
    public static TransactionResponse of(Transaction t, Set<UUID> viewerAccountIds, List<LedgerEntry> entries) {
        String direction = viewerAccountIds.contains(t.getSourceAccountId()) ? "OUT" : "IN";
        List<EntryResponse> entryResponses = entries == null ? null : entries.stream()
                .map(e -> new EntryResponse(e.getAccountId(), e.getEntryType().name(), Money.format(e.getAmount())))
                .toList();
        return new TransactionResponse(t.getId(), t.getType().name(), t.getStatus().name(),
                Money.format(t.getAmount()), t.getCurrency(), t.getSourceAccountId(), t.getDestinationAccountId(),
                direction, t.getReversesTransactionId(), t.getDescription(), t.getCreatedAt(), entryResponses);
    }
}
