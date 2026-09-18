package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AccountProperties;
import com.baseProject.myBaseProject.entity.AccountToken;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountTokenType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AccountTokenRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.AccountMailService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import com.baseProject.myBaseProject.util.Sha256;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountCredentialServiceImpl implements AccountCredentialService {
    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository users;
    private final AccountTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final AccountMailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AccountProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestEmailVerification(Long userId) {
        UserAccount user = requireUser(userId);
        if (user.isEmailVerified()) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        String rawToken = issue(user, AccountTokenType.EMAIL_VERIFICATION,
                properties.emailVerificationTtlMinutes());
        mailService.sendEmailVerification(user.getEmail(), user.getFullName(), rawToken);
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        AccountToken token = requireUsableToken(rawToken, AccountTokenType.EMAIL_VERIFICATION);
        Instant now = clock.instant();
        UserAccount user = token.getUser();
        user.setEmailVerifiedAt(now);
        user.setUpdatedAt(now);
        token.setUsedAt(now);
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        users.findByEmail(normalized)
                .filter(UserAccount::isEnabled)
                .ifPresent(user -> {
                    String rawToken = issue(user, AccountTokenType.PASSWORD_RESET,
                            properties.passwordResetTtlMinutes());
                    mailService.sendPasswordReset(user.getEmail(), user.getFullName(), rawToken);
                });
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = requireUsableToken(rawToken, AccountTokenType.PASSWORD_RESET);
        Instant now = clock.instant();
        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        token.setUsedAt(now);
        refreshTokens.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccount user = requireUser(userId);
        if (user.getPasswordHash() == null) {
            throw new DomainException(ErrorCode.PASSWORD_NOT_CONFIGURED);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new DomainException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "newPassword must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(clock.instant());
        refreshTokens.revokeAllForUser(userId);
    }

    @Override
    @Transactional
    public int purgeExpiredTokens() {
        return tokens.deleteAllExpiredBefore(clock.instant());
    }

    private String issue(UserAccount user, AccountTokenType type, long ttlMinutes) {
        Instant now = clock.instant();
        tokens.invalidateUnused(user.getId(), type, now);
        String raw = generateRawToken();
        tokens.save(AccountToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .tokenType(type)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .build());
        return raw;
    }

    private AccountToken requireUsableToken(String rawToken, AccountTokenType type) {
        AccountToken token = tokens.findByTokenHashForUpdate(hash(rawToken.strip()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_TOKEN_INVALID));
        if (token.getTokenType() != type || !token.isUsableAt(clock.instant())) {
            throw new DomainException(ErrorCode.ACCOUNT_TOKEN_INVALID);
        }
        return token;
    }

    private UserAccount requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        return Sha256.hex(rawToken.getBytes(StandardCharsets.UTF_8));
    }
}
