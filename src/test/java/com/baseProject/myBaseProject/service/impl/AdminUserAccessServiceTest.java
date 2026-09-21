package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.UpdateUserAccessRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.repository.RefreshTokenRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserAccessServiceTest {
    @Test
    void temporarySuspensionRevokesSessionsAndExpiresWithoutChangingEnabledFlag() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        UserAccountRepository users = mock(UserAccountRepository.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        RefreshTokenRepository tokenRepository = mock(RefreshTokenRepository.class);
        AccountDeletionRequestRepository deletions = mock(AccountDeletionRequestRepository.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        UserAccount user = UserAccount.builder()
                .id(7L).fullName("Minh").email("minh@example.com")
                .role(UserRole.USER).enabled(true)
                .createdAt(now.minusSeconds(3600)).updatedAt(now.minusSeconds(60)).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(deletions.findByUserId(7L)).thenReturn(Optional.empty());

        AdminUserServiceImpl service = new AdminUserServiceImpl(
                users, mock(CvDocumentRepository.class),
                mock(JobDescriptionDocumentRepository.class),
                mock(InterviewSessionRepository.class), refreshTokens, tokenRepository,
                deletions, mock(AccountCredentialService.class), audit,
                Clock.fixed(now, ZoneOffset.UTC));

        var response = service.updateAccess(3L, 7L, new UpdateUserAccessRequest(
                AccountAccessStatus.SUSPENDED, "Abusive traffic", now.plusSeconds(3600)));

        assertThat(response.accessStatus()).isEqualTo(AccountAccessStatus.SUSPENDED);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.canAuthenticateAt(now)).isFalse();
        assertThat(user.canAuthenticateAt(now.plusSeconds(3601))).isTrue();
        verify(refreshTokens).revokeAllForUser(7L);
    }
}
