package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.account.AccountDeletionResponse;
import com.baseProject.myBaseProject.dto.account.AccountExportResponse;
import com.baseProject.myBaseProject.dto.account.AccountProfileResponse;
import com.baseProject.myBaseProject.dto.account.ChangePasswordRequest;
import com.baseProject.myBaseProject.dto.account.LoginSessionResponse;
import com.baseProject.myBaseProject.dto.account.RequestAccountDeletionRequest;
import com.baseProject.myBaseProject.dto.account.UpdateAccountRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.RefreshTokenCookieFactory;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.AccountExportService;
import com.baseProject.myBaseProject.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Tài khoản người dùng")
public class AccountController {
    private final AccountService accountService;
    private final AccountCredentialService credentialService;
    private final AccountExportService exportService;
    private final RefreshTokenCookieFactory cookieFactory;

    @GetMapping
    @Operation(summary = "Lấy hồ sơ và cài đặt tài khoản")
    public AccountProfileResponse get(@CurrentUser CustomUserDetails user) {
        return accountService.get(user.getId());
    }

    @PatchMapping
    @Operation(summary = "Cập nhật hồ sơ và cài đặt tài khoản")
    public AccountProfileResponse update(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.update(user.getId(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Đổi mật khẩu")
    public ResponseEntity<Void> changePassword(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        credentialService.changePassword(
                user.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/sessions")
    @Operation(summary = "Liệt kê các phiên đăng nhập")
    public List<LoginSessionResponse> loginSessions(
            @CurrentUser CustomUserDetails user,
            HttpServletRequest request) {
        String currentToken = cookieFactory.read(request).orElse(null);
        return accountService.loginSessions(user.getId(), currentToken);
    }

    @GetMapping("/export")
    @Operation(summary = "Xuất dữ liệu tài khoản dưới dạng JSON")
    public ResponseEntity<AccountExportResponse> export(
            @CurrentUser CustomUserDetails user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=account-export.json")
                .body(exportService.export(user.getId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Thu hồi một phiên đăng nhập")
    public ResponseEntity<Void> revokeLoginSession(
            @CurrentUser CustomUserDetails user,
            @PathVariable String sessionId) {
        accountService.revokeLoginSession(user.getId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deletion-request")
    @Operation(summary = "Lấy trạng thái yêu cầu xóa tài khoản")
    public ResponseEntity<AccountDeletionResponse> deletionStatus(
            @CurrentUser CustomUserDetails user) {
        return ResponseEntity.of(accountService.deletionStatus(user.getId()));
    }

    @PostMapping("/deletion-request")
    @Operation(summary = "Lên lịch xóa tài khoản")
    public ResponseEntity<AccountDeletionResponse> requestDeletion(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody RequestAccountDeletionRequest request) {
        return ResponseEntity.accepted()
                .body(accountService.requestDeletion(user.getId(), request));
    }

    @DeleteMapping("/deletion-request")
    @Operation(summary = "Hủy yêu cầu xóa tài khoản")
    public ResponseEntity<Void> cancelDeletion(
            @CurrentUser CustomUserDetails user) {
        accountService.cancelDeletion(user.getId());
        return ResponseEntity.noContent().build();
    }
}
