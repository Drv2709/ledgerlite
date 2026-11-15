package com.ledgerlite.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();
    private static final UUID TX = UUID.randomUUID();

    @Mock
    private LedgerEntryRepository repository;

    private LedgerService service;

    @BeforeEach
    void setUp() {
        service = new LedgerService(repository);
    }

    @Test
    void emptyAccountHasZeroBalanceWithScaleFour() {
        when(repository.balanceOf(ACCOUNT_A)).thenReturn(null);

        assertThat(service.balanceOf(ACCOUNT_A)).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void balanceIsRoundedHalfEven() {
        when(repository.balanceOf(ACCOUNT_A)).thenReturn(new BigDecimal("100.12345"));
        when(repository.balanceOf(ACCOUNT_B)).thenReturn(new BigDecimal("100.12355"));

        assertThat(service.balanceOf(ACCOUNT_A)).isEqualTo(new BigDecimal("100.1234"));
        assertThat(service.balanceOf(ACCOUNT_B)).isEqualTo(new BigDecimal("100.1236"));
    }

    @Test
    void balanceKeepsTheExactDecimalValue() {
        when(repository.balanceOf(ACCOUNT_A)).thenReturn(new BigDecimal("0.3"));

        assertThat(service.balanceOf(ACCOUNT_A)).isEqualTo(new BigDecimal("0.3000"));
    }

    @Test
    void postWritesOneDebitAndOneCreditOfTheSameAmount() {
        service.post(TX, ACCOUNT_A, ACCOUNT_B, new BigDecimal("10.5"));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(repository, times(2)).save(captor.capture());
        verify(repository).flush();

        List<LedgerEntry> entries = captor.getAllValues();
        LedgerEntry debit = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).findFirst().orElseThrow();
        LedgerEntry credit = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).findFirst().orElseThrow();

        assertThat(entries).hasSize(2);
        assertThat(debit.getAccountId()).isEqualTo(ACCOUNT_A);
        assertThat(credit.getAccountId()).isEqualTo(ACCOUNT_B);
        assertThat(debit.getAmount()).isEqualTo(new BigDecimal("10.5000"));
        assertThat(credit.getAmount()).isEqualTo(debit.getAmount());
        assertThat(debit.getTransactionId()).isEqualTo(TX);
        assertThat(credit.getTransactionId()).isEqualTo(TX);
    }

    @Test
    void postRejectsTheSameAccountOnBothSides() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(TX, ACCOUNT_A, ACCOUNT_A, new BigDecimal("10.00")));

        verify(repository, never()).save(any());
    }

    @Test
    void postRejectsAZeroAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(TX, ACCOUNT_A, ACCOUNT_B, BigDecimal.ZERO));

        verify(repository, never()).save(any());
    }

    @Test
    void postRejectsANegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(TX, ACCOUNT_A, ACCOUNT_B, new BigDecimal("-5.00")));

        verify(repository, never()).save(any());
    }

    @Test
    void postRejectsAnAmountThatRoundsToZero() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(TX, ACCOUNT_A, ACCOUNT_B, new BigDecimal("0.00004")));

        verify(repository, never()).save(any());
    }
}
