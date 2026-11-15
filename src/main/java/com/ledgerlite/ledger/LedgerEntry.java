package com.ledgerlite.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One side of a double-entry posting. Immutable: no setters, Hibernate never updates it,
 * and a Postgres trigger rejects UPDATE/DELETE. Amount is always positive; direction is entryType.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // for JPA
    }

    private LedgerEntry(UUID transactionId, UUID accountId, EntryType entryType, BigDecimal amount) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
    }

    public static LedgerEntry debit(UUID transactionId, UUID accountId, BigDecimal amount) {
        return new LedgerEntry(transactionId, accountId, EntryType.DEBIT, amount);
    }

    public static LedgerEntry credit(UUID transactionId, UUID accountId, BigDecimal amount) {
        return new LedgerEntry(transactionId, accountId, EntryType.CREDIT, amount);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
