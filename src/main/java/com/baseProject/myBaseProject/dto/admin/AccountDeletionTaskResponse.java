package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AccountDeletionStatus;

import java.time.Instant;

public record AccountDeletionTaskResponse(
        Long id,
        AdminUserReferenceResponse user,
        AccountDeletionStatus status,
        Instant requestedAt,
        Instant scheduledAt,
        Instant cancelledAt,
        int attempts,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        String lastError) {
}
