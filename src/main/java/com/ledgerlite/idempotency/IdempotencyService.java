package com.ledgerlite.idempotency;

import com.ledgerlite.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class IdempotencyService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Either "this request now owns the key" (replay == null) or "this is a retry" (replay holds the stored response). */
    public record Claim(UUID rowId, IdempotentResponse replay) {
        public boolean isReplay() {
            return replay != null;
        }
    }

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public String validateKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required",
                    "The Idempotency-Key header is required.");
        }
        String key = raw.trim();
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid Idempotency-Key",
                    "Idempotency-Key must be 1 to 64 characters: letters, digits, '.', '_' or '-'.");
        }
        return key;
    }

    /** SHA-256 over length-prefixed parts, so "ab|c" and "a|bc" can never collide. */
    public static String hash(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part.length() + ":" + part + ";").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** MANDATORY: the key row must commit or roll back together with the money movement. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Claim claim(UUID userId, String key, String requestHash) {
        UUID rowId = UUID.randomUUID();
        if (repository.insertIfAbsent(rowId, userId, key, requestHash) == 1) {
            return new Claim(rowId, null);
        }

        IdempotencyKey existing = repository.findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished"));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key reused", "This Idempotency-Key was already used with a different request.");
        }
        if (existing.getResponseBody() == null || existing.getResponseStatus() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
                    "Request in progress", "A request with this Idempotency-Key is still being processed.");
        }
        return new Claim(existing.getId(),
                new IdempotentResponse(existing.getResponseStatus(), existing.getResponseBody(), true));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID rowId, UUID transactionId, int status, String body) {
        repository.complete(rowId, transactionId, status, body);
    }
}
