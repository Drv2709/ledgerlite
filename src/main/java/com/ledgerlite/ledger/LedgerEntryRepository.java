package com.ledgerlite.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * balance = SUM(credits) - SUM(debits), computed in ONE statement so a single read
     * can never observe half of a concurrent transfer.
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
              FROM ledger_entries
             WHERE account_id = :accountId
            """, nativeQuery = true)
    BigDecimal balanceOf(@Param("accountId") UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}
