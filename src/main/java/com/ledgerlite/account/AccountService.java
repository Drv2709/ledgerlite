package com.ledgerlite.account;

import com.ledgerlite.account.dto.AccountResponse;
import com.ledgerlite.account.dto.CreateAccountRequest;
import com.ledgerlite.common.exception.ApiException;
import com.ledgerlite.common.exception.ForbiddenException;
import com.ledgerlite.common.exception.NotFoundException;
import com.ledgerlite.common.money.Money;
import com.ledgerlite.ledger.LedgerService;
import com.ledgerlite.transaction.Transaction;
import com.ledgerlite.transaction.TransactionRepository;
import com.ledgerlite.transaction.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerService ledger;

    public AccountService(AccountRepository accounts, TransactionRepository transactions, LedgerService ledger) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
    }

    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        String currency = request.currency().toUpperCase(Locale.ROOT);
        BigDecimal deposit = request.initialDeposit() == null
                ? BigDecimal.ZERO
                : Money.normalize(request.initialDeposit());

        Account systemAccount = null;
        if (deposit.signum() > 0) {
            systemAccount = accounts.findFirstByTypeAndCurrency(AccountType.SYSTEM, currency)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "Deposit unavailable", "Initial deposits are not available for " + currency + "."));
        }

        String accountNumber = "LL-" + String.format("%010d", accounts.nextAccountNumberSeq());
        Account account = accounts.saveAndFlush(
                new Account(userId, accountNumber, request.name().trim(), currency, AccountType.USER));

        if (deposit.signum() > 0) {
            // Same double-entry path every transfer uses: DEBIT the system account, CREDIT the new account.
            Transaction tx = transactions.saveAndFlush(new Transaction(
                    TransactionType.DEPOSIT, deposit, currency,
                    systemAccount.getId(), account.getId(), userId, "Initial deposit", null));
            ledger.post(tx.getId(), systemAccount.getId(), account.getId(), deposit);
        }

        return AccountResponse.from(account, ledger.balanceOf(account.getId()));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> AccountResponse.from(a, ledger.balanceOf(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        // IDOR protection: the owner always comes from the authenticated principal, never from the request.
        if (!account.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this account.");
        }
        return AccountResponse.from(account, ledger.balanceOf(account.getId()));
    }
}
