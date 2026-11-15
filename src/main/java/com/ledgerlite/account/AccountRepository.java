package com.ledgerlite.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findFirstByTypeAndCurrency(AccountType type, String currency);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSeq();
}
