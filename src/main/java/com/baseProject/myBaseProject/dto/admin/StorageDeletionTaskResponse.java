package com.baseProject.myBaseProject.dto.admin;

import java.time.Instant;

public record StorageDeletionTaskResponse(
        Long id,
        String storageKey,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant createdAt) {
}
