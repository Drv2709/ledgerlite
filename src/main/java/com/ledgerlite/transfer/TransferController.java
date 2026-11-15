package com.ledgerlite.transfer;

import com.ledgerlite.idempotency.IdempotentResponse;
import com.ledgerlite.transfer.dto.ReverseRequest;
import com.ledgerlite.transfer.dto.TransferRequest;
import com.ledgerlite.user.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<String> transfer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return toResponse(transferService.transfer(user.getId(), idempotencyKey, request));
    }

    @PostMapping("/api/transactions/{transactionId}/reverse")
    public ResponseEntity<String> reverse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID transactionId,
            @Valid @RequestBody(required = false) ReverseRequest request) {
        return toResponse(transferService.reverse(user.getId(), idempotencyKey, transactionId, request));
    }

    /** Sends the stored JSON exactly as it was first produced; replays are marked with a header. */
    private ResponseEntity<String> toResponse(IdempotentResponse result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(result.body());
    }
}
