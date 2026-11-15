package com.ledgerlite.transaction;

import com.ledgerlite.account.Account;
import com.ledgerlite.account.AccountRepository;
import com.ledgerlite.common.dto.PageResponse;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.common.exception.ForbiddenException;
import com.ledgerlite.common.exception.NotFoundException;
import com.ledgerlite.ledger.LedgerService;
import com.ledgerlite.transaction.dto.TransactionResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final LedgerService ledger;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts, LedgerService ledger) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, int page, int size, UUID accountId,
                                                  TransactionType type, Instant from, Instant to) {
        if (page < 0) {
            throw badRequest("page must be 0 or greater.");
        }
        if (size < 1 || size > 100) {
            throw badRequest("size must be between 1 and 100.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("from must not be after to.");
        }

        Set<UUID> scope = accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(Account::getId).collect(Collectors.toSet());

        if (accountId != null) {
            Account account = accounts.findById(accountId)
                    .orElseThrow(() -> new NotFoundException("Account not found."));
            if (!account.getUserId().equals(userId)) {
                throw new ForbiddenException("You do not have access to this account.");
            }
            scope = Set.of(accountId);
        }
        if (scope.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        final Set<UUID> viewerAccounts = scope;
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // IDOR: only transactions that touch the caller's accounts can ever match.
            predicates.add(cb.or(
                    root.get("sourceAccountId").in(viewerAccounts),
                    root.get("destinationAccountId").in(viewerAccounts)));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Transaction> result = transactions.findAll(spec, PageRequest.of(page, size, sort));
        return PageResponse.of(result.map(t -> TransactionResponse.of(t, viewerAccounts, null)));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID transactionId) {
        Transaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
        Set<UUID> mine = accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(Account::getId).collect(Collectors.toSet());
        if (!mine.contains(transaction.getSourceAccountId()) && !mine.contains(transaction.getDestinationAccountId())) {
            throw new ForbiddenException("You do not have access to this transaction.");
        }
        return TransactionResponse.of(transaction, mine, ledger.entriesOf(transaction.getId()));
    }

    private ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid parameter", detail);
    }
}
