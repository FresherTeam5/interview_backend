package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;

import java.time.Instant;

public record AdminUserOperationsSummaryResponse(
        Long id,
        String fullName,
        String email,
        AccountAccessStatus accessStatus,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant suspendedUntil,
        String restrictionReason,
        AccountDeletionStatus deletionStatus,
        Instant deletionScheduledAt,
        Instant createdAt,
        Instant updatedAt) {
}
