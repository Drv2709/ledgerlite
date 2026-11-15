package com.ledgerlite.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlite.account.Account;
import com.ledgerlite.account.AccountRepository;
import com.ledgerlite.account.AccountType;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.common.exception.ForbiddenException;
import com.ledgerlite.common.exception.InsufficientFundsException;
import com.ledgerlite.idempotency.IdempotencyService;
import com.ledgerlite.idempotency.IdempotentResponse;
import com.ledgerlite.ledger.LedgerService;
import com.ledgerlite.transaction.Transaction;
import com.ledgerlite.transaction.TransactionRepository;
import com.ledgerlite.transaction.TransactionType;
import com.ledgerlite.transfer.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: every collaborator is a mock, so these pin down the ORDER and the CONDITIONS of the
 * transfer algorithm (lock before balance read, never write on a failed check, never touch money on replay).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID SRC_ID = UUID.randomUUID();
    private static final UUID DST_ID = UUID.randomUUID();
    private static final UUID TX_ID = UUID.randomUUID();
    private static final String DST_NUMBER = "LL-0000000002";

    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private LedgerService ledger;
    @Mock private IdempotencyService idempotency;

    private TransferService service;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, transactions, ledger, idempotency,
                new ObjectMapper().findAndRegisterModules());

        source = account(SRC_ID, USER, "LL-0000000001", "USD", AccountType.USER);
        destination = account(DST_ID, OTHER_USER, DST_NUMBER, "USD", AccountType.USER);

        when(idempotency.validateKey(any())).thenReturn("key-1");
        when(idempotency.claim(eq(USER), eq("key-1"), anyString()))
                .thenReturn(new IdempotencyService.Claim(UUID.randomUUID(), null));
        when(accounts.findById(SRC_ID)).thenReturn(Optional.of(source));
        when(accounts.findByAccountNumber(DST_NUMBER)).thenReturn(Optional.of(destination));
        when(ledger.balanceOf(SRC_ID)).thenReturn(new BigDecimal("100.0000"));
        when(transactions.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", TX_ID);
            return saved;
        });
    }

    @Test
    void postsOneBalancedPairWhenFundsAreSufficient() {
        IdempotentResponse response = service.transfer(USER, "key-1", request("25.00"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.replayed()).isFalse();
        verify(ledger).post(TX_ID, SRC_ID, DST_ID, new BigDecimal("25.0000"));
    }

    @Test
    void rejectsAnOverdraftWithoutWritingAnything() {
        when(ledger.balanceOf(SRC_ID)).thenReturn(new BigDecimal("10.0000"));

        assertThrows(InsufficientFundsException.class,
                () -> service.transfer(USER, "key-1", request("25.00")));

        verify(transactions, never()).saveAndFlush(any());
        verify(ledger, never()).post(any(), any(), any(), any());
    }

    @Test
    void allowsATransferOfExactlyTheWholeBalance() {
        when(ledger.balanceOf(SRC_ID)).thenReturn(new BigDecimal("25.0000"));

        IdempotentResponse response = service.transfer(USER, "key-1", request("25.00"));

        assertThat(response.status()).isEqualTo(201);
        verify(ledger).post(TX_ID, SRC_ID, DST_ID, new BigDecimal("25.0000"));
    }

    @Test
    void locksBothAccountsBeforeReadingTheBalanceAndPostsAfterwards() {
        InOrder order = inOrder(accounts, ledger);

        service.transfer(USER, "key-1", request("25.00"));

        order.verify(accounts).lockByIdsOrdered(
                argThat((Collection<UUID> ids) -> ids.contains(SRC_ID) && ids.contains(DST_ID)));
        order.verify(ledger).balanceOf(SRC_ID);
        order.verify(ledger).post(TX_ID, SRC_ID, DST_ID, new BigDecimal("25.0000"));
    }

    @Test
    void rejectsASourceAccountOwnedBySomeoneElseBeforeTouchingMoney() {
        when(accounts.findById(SRC_ID))
                .thenReturn(Optional.of(account(SRC_ID, OTHER_USER, "LL-0000000001", "USD", AccountType.USER)));

        assertThrows(ForbiddenException.class, () -> service.transfer(USER, "key-1", request("25.00")));

        verify(accounts, never()).lockByIdsOrdered(any());
        verifyNoInteractions(ledger, transactions);
    }

    @Test
    void rejectsDifferentCurrencies() {
        when(accounts.findByAccountNumber(DST_NUMBER))
                .thenReturn(Optional.of(account(DST_ID, OTHER_USER, DST_NUMBER, "CAD", AccountType.USER)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.transfer(USER, "key-1", request("25.00")));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(accounts, never()).lockByIdsOrdered(any());
    }

    @Test
    void rejectsASystemAccountAsDestination() {
        when(accounts.findByAccountNumber(DST_NUMBER))
                .thenReturn(Optional.of(account(DST_ID, OTHER_USER, DST_NUMBER, "USD", AccountType.SYSTEM)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.transfer(USER, "key-1", request("25.00")));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(accounts, never()).lockByIdsOrdered(any());
    }

    @Test
    void rejectsTheSameAccountOnBothSides() {
        when(accounts.findByAccountNumber(DST_NUMBER)).thenReturn(Optional.of(source));

        ApiException error = assertThrows(ApiException.class,
                () -> service.transfer(USER, "key-1", request("25.00")));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(accounts, never()).lockByIdsOrdered(any());
    }

    @Test
    void anIdempotentReplayReturnsTheStoredResponseAndTouchesNothing() {
        IdempotentResponse stored = new IdempotentResponse(201, "{\"stored\":true}", true);
        when(idempotency.claim(eq(USER), eq("key-1"), anyString()))
                .thenReturn(new IdempotencyService.Claim(UUID.randomUUID(), stored));

        IdempotentResponse result = service.transfer(USER, "key-1", request("25.00"));

        assertThat(result).isEqualTo(stored);
        verifyNoInteractions(accounts, ledger, transactions);
    }

    @Test
    void reverseRejectsATransactionThatWasAlreadyReversed() {
        Transaction original = original(TransactionType.TRANSFER);
        original.markReversed();
        when(transactions.findByIdForUpdate(TX_ID)).thenReturn(Optional.of(original));

        ApiException error = assertThrows(ApiException.class,
                () -> service.reverse(USER, "key-1", TX_ID, null));

        assertThat(error.getCode()).isEqualTo("ALREADY_REVERSED");
        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(ledger, never()).post(any(), any(), any(), any());
    }

    @Test
    void reverseRejectsATransactionThatIsNotATransfer() {
        when(transactions.findByIdForUpdate(TX_ID)).thenReturn(Optional.of(original(TransactionType.REVERSAL)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.reverse(USER, "key-1", TX_ID, null));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(ledger, never()).post(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- helpers

    private static TransferRequest request(String amount) {
        return new TransferRequest(SRC_ID, DST_NUMBER, new BigDecimal(amount), "test");
    }

    private static Account account(UUID id, UUID owner, String number, String currency, AccountType type) {
        Account account = new Account(owner, number, "name", currency, type);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static Transaction original(TransactionType type) {
        Transaction transaction = new Transaction(type, new BigDecimal("25.0000"), "USD",
                SRC_ID, DST_ID, USER, null, null);
        ReflectionTestUtils.setField(transaction, "id", TX_ID);
        return transaction;
    }
}
