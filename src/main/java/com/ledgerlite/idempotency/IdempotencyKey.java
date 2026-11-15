package com.ledgerlite.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Read-only view. Rows are written with native SQL (INSERT ... ON CONFLICT DO NOTHING) in the repository. */
@Entity
@Immutable
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
        // for JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getTransactionId() { return transactionId; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
}
