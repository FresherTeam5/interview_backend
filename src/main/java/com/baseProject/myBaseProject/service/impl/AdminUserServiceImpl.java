package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateUserAccessRequest;
import com.baseProject.myBaseProject.dto.admin.AdminUserOperationsSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSecurityResponse;
import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.RefreshTokenRepository;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.AdminUserService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {
    private final UserAccountRepository users;
    private final CvDocumentRepository cvDocuments;
    private final JobDescriptionDocumentRepository jobDescriptions;
    private final InterviewSessionRepository sessions;
    private final RefreshTokenService refreshTokens;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountDeletionRequestRepository deletionRequests;
    private final AccountCredentialService credentials;
    private final AdminAuditService audit;
    private final Clock clock;

    @Autowired
    public AdminUserServiceImpl(
            UserAccountRepository users,
            CvDocumentRepository cvDocuments,
            JobDescriptionDocumentRepository jobDescriptions,
            InterviewSessionRepository sessions,
            RefreshTokenService refreshTokens,
            RefreshTokenRepository refreshTokenRepository,
            AccountDeletionRequestRepository deletionRequests,
            AccountCredentialService credentials,
            AdminAuditService audit,
            Clock clock) {
        this.users = users;
        this.cvDocuments = cvDocuments;
        this.jobDescriptions = jobDescriptions;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deletionRequests = deletionRequests;
        this.credentials = credentials;
        this.audit = audit;
        this.clock = clock;
    }

    // Giữ constructor cũ cho unit test hiện có.
    public AdminUserServiceImpl(
            UserAccountRepository users,
            CvDocumentRepository cvDocuments,
            JobDescriptionDocumentRepository jobDescriptions,
            InterviewSessionRepository sessions,
            RefreshTokenService refreshTokens,
            Clock clock) {
        this(users, cvDocuments, jobDescriptions, sessions, refreshTokens,
                null, null, null, null, clock);
    }

    @Override
    public AdminPageResponse<AdminUserSummaryResponse> list(
            String keyword, UserRole role, Boolean enabled, int page, int size) {
        Page<UserAccount> result = users.searchForAdmin(
                normalizeKeyword(keyword), role, enabled, pageRequest(page, size));
        return new AdminPageResponse<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        return toDetail(user);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateStatus(
            Long adminId, Long userId, UpdateUserStatusRequest request) {
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ADMIN_USER_STATUS_PROTECTED);
        }

        Instant now = clock.instant();
        Map<String, Object> before = accessSnapshot(user, now);
        boolean enabled = request.enabled();
        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            user.setUpdatedAt(nextUpdatedAt(user.getUpdatedAt()));
        }
        if (!enabled) {
            refreshTokens.revokeAllForUser(userId);
        } else {
            user.setSuspendedAt(null);
            user.setSuspendedUntil(null);
            user.setRestrictionReason(null);
        }
        if (audit != null) {
            audit.record(adminId, AdminAuditAction.USER_ACCESS_CHANGED, "USER", userId,
                    before, accessSnapshot(user, now));
        }

        log.info("Admin {} set user {} enabled={}", adminId, userId, enabled);
        return toDetail(user);
    }

    @Override
    public AdminPageResponse<AdminUserOperationsSummaryResponse> listAdvanced(
            String keyword, Boolean emailVerified, AccountAccessStatus accessStatus,
            AccountDeletionStatus deletionStatus, Instant lastLoginFrom, Instant lastLoginTo,
            int page, int size) {
        if (lastLoginFrom != null && lastLoginTo != null
                && lastLoginFrom.isAfter(lastLoginTo)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "lastLoginFrom must be before or equal to lastLoginTo");
        }
        Page<UserAccount> result = users.searchAdvancedForAdmin(normalizeKeyword(keyword),
                emailVerified, accessStatus == null ? null : accessStatus.name(),
                deletionStatus, lastLoginFrom, lastLoginTo,
                clock.instant(), pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::toOperations).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public AdminUserSecurityResponse getSecurity(Long userId) {
        UserAccount user = requireUser(userId);
        return toSecurity(user);
    }

    @Override
    @Transactional
    public AdminUserSecurityResponse updateAccess(
            Long adminId, Long userId, UpdateUserAccessRequest request) {
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        protectAdmin(user);
        Instant now = clock.instant();
        Map<String, Object> before = accessSnapshot(user, now);
        String reason = normalizeReason(request.reason());
        switch (request.status()) {
            case ACTIVE -> {
                user.setEnabled(true);
                user.setSuspendedAt(null);
                user.setSuspendedUntil(null);
                user.setRestrictionReason(null);
            }
            case SUSPENDED -> {
                if (reason == null || request.suspendedUntil() == null
                        || !request.suspendedUntil().isAfter(now)) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "A future suspendedUntil and reason are required for SUSPENDED");
                }
                user.setEnabled(true);
                user.setSuspendedAt(now);
                user.setSuspendedUntil(request.suspendedUntil());
                user.setRestrictionReason(reason);
                refreshTokens.revokeAllForUser(userId);
            }
            case DISABLED -> {
                if (reason == null) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "reason is required for DISABLED");
                }
                user.setEnabled(false);
                user.setSuspendedAt(now);
                user.setSuspendedUntil(null);
                user.setRestrictionReason(reason);
                refreshTokens.revokeAllForUser(userId);
            }
        }
        user.setUpdatedAt(nextUpdatedAt(user.getUpdatedAt()));
        audit.record(adminId, AdminAuditAction.USER_ACCESS_CHANGED, "USER", userId,
                before, accessSnapshot(user, now));
        log.info("Admin {} changed user {} access to {}", adminId, userId, request.status());
        return toSecurity(user);
    }

    @Override
    @Transactional
    public int revokeSessions(Long adminId, Long userId) {
        protectAdmin(requireUser(userId));
        int revoked = refreshTokens.revokeAllForUser(userId);
        audit.record(adminId, AdminAuditAction.USER_SESSIONS_REVOKED, "USER", userId,
                null, Map.of("revokedSessions", revoked));
        return revoked;
    }

    @Override
    @Transactional
    public void sendVerification(Long adminId, Long userId) {
        UserAccount user = requireOperationalUser(userId);
        credentials.requestEmailVerification(userId);
        audit.record(adminId, AdminAuditAction.USER_VERIFICATION_SENT, "USER", userId,
                null, Map.of("email", user.getEmail()));
    }

    @Override
    @Transactional
    public void sendPasswordReset(Long adminId, Long userId) {
        UserAccount user = requireOperationalUser(userId);
        credentials.requestPasswordReset(user.getEmail());
        audit.record(adminId, AdminAuditAction.USER_PASSWORD_RESET_SENT, "USER", userId,
                null, Map.of("email", user.getEmail()));
    }

    private AdminUserSummaryResponse toSummary(UserAccount user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private AdminUserDetailResponse toDetail(UserAccount user) {
        Long userId = user.getId();
        return new AdminUserDetailResponse(
                userId,
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                new AdminUserDetailResponse.Activity(
                        cvDocuments.countByUserIdAndActiveTrue(userId),
                        jobDescriptions.countByOwnerIdAndActiveTrue(userId),
                        sessions.countByUserId(userId),
                        sessions.countByUserIdAndStatus(
                                userId, InterviewSessionStatus.COMPLETED)));
    }

    private AdminUserOperationsSummaryResponse toOperations(UserAccount user) {
        AccountDeletionRequest deletion = deletionRequests == null
                ? null : deletionRequests.findByUserId(user.getId()).orElse(null);
        return new AdminUserOperationsSummaryResponse(user.getId(), user.getFullName(),
                user.getEmail(), accessStatus(user, clock.instant()), user.getEmailVerifiedAt(),
                user.getLastLoginAt(), user.getSuspendedUntil(), user.getRestrictionReason(),
                deletion == null ? null : deletion.getStatus(),
                deletion == null ? null : deletion.getScheduledAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private AdminUserSecurityResponse toSecurity(UserAccount user) {
        Instant now = clock.instant();
        AccountDeletionRequest deletion = deletionRequests == null
                ? null : deletionRequests.findByUserId(user.getId()).orElse(null);
        long activeSessions = refreshTokenRepository == null ? 0
                : refreshTokenRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user.getId(), now);
        return new AdminUserSecurityResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), accessStatus(user, now), user.isEnabled(),
                user.getEmailVerifiedAt(), user.getLastLoginAt(), user.getSuspendedAt(),
                user.getSuspendedUntil(), user.getRestrictionReason(), activeSessions,
                deletion == null ? null : deletion.getStatus(), user.getDeletionRequestedAt(),
                deletion == null ? null : deletion.getScheduledAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private AccountAccessStatus accessStatus(UserAccount user, Instant now) {
        if (!user.isEnabled()) {
            return AccountAccessStatus.DISABLED;
        }
        return user.isSuspendedAt(now)
                ? AccountAccessStatus.SUSPENDED : AccountAccessStatus.ACTIVE;
    }

    private Map<String, Object> accessSnapshot(UserAccount user, Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", accessStatus(user, now));
        values.put("suspendedUntil", user.getSuspendedUntil());
        values.put("reason", user.getRestrictionReason());
        return values;
    }

    private UserAccount requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }

    private UserAccount requireOperationalUser(Long userId) {
        UserAccount user = requireUser(userId);
        protectAdmin(user);
        if (!user.canAuthenticateAt(clock.instant())) {
            throw new DomainException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private void protectAdmin(UserAccount user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ADMIN_USER_STATUS_PROTECTED);
        }
    }

    private String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private Instant nextUpdatedAt(Instant current) {
        Instant now = clock.instant();
        return now.isAfter(current) ? now : current.plusNanos(1000);
    }
}
