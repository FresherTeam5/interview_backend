package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.UserRole;

import java.time.Instant;

public record AdminUserSecurityResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        AccountAccessStatus accessStatus,
        boolean enabled,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant suspendedAt,
        Instant suspendedUntil,
        String restrictionReason,
        long activeLoginSessions,
        AccountDeletionStatus deletionStatus,
        Instant deletionRequestedAt,
        Instant deletionScheduledAt,
        Instant createdAt,
        Instant updatedAt) {
}
