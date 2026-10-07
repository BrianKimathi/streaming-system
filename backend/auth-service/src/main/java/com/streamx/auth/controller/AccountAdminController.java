package com.streamx.auth.controller;

import com.streamx.auth.dto.AccountSummaryResponse;
import com.streamx.auth.dto.AuthResponse;
import com.streamx.auth.dto.LoginRequest;
import com.streamx.auth.dto.UpdateAccountStatusRequest;
import com.streamx.auth.service.AccountAdminService;
import com.streamx.auth.service.AuthService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/admin")
public class AccountAdminController {

    private final AuthService authService;
    private final AccountAdminService accountAdminService;

    public AccountAdminController(AuthService authService, AccountAdminService accountAdminService) {
        this.authService = authService;
        this.accountAdminService = accountAdminService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator login successful", authService.adminLogin(request)));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> listAccounts() {
        return ResponseEntity.ok(ApiResponse.success(accountAdminService.listAccounts()));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getAccount(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(accountAdminService.getAccount(id)));
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> updateStatus(
            @PathVariable("id") String id,
            @RequestHeader(value = SecurityConstants.HEADER_X_ACCOUNT_ID, required = false) String actingAccountId,
            @RequestBody UpdateAccountStatusRequest request) {
        AccountSummaryResponse response = accountAdminService.updateBlockedStatus(id, request.isBlocked(), actingAccountId);
        return ResponseEntity.ok(ApiResponse.success(request.isBlocked() ? "Account suspended" : "Account reactivated", response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(accountAdminService.getStats()));
    }
}
