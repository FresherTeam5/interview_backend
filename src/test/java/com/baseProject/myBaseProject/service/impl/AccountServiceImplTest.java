package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AccountProperties;
import com.baseProject.myBaseProject.dto.account.RequestAccountDeletionRequest;
import com.baseProject.myBaseProject.dto.account.UpdateAccountRequest;
import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.entity.AccountPreference;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.AccountPreferenceRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final long USER_ID = 7L;

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AccountPreferenceRepository preferences = mock(AccountPreferenceRepository.class);
    private final AccountDeletionRequestRepository deletions =
            mock(AccountDeletionRequestRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AccountServiceImpl service = new AccountServiceImpl(
            users,
            preferences,
            deletions,
            refreshTokens,
            passwords,
            new AccountProperties(
                    1440, 30, 7, "https://app.example.com", false,
                    "no-reply@example.com"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void updatesProfileAndCreatesDefaultPreferences() {
        UserAccount user = user();
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(preferences.findById(USER_ID)).thenReturn(Optional.empty());
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(USER_ID, new UpdateAccountRequest(
                "  Nguyen Van B  ",
                "https://example.com/avatar.png",
                null,
                "en-US",
                "Asia/Ho_Chi_Minh",
                false,
                true));

        assertThat(response.fullName()).isEqualTo("Nguyen Van B");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.preferences().languageCode()).isEqualTo("en-US");
        assertThat(response.preferences().timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.preferences().emailNotifications()).isFalse();
        verify(preferences).save(any(AccountPreference.class));
    }

    @Test
    void schedulesAndCancelsAccountDeletion() {
        UserAccount user = user();
        user.setPasswordHash("encoded");
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(passwords.matches("current-password", "encoded")).thenReturn(true);
        when(deletions.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(deletions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestDeletion(
                USER_ID,
                new RequestAccountDeletionRequest("DELETE", "current-password"));

        assertThat(response.status()).isEqualTo(AccountDeletionStatus.PENDING);
        assertThat(response.scheduledAt()).isEqualTo(NOW.plusSeconds(7 * 86400L));
        assertThat(user.getDeletionRequestedAt()).isEqualTo(NOW);

        AccountDeletionRequest deletion = AccountDeletionRequest.builder()
                .user(user)
                .status(AccountDeletionStatus.PENDING)
                .requestedAt(NOW)
                .scheduledAt(response.scheduledAt())
                .build();
        when(deletions.findByUserId(USER_ID)).thenReturn(Optional.of(deletion));

        service.cancelDeletion(USER_ID);

        assertThat(deletion.getStatus()).isEqualTo(AccountDeletionStatus.CANCELLED);
        assertThat(user.getDeletionRequestedAt()).isNull();
    }

    @Test
    void rejectsEmptyPatch() {
        assertThatThrownBy(() -> service.update(
                USER_ID,
                new UpdateAccountRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(DomainException.class);
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
