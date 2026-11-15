package com.ledgerlite.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findFirstByTypeAndCurrency(AccountType type, String currency);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSeq();

    /**
     * SELECT ... FOR UPDATE on every listed account, always in ascending id order, so two transfers
     * touching the same accounts in opposite directions cannot deadlock. Must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockByIdsOrdered(@Param("ids") Collection<UUID> ids);
}
