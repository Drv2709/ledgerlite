package com.ledgerlite.admin;

import com.ledgerlite.admin.dto.AdminTransactionResponse;
import com.ledgerlite.admin.dto.AdminUserResponse;
import com.ledgerlite.common.dto.PageResponse;
import com.ledgerlite.transaction.TransactionType;
import com.ledgerlite.user.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Read-only. Only GET endpoints exist here, on purpose. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return adminService.listUsers(admin.getId(), page, size);
    }

    @GetMapping("/transactions")
    public PageResponse<AdminTransactionResponse> transactions(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return adminService.listTransactions(admin.getId(), page, size, accountId, type, from, to);
    }
}
