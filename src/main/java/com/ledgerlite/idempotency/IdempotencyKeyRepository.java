package com.ledgerlite.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Returns 1 if this request now owns the key, 0 if the key already exists. If another transaction
     * is inserting the same key right now, Postgres makes this statement WAIT until that transaction
     * commits or rolls back, so two concurrent retries can never both proceed.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO idempotency_keys (id, user_id, idempotency_key, request_hash)
            VALUES (:id, :userId, :key, :hash)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
                       @Param("key") String key, @Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE idempotency_keys
               SET transaction_id = :txId, response_status = :status, response_body = :body
             WHERE id = :id
            """, nativeQuery = true)
    int complete(@Param("id") UUID id, @Param("txId") UUID txId,
                 @Param("status") int status, @Param("body") String body);
}
