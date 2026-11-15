package com.ledgerlite.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Header for a set of ledger entries. Only "status" can ever change (COMPLETED to REVERSED);
 * the money itself lives in the immutable ledger entries.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private UUID initiatedBy;

    @Column(name = "reverses_transaction_id", updatable = false)
    private UUID reversesTransactionId;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // for JPA
    }

    public Transaction(TransactionType type, BigDecimal amount, String currency,
                       UUID sourceAccountId, UUID destinationAccountId, UUID initiatedBy,
                       String description, UUID reversesTransactionId) {
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.amount = amount;
        this.currency = currency;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.initiatedBy = initiatedBy;
        this.description = description;
        this.reversesTransactionId = reversesTransactionId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markReversed() {
        this.status = TransactionStatus.REVERSED;
    }

    public UUID getId() { return id; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public UUID getInitiatedBy() { return initiatedBy; }
    public UUID getReversesTransactionId() { return reversesTransactionId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
