package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.AccountProperties;
import com.baseProject.myBaseProject.dto.account.AccountDeletionResponse;
import com.baseProject.myBaseProject.dto.account.AccountProfileResponse;
import com.baseProject.myBaseProject.dto.account.LoginSessionResponse;
import com.baseProject.myBaseProject.dto.account.RequestAccountDeletionRequest;
import com.baseProject.myBaseProject.dto.account.UpdateAccountRequest;
import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.entity.AccountPreference;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.AccountPreferenceRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AccountService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private static final String DEFAULT_LANGUAGE = "vi";
    private static final String DEFAULT_TIME_ZONE = "Asia/Bangkok";

    private final UserAccountRepository users;
    private final AccountPreferenceRepository preferences;
    private final AccountDeletionRequestRepository deletionRequests;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccountProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AccountProfileResponse get(Long userId) {
        UserAccount user = requireUser(userId);
        return toResponse(user, preferences.findById(userId).orElse(null));
    }

    @Override
    @Transactional
    public AccountProfileResponse update(Long userId, UpdateAccountRequest request) {
        validateUpdate(request);
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        Instant now = clock.instant();

        if (request.fullName() != null) {
            String fullName = request.fullName().strip();
            if (fullName.isEmpty()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED,
                        "fullName must not be blank");
            }
            user.setFullName(fullName);
        }
        if (Boolean.TRUE.equals(request.clearAvatar())) {
            user.setAvatarUrl(null);
        } else if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().strip());
        }

        AccountPreference preference = preference(user, now);
        if (request.languageCode() != null) {
            preference.setLanguageCode(request.languageCode().strip().replace('_', '-'));
        }
        if (request.timeZone() != null) {
            String timeZone = request.timeZone().strip();
            validateTimeZone(timeZone);
            preference.setTimeZone(timeZone);
        }
        if (request.emailNotifications() != null) {
            preference.setEmailNotifications(request.emailNotifications());
        }
        if (request.processingNotifications() != null) {
            preference.setProcessingNotifications(request.processingNotifications());
        }
        preference.setUpdatedAt(now);
        user.setUpdatedAt(now);
        preferences.save(preference);
        return toResponse(user, preference);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginSessionResponse> loginSessions(
            Long userId, String currentRefreshToken) {
        requireUser(userId);
        return refreshTokens.listSessions(userId, currentRefreshToken);
    }

    @Override
    public void revokeLoginSession(Long userId, String sessionId) {
        refreshTokens.revokeSession(userId, sessionId);
    }

    @Override
    @Transactional
    public AccountDeletionResponse requestDeletion(
            Long userId, RequestAccountDeletionRequest request) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "confirmation must equal DELETE");
        }
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETION_ADMIN_FORBIDDEN);
        }
        verifyPasswordWhenConfigured(user, request.currentPassword());

        Instant now = clock.instant();
        Instant scheduledAt = now.plus(properties.deletionGraceDays(), ChronoUnit.DAYS);
        AccountDeletionRequest deletion = deletionRequests.findByUserId(userId)
                .orElseGet(() -> AccountDeletionRequest.builder()
                        .user(user)
                        .build());
        deletion.reschedule(now, scheduledAt);
        user.setDeletionRequestedAt(now);
        user.setUpdatedAt(now);
        deletionRequests.save(deletion);
        return toResponse(deletion);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDeletionResponse> deletionStatus(Long userId) {
        requireUser(userId);
        return deletionRequests.findByUserId(userId).map(this::toResponse);
    }

    @Override
    @Transactional
    public void cancelDeletion(Long userId) {
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        AccountDeletionRequest deletion = deletionRequests.findByUserId(userId)
                .filter(value -> value.getStatus() == AccountDeletionStatus.PENDING)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.ACCOUNT_DELETION_NOT_PENDING));
        Instant now = clock.instant();
        deletion.cancel(now);
        user.setDeletionRequestedAt(null);
        user.setUpdatedAt(now);
    }

    private AccountPreference preference(UserAccount user, Instant now) {
        return preferences.findById(user.getId())
                .orElseGet(() -> AccountPreference.builder()
                        .user(user)
                        .languageCode(DEFAULT_LANGUAGE)
                        .timeZone(DEFAULT_TIME_ZONE)
                        .emailNotifications(true)
                        .processingNotifications(true)
                        .updatedAt(now)
                        .build());
    }

    private AccountProfileResponse toResponse(
            UserAccount user, AccountPreference preference) {
        AccountProfileResponse.Preferences responsePreferences = preference == null
                ? new AccountProfileResponse.Preferences(
                        DEFAULT_LANGUAGE, DEFAULT_TIME_ZONE, true, true)
                : new AccountProfileResponse.Preferences(
                        preference.getLanguageCode(),
                        preference.getTimeZone(),
                        preference.isEmailNotifications(),
                        preference.isProcessingNotifications());
        return new AccountProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getEmailVerifiedAt(),
                responsePreferences,
                user.getDeletionRequestedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private AccountDeletionResponse toResponse(AccountDeletionRequest request) {
        return new AccountDeletionResponse(
                request.getStatus(),
                request.getRequestedAt(),
                request.getScheduledAt(),
                request.getCancelledAt());
    }

    private void validateUpdate(UpdateAccountRequest request) {
        if (request.fullName() == null
                && request.avatarUrl() == null
                && request.clearAvatar() == null
                && request.languageCode() == null
                && request.timeZone() == null
                && request.emailNotifications() == null
                && request.processingNotifications() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "At least one account field is required");
        }
        if (Boolean.TRUE.equals(request.clearAvatar()) && request.avatarUrl() != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "avatarUrl and clearAvatar cannot be used together");
        }
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (Exception exception) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "timeZone must be a valid IANA time zone");
        }
    }

    private void verifyPasswordWhenConfigured(UserAccount user, String currentPassword) {
        if (user.getPasswordHash() != null
                && (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new DomainException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
    }

    private UserAccount requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }
}
