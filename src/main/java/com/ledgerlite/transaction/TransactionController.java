package com.ledgerlite.transaction;

import com.ledgerlite.common.dto.PageResponse;
import com.ledgerlite.transaction.dto.TransactionResponse;
import com.ledgerlite.user.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return transactionService.list(user.getId(), page, size, accountId, type, from, to);
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID transactionId) {
        return transactionService.get(user.getId(), transactionId);
    }
}
