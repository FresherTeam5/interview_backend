package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AccountProperties;
import com.baseProject.myBaseProject.entity.AccountToken;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountTokenType;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AccountTokenRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AccountMailService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import com.baseProject.myBaseProject.util.Sha256;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountCredentialServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final long USER_ID = 7L;

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AccountTokenRepository tokens = mock(AccountTokenRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AccountMailService mail = mock(AccountMailService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AccountCredentialServiceImpl service = new AccountCredentialServiceImpl(
            users,
            tokens,
            refreshTokens,
            mail,
            passwords,
            new AccountProperties(
                    1440, 30, 7, "https://app.example.com", false,
                    "no-reply@example.com"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuesHashedEmailVerificationToken() {
        UserAccount user = user();
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);

        service.requestEmailVerification(USER_ID);

        verify(tokens).invalidateUnused(
                USER_ID, AccountTokenType.EMAIL_VERIFICATION, NOW);
        verify(tokens).save(saved.capture());
        verify(mail).sendEmailVerification(
                org.mockito.ArgumentMatchers.eq(user.getEmail()),
                org.mockito.ArgumentMatchers.eq(user.getFullName()),
                delivered.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(Sha256.hex(delivered.getValue().getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(delivered.getValue());
        assertThat(saved.getValue().getExpiresAt())
                .isEqualTo(NOW.plusSeconds(1440 * 60L));
    }

    @Test
    void verifiesEmailWithMatchingUsableToken() {
        UserAccount user = user();
        AccountToken token = AccountToken.builder()
                .user(user)
                .tokenHash(Sha256.hex("raw-token".getBytes(StandardCharsets.UTF_8)))
                .tokenType(AccountTokenType.EMAIL_VERIFICATION)
                .createdAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        when(tokens.findByTokenHashForUpdate(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        service.verifyEmail("raw-token");

        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(" Missing@Example.com ");

        verifyNoInteractions(tokens, mail, refreshTokens, passwords);
    }

    @Test
    void rejectsExpiredToken() {
        AccountToken token = AccountToken.builder()
                .user(user())
                .tokenHash(Sha256.hex("expired".getBytes(StandardCharsets.UTF_8)))
                .tokenType(AccountTokenType.PASSWORD_RESET)
                .expiresAt(NOW)
                .build();
        when(tokens.findByTokenHashForUpdate(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("expired", "new-password"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.ACCOUNT_TOKEN_INVALID));
        verify(passwords, never()).encode(any());
    }

    private UserAccount user() {
        return UserAccount.builder()
                .id(USER_ID)
                .fullName("Nguyen Van A")
                .email("user@example.com")
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(NOW.minusSeconds(3600))
                .updatedAt(NOW.minusSeconds(3600))
                .build();
    }
}
