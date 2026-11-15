package com.ledgerlite.account;

import com.ledgerlite.account.dto.AccountResponse;
import com.ledgerlite.account.dto.CreateAccountRequest;
import com.ledgerlite.user.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.list(user.getId());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(user.getId(), request));
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID accountId) {
        return accountService.get(user.getId(), accountId);
    }
}
