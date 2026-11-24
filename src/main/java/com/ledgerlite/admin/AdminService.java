package com.ledgerlite.admin;

import com.ledgerlite.account.Account;
import com.ledgerlite.account.AccountRepository;
import com.ledgerlite.admin.dto.AdminTransactionResponse;
import com.ledgerlite.admin.dto.AdminUserResponse;
import com.ledgerlite.common.dto.PageResponse;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.common.exception.ForbiddenException;
import com.ledgerlite.common.money.Money;
import com.ledgerlite.transaction.Transaction;
import com.ledgerlite.transaction.TransactionRepository;
import com.ledgerlite.transaction.TransactionType;
import com.ledgerlite.user.Role;
import com.ledgerlite.user.User;
import com.ledgerlite.user.UserRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only administration. There is intentionally no method here that changes any data. */
@Service
public class AdminService {

    /** The seeded internal user that owns the SYSTEM funding accounts. It is never shown to admins. */
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository users;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;

    public AdminService(UserRepository users, TransactionRepository transactions, AccountRepository accounts) {
        this.users = users;
        this.transactions = transactions;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(UUID adminId, int page, int size) {
        requireAdmin(adminId);
        PageRequest pageRequest = pageRequest(page, size);
        Page<User> result = users.findByIdNot(SYSTEM_USER_ID, pageRequest);
        return PageResponse.of(result.map(user ->
                new AdminUserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> listTransactions(UUID adminId, int page, int size, UUID accountId,
                                                                  TransactionType type, Instant from, Instant to) {
        requireAdmin(adminId);
        PageRequest pageRequest = pageRequest(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("from must not be after to.");
        }

        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sourceAccountId"), accountId),
                        cb.equal(root.get("destinationAccountId"), accountId)));
            }
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

        Page<Transaction> result = transactions.findAll(spec, pageRequest);

        Set<UUID> accountIds = new HashSet<>();
        for (Transaction transaction : result.getContent()) {
            accountIds.add(transaction.getSourceAccountId());
            accountIds.add(transaction.getDestinationAccountId());
        }
        Map<UUID, String> accountNumbers = accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getAccountNumber));

        return PageResponse.of(result.map(transaction -> toResponse(transaction, accountNumbers)));
    }

    /**
     * Second layer of protection. The role in the JWT can be stale (a demoted admin keeps a valid token
     * for up to an hour), so every admin call re-reads the role from the database.
     */
    private void requireAdmin(UUID userId) {
        boolean isAdmin = users.findById(userId).map(user -> user.getRole() == Role.ADMIN).orElse(false);
        if (!isAdmin) {
            throw new ForbiddenException("Administrator access is required.");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0) {
            throw badRequest("page must be 0 or greater.");
        }
        if (size < 1 || size > 100) {
            throw badRequest("size must be between 1 and 100.");
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private AdminTransactionResponse toResponse(Transaction transaction, Map<UUID, String> accountNumbers) {
        return new AdminTransactionResponse(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                transaction.getSourceAccountId(),
                accountNumbers.get(transaction.getSourceAccountId()),
                transaction.getDestinationAccountId(),
                accountNumbers.get(transaction.getDestinationAccountId()),
                transaction.getInitiatedBy(),
                transaction.getReversesTransactionId(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }

    private ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid parameter", detail);
    }
}
