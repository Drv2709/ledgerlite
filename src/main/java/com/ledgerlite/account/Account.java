package com.ledgerlite.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An account has NO balance field. Balance is always derived from ledger entries. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, updatable = false, length = 20)
    private String accountNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private AccountType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(UUID userId, String accountNumber, String name, String currency, AccountType type) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.name = name;
        this.currency = currency;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAccountNumber() { return accountNumber; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public AccountType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
}
