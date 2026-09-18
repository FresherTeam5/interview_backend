package com.baseProject.myBaseProject.dto.account;

import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import java.time.Instant;

public record AccountDeletionResponse(
        AccountDeletionStatus status,
        Instant requestedAt,
        Instant scheduledAt,
        Instant cancelledAt) {
}
