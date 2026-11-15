package com.ledgerlite.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlite.account.Account;
import com.ledgerlite.account.AccountRepository;
import com.ledgerlite.account.AccountType;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.common.exception.ForbiddenException;
import com.ledgerlite.common.exception.InsufficientFundsException;
import com.ledgerlite.common.exception.NotFoundException;
import com.ledgerlite.common.money.Money;
import com.ledgerlite.idempotency.IdempotencyService;
import com.ledgerlite.idempotency.IdempotentResponse;
import com.ledgerlite.ledger.LedgerEntry;
import com.ledgerlite.ledger.LedgerService;
import com.ledgerlite.transaction.Transaction;
import com.ledgerlite.transaction.TransactionRepository;
import com.ledgerlite.transaction.TransactionStatus;
import com.ledgerlite.transaction.TransactionType;
import com.ledgerlite.transaction.dto.TransactionResponse;
import com.ledgerlite.transfer.dto.ReverseRequest;
import com.ledgerlite.transfer.dto.TransferRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public TransferService(AccountRepository accounts, TransactionRepository transactions, LedgerService ledger,
                           IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /**
     * One database transaction: claim idempotency key, validate, lock both accounts (ascending id),
     * check balance, write the transaction + two ledger entries, store the response.
     * Any exception rolls EVERYTHING back, including the idempotency key (failures are not cached).
     */
    @Transactional
    public IdempotentResponse transfer(UUID userId, String rawKey, TransferRequest request) {
        String key = idempotency.validateKey(rawKey);
        BigDecimal amount = Money.normalize(request.amount());
        String destinationNumber = request.destinationAccountNumber().trim().toUpperCase(Locale.ROOT);
        String description = blankToNull(request.description());

        String hash = IdempotencyService.hash("TRANSFER", request.sourceAccountId().toString(),
                destinationNumber, Money.format(amount), description == null ? "" : description);
        IdempotencyService.Claim claim = idempotency.claim(userId, key, hash);
        if (claim.isReplay()) {
            return claim.replay();
        }

        Account source = accounts.findById(request.sourceAccountId())
                .orElseThrow(() -> new NotFoundException("Source account not found."));
        if (!source.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not own the source account.");
        }
        Account destination = accounts.findByAccountNumber(destinationNumber)
                .orElseThrow(() -> new NotFoundException("Destination account not found."));

        if (destination.getType() != AccountType.USER || source.getType() != AccountType.USER) {
            throw badRequest("Transfers to or from system accounts are not allowed.");
        }
        if (source.getId().equals(destination.getId())) {
            throw badRequest("Source and destination accounts must differ.");
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw badRequest("Source and destination accounts must have the same currency.");
        }

        // Row locks, ascending id. Everything that writes ledger entries for these accounts now waits.
        accounts.lockByIdsOrdered(List.of(source.getId(), destination.getId()));

        // Balance is read AFTER the lock, so no concurrent transfer can slip in between check and write.
        if (ledger.balanceOf(source.getId()).compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        Transaction transaction = transactions.saveAndFlush(new Transaction(
                TransactionType.TRANSFER, amount, source.getCurrency(),
                source.getId(), destination.getId(), userId, description, null));
        ledger.post(transaction.getId(), source.getId(), destination.getId(), amount);

        List<LedgerEntry> entries = ledger.entriesOf(transaction.getId());
        return finish(claim, transaction, TransactionResponse.of(transaction, Set.of(source.getId()), entries));
    }

    /** Reverses a TRANSFER by posting opposite entries in a new REVERSAL transaction. Nothing is edited or deleted. */
    @Transactional
    public IdempotentResponse reverse(UUID userId, String rawKey, UUID transactionId, ReverseRequest request) {
        String key = idempotency.validateKey(rawKey);
        String reason = request == null ? null : blankToNull(request.reason());

        String hash = IdempotencyService.hash("REVERSE", transactionId.toString(), reason == null ? "" : reason);
        IdempotencyService.Claim claim = idempotency.claim(userId, key, hash);
        if (claim.isReplay()) {
            return claim.replay();
        }

        // Lock the original FIRST: a concurrent second reversal waits here, then sees status REVERSED.
        Transaction original = transactions.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
        Account originalSource = accounts.findById(original.getSourceAccountId())
                .orElseThrow(() -> new IllegalStateException("Source account missing"));
        if (!originalSource.getUserId().equals(userId)) {
            throw new ForbiddenException("Only the owner of the source account can reverse this transaction.");
        }
        if (original.getType() != TransactionType.TRANSFER) {
            throw badRequest("Only transfers can be reversed.");
        }
        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVERSED", "Already reversed",
                    "This transaction has already been reversed.");
        }

        accounts.lockByIdsOrdered(List.of(original.getSourceAccountId(), original.getDestinationAccountId()));

        if (ledger.balanceOf(original.getDestinationAccountId()).compareTo(original.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "The destination account no longer has enough funds to reverse this transaction.");
        }

        Transaction reversal = transactions.saveAndFlush(new Transaction(
                TransactionType.REVERSAL, original.getAmount(), original.getCurrency(),
                original.getDestinationAccountId(), original.getSourceAccountId(), userId,
                reason == null ? "Reversal" : "Reversal: " + reason, original.getId()));
        ledger.post(reversal.getId(), original.getDestinationAccountId(), original.getSourceAccountId(),
                original.getAmount());

        original.markReversed();      // transaction metadata only; ledger entries are untouched
        transactions.saveAndFlush(original);

        List<LedgerEntry> entries = ledger.entriesOf(reversal.getId());
        return finish(claim, reversal,
                TransactionResponse.of(reversal, Set.of(original.getSourceAccountId()), entries));
    }

    private IdempotentResponse finish(IdempotencyService.Claim claim, Transaction transaction,
                                      TransactionResponse body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize response", e);
        }
        idempotency.complete(claim.rowId(), transaction.getId(), HttpStatus.CREATED.value(), json);
        return new IdempotentResponse(HttpStatus.CREATED.value(), json, false);
    }

    private ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid transfer", detail);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
