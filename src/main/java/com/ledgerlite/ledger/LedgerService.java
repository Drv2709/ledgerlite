package com.ledgerlite.ledger;

import com.ledgerlite.common.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntries;

    public LedgerService(LedgerEntryRepository ledgerEntries) {
        this.ledgerEntries = ledgerEntries;
    }

    /** Current balance, derived from the ledger. Never stored anywhere. */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(UUID accountId) {
        BigDecimal balance = ledgerEntries.balanceOf(accountId);
        return Money.normalize(balance == null ? BigDecimal.ZERO : balance);
    }

    /** The entries of one transaction, DEBIT first, then CREDIT. */
    @Transactional(readOnly = true)
    public List<LedgerEntry> entriesOf(UUID transactionId) {
        return ledgerEntries.findByTransactionId(transactionId).stream()
                .sorted(Comparator.comparing(LedgerEntry::getEntryType))
                .toList();
    }

    /**
     * Posts one balanced pair of entries (DEBIT one account, CREDIT the other) for an existing
     * transaction. MANDATORY: it must run inside the caller's database transaction, so the
     * transaction row and both entries commit together or not at all. The Postgres trigger
     * re-checks debits = credits at commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(UUID transactionId, UUID debitAccountId, UUID creditAccountId, BigDecimal amount) {
        if (debitAccountId.equals(creditAccountId)) {
            throw new IllegalArgumentException("Debit and credit accounts must differ");
        }
        BigDecimal normalized = Money.normalize(amount);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        ledgerEntries.save(LedgerEntry.debit(transactionId, debitAccountId, normalized));
        ledgerEntries.save(LedgerEntry.credit(transactionId, creditAccountId, normalized));
        ledgerEntries.flush();
    }
}
