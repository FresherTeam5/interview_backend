package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.auth.AuthResponse;
import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.CurrentUserResponse;
import com.baseProject.myBaseProject.dto.auth.GoogleLoginRequest;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;
import com.baseProject.myBaseProject.dto.auth.ConfirmAccountTokenRequest;
import com.baseProject.myBaseProject.dto.auth.ForgotPasswordRequest;
import com.baseProject.myBaseProject.dto.auth.ResetPasswordRequest;
import com.baseProject.myBaseProject.dto.account.ClientMetadata;
import com.baseProject.myBaseProject.exception.ApiError;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.security.RefreshTokenCookieFactory;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.AuthService;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Xác thực")
public class AuthController {
    private final AuthService authService;
    private final AccountCredentialService accountCredentialService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final Clock clock;

    @PostMapping("/register")
    @Operation(
            summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới, trả về access token và lưu refresh token trong cookie.")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        return withRefreshCookie(HttpStatus.CREATED,
                authService.register(request, clientMetadata(servletRequest)));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực bằng email và mật khẩu, trả về access token và lưu refresh token trong cookie.")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return withRefreshCookie(HttpStatus.OK,
                authService.login(request, clientMetadata(servletRequest)));
    }

    @PostMapping("/google")
    @Operation(
            summary = "Đăng nhập bằng Google",
            description = "Xác thực Google ID token, tạo tài khoản nếu chưa tồn tại và trả về thông tin đăng nhập.")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest) {
        return withRefreshCookie(HttpStatus.OK,
                authService.loginWithGoogle(request, clientMetadata(servletRequest)));
    }

    @GetMapping("/me")
    @IsAuthenticated
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(
            summary = "Lấy người dùng hiện tại",
            description = "Trả về thông tin tài khoản đang đăng nhập từ access token.")
    public CurrentUserResponse currentUser(@CurrentUser CustomUserDetails currentUser) {
        return authService.currentUser(currentUser.getId());
    }

    @PostMapping("/email-verification/request")
    @IsAuthenticated
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Gửi email xác minh")
    public ResponseEntity<Void> requestEmailVerification(
            @CurrentUser CustomUserDetails currentUser) {
        accountCredentialService.requestEmailVerification(currentUser.getId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "Xác minh email bằng token một lần")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody ConfirmAccountTokenRequest request) {
        accountCredentialService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Yêu cầu đặt lại mật khẩu")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        accountCredentialService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Đặt lại mật khẩu bằng token một lần")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        accountCredentialService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Làm mới access token",
            description = "Dùng refresh token trong cookie để cấp access token mới và xoay vòng refresh token.")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookieFactory.read(request)
                .orElseThrow(() -> new DomainException(ErrorCode.MISSING_REFRESH_TOKEN));

        return withRefreshCookie(HttpStatus.OK,
                authService.refresh(refreshToken, clientMetadata(request)));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Đăng xuất thiết bị hiện tại",
            description = "Thu hồi refresh token của phiên hiện tại và xóa cookie đăng nhập.")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookieFactory.read(request).ifPresent(authService::logout);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/logout-all")
    @IsAuthenticated
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(
            summary = "Đăng xuất mọi thiết bị",
            description = "Thu hồi toàn bộ refresh token của người dùng và xóa cookie đăng nhập hiện tại.")
    public ResponseEntity<Void> logoutAll(@CurrentUser CustomUserDetails currentUser) {
        authService.logoutAll(currentUser.getId());

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleRefreshTokenRejected(DomainException ex, HttpServletRequest request) {
        if (ex.getCode() == ErrorCode.MISSING_REFRESH_TOKEN || ex.getCode() == ErrorCode.INVALID_REFRESH_TOKEN) {
            ApiError body = ApiError.of(
                    clock.instant(),
                    ex.getStatus().value(),
                    ex.getCode(),
                    ex.getMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                    .body(body);
        }
        throw ex;
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.build(result.refreshToken()).toString())
                .body(result.body());
    }

    private ClientMetadata clientMetadata(HttpServletRequest request) {
        return new ClientMetadata(
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRemoteAddr());
    }
}
